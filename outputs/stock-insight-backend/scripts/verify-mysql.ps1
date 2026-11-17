param([ValidateRange(1024, 65535)][int]$MySqlPort = 13407)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $backendRoot)
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$scratchRoot = Join-Path (Join-Path $workspaceRoot 'work\verification-tests') $runId
$mysqlBase = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0'
$mysqld = Join-Path $mysqlBase 'bin\mysqld.exe'
$mysql = Join-Path $mysqlBase 'bin\mysql.exe'
$javaRoot = Join-Path $env:ProgramFiles 'Amazon Corretto\jdk21.0.12_8'
if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin\java.exe'))) { $javaRoot = $env:JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($javaRoot)) { throw 'Set JAVA_HOME to a Java 21 installation.' }
foreach ($required in @($mysqld, $mysql, (Join-Path $javaRoot 'bin\java.exe'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw ('Required executable missing: ' + $required) }
}
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $MySqlPort)
try { $listener.Start() } catch { throw ('Test port is already in use: ' + $MySqlPort) } finally { $listener.Stop() }
$previousEnv = @{}
function Set-TestEnvironment([string]$name, [string]$value) {
    if (-not $previousEnv.ContainsKey($name)) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
$dbProcess = $null
New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null
$mysqlData = Join-Path $scratchRoot 'mysql-data'
New-Item -ItemType Directory -Path $mysqlData | Out-Null
try {
    Write-Host '[1/3] Initializing a new isolated test database...'
    $init = Start-Process -FilePath $mysqld -ArgumentList @('--initialize-insecure', ('--basedir="' + $mysqlBase + '"'), ('--datadir="' + $mysqlData + '"'), '--console') `
        -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $scratchRoot 'initialize.stdout.log') -RedirectStandardError (Join-Path $scratchRoot 'initialize.stderr.log')
    if ($init.ExitCode -ne 0) { throw ('MySQL initialization failed. Logs: ' + $scratchRoot) }
    $bootstrap = Join-Path $scratchRoot 'bootstrap.sql'
    $rootPassword = [Guid]::NewGuid().ToString('N')
    $sql = @"
CREATE DATABASE stock_insight_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE stock_insight_upgrade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'stock_insight'@'127.0.0.1' IDENTIFIED BY 'local_test_password';
GRANT ALL ON stock_insight_it.* TO 'stock_insight'@'127.0.0.1';
GRANT ALL ON stock_insight_upgrade.* TO 'stock_insight'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
"@
    [IO.File]::WriteAllText($bootstrap, $sql, (New-Object Text.UTF8Encoding($false)))
    $dbProcess = Start-Process -FilePath $mysqld -ArgumentList @(
        ('--basedir="' + $mysqlBase + '"'), ('--datadir="' + $mysqlData + '"'), ('--port=' + $MySqlPort),
        '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin', '--skip-name-resolve', ('--init-file="' + $bootstrap + '"'), '--console') `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $scratchRoot 'mysql.stdout.log') -RedirectStandardError (Join-Path $scratchRoot 'mysql.stderr.log')
    Set-TestEnvironment 'MYSQL_PWD' 'local_test_password'
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $dbProcess.Refresh()
        if ($dbProcess.HasExited) { throw ('Test MySQL exited. Logs: ' + $scratchRoot) }
        try {
            & $mysql --protocol=TCP --host=127.0.0.1 "--port=$MySqlPort" --connect-timeout=1 --user=stock_insight --execute='SELECT 1;' 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        } catch { } # Windows PowerShell 5.1에서는 준비 전 native stderr도 예외로 전달됩니다.
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw ('Test MySQL readiness timed out. Logs: ' + $scratchRoot) }
    Set-TestEnvironment 'JAVA_HOME' $javaRoot
    Set-TestEnvironment 'GRADLE_USER_HOME' (Join-Path $workspaceRoot 'work\gradle')
    Set-TestEnvironment 'MYSQL_IT_URL' ('jdbc:mysql://127.0.0.1:' + $MySqlPort + '/stock_insight_it?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC')
    Set-TestEnvironment 'MYSQL_IT_UPGRADE_URL' ('jdbc:mysql://127.0.0.1:' + $MySqlPort + '/stock_insight_upgrade?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC')
    Set-TestEnvironment 'MYSQL_IT_USER' 'stock_insight'
    Set-TestEnvironment 'MYSQL_IT_PASSWORD' 'local_test_password'
    Set-TestEnvironment 'APP_DATA_MODE' 'MOCK'
    Set-TestEnvironment 'SPRING_PROFILES_ACTIVE' 'mock'
    Set-TestEnvironment 'DART_API_KEY' ''
    Set-TestEnvironment 'FSC_API_KEY' ''
    Write-Host '[2/3] Running all tests, including MySQL and migration tests...'
    Push-Location $backendRoot
    try {
        & .\gradlew.bat --no-daemon --console=plain test --rerun-tasks
        if ($LASTEXITCODE -ne 0) { throw 'Gradle tests failed. See build/reports/tests/test/index.html.' }
    } finally { Pop-Location }
    $total = 0; $failed = 0; $errors = 0; $skipped = 0
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $backendRoot 'build\test-results\test') -Filter 'TEST-*.xml') {
        [xml]$report = Get-Content -LiteralPath $file.FullName
        $total += [int]$report.testsuite.tests; $failed += [int]$report.testsuite.failures
        $errors += [int]$report.testsuite.errors; $skipped += [int]$report.testsuite.skipped
    }
    if ($total -eq 0 -or $failed -gt 0 -or $errors -gt 0 -or $skipped -gt 0) { throw 'Test report is empty, failed or contains skipped tests.' }
    $summaryJson = [ordered]@{ verifiedAt=(Get-Date).ToUniversalTime().ToString('o');tests=$total;failures=$failed;errors=$errors;skipped=$skipped } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $scratchRoot 'test-summary.json'), $summaryJson, (New-Object Text.UTF8Encoding($false)))
    Write-Host ('[3/3] PASS: ' + $total + ' tests; no failures, errors or skips.') -ForegroundColor Green
    Write-Host ('Test logs: ' + $scratchRoot)
} finally {
    if ($null -ne $dbProcess) { $dbProcess.Refresh(); if (-not $dbProcess.HasExited) { Stop-Process -Id $dbProcess.Id } }
    foreach ($name in $previousEnv.Keys) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
}
