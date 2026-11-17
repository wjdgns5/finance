param(
    [int]$FrontendPort = 5175,
    [int]$BackendPort = 0,
    [int]$MySqlPort = 0,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$InstanceName = 'real-local',
    [switch]$DatabaseOnly,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$frontendRoot = Split-Path -Parent $PSScriptRoot
$outputsRoot = Split-Path -Parent $frontendRoot
$workspaceRoot = Split-Path -Parent $outputsRoot
$backendRoot = Join-Path $outputsRoot 'stock-insight-backend'
$scratchRoot = Join-Path (Join-Path $workspaceRoot 'work') $InstanceName
$settingsFile = Join-Path $backendRoot '.env.real'
if (-not (Test-Path -LiteralPath $settingsFile)) { throw '.env.real 파일이 없습니다. 먼저 API 키 설정을 준비하세요.' }

$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($settingsFile)) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}
if ($settings.SPRING_PROFILES_ACTIVE -ne 'real' -or $settings.APP_DATA_MODE -ne 'REAL') {
    throw '.env.real의 SPRING_PROFILES_ACTIVE=real, APP_DATA_MODE=REAL을 확인하세요.'
}
if ([string]::IsNullOrWhiteSpace($settings.DART_API_KEY)) { throw '.env.real에 DART_API_KEY를 입력하세요.' }
if ($BackendPort -eq 0) { $BackendPort = if ($settings.SERVER_PORT) { [int]$settings.SERVER_PORT } else { 8081 } }
if ($MySqlPort -eq 0) { $MySqlPort = if ($settings.MYSQL_PORT) { [int]$settings.MYSQL_PORT } else { 13307 } }
$database = if ($settings.MYSQL_DATABASE) { $settings.MYSQL_DATABASE } else { 'stock_insight_real' }
$dbUser = if ($settings.MYSQL_USER) { $settings.MYSQL_USER } else { 'stock_insight' }
$dbPassword = if ($settings.MYSQL_PASSWORD) { $settings.MYSQL_PASSWORD } else { 'local_real_password' }
$rootPassword = if ($settings.MYSQL_ROOT_PASSWORD) { $settings.MYSQL_ROOT_PASSWORD } else { [Guid]::NewGuid().ToString('N') }
if ($database -notmatch '^[A-Za-z0-9_]+$') { throw 'MYSQL_DATABASE는 영문·숫자·밑줄만 사용할 수 있습니다.' }

$mysqlBase = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0'
$mysqlExe = Join-Path $mysqlBase 'bin\mysqld.exe'
$mysqlClient = Join-Path $mysqlBase 'bin\mysql.exe'
if ($DatabaseOnly) {
    if (-not (Test-Path -LiteralPath $mysqlClient)) { throw ('필요한 파일이 없습니다: ' + $mysqlClient) }
} else {
    $javaRoot = Join-Path $env:ProgramFiles 'Amazon Corretto\jdk21.0.12_8'
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin\java.exe'))) { $javaRoot = $env:JAVA_HOME }
    if ([string]::IsNullOrWhiteSpace($javaRoot)) { throw 'Java 21의 JAVA_HOME을 설정하세요.' }
    $javaExe = Join-Path $javaRoot 'bin\java.exe'
    $nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
    $jarPath = Join-Path $backendRoot 'build\libs\stock-insight-backend-0.6.0.jar'
    $vitePath = Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'
    foreach ($required in @($javaExe, $jarPath, $vitePath)) {
        if (-not (Test-Path -LiteralPath $required)) { throw ('필요한 파일이 없습니다: ' + $required) }
    }
}
if (-not (Test-Path -LiteralPath $mysqlExe)) { throw ('필요한 파일이 없습니다: ' + $mysqlExe) }
$portsToCheck = if ($DatabaseOnly) { @($MySqlPort) } else { @($FrontendPort, $BackendPort, $MySqlPort) }
foreach ($port in $portsToCheck) {
    if ($port -lt 1024 -or $port -gt 65535) { throw '포트는 1024~65535 사이여야 합니다.' }
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } catch { throw ('이미 사용 중인 포트입니다: ' + $port) } finally { $listener.Stop() }
}
if (-not $DatabaseOnly -and @($FrontendPort, $BackendPort, $MySqlPort | Select-Object -Unique).Count -ne 3) {
    throw '프론트엔드·백엔드·MySQL 포트는 서로 달라야 합니다.'
}

function Sql-Literal([string]$value) { return "'" + $value.Replace('\', '\\').Replace("'", "''") + "'" }
function Test-BackendValidation([string]$url) {
    try { Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2 -ErrorAction Stop | Out-Null; return $false }
    catch { return ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 400) }
}
$previousEnv = @{}
function Set-TaskEnvironment([string]$name, [string]$value) {
    if (-not $previousEnv.ContainsKey($name)) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$mysqlProcess = $null
$backendProcess = $null
$frontendProcess = $null
New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null
$mysqlData = Join-Path $scratchRoot 'mysql-data'
try {
    if ($DatabaseOnly) { Write-Host '[DB] IDE 전용 로컬 DB를 준비합니다. 첫 실행은 잠시 걸릴 수 있습니다.' }
    else { Write-Host '[1/3] 실제 데이터용 로컬 DB를 준비합니다. 첫 실행은 잠시 걸릴 수 있습니다.' }
    if (-not (Test-Path -LiteralPath (Join-Path $mysqlData 'auto.cnf'))) {
        New-Item -ItemType Directory -Path $mysqlData -Force | Out-Null
        $init = Start-Process -FilePath $mysqlExe -ArgumentList @('--initialize-insecure',
            ('--basedir="' + $mysqlBase + '"'), ('--datadir="' + $mysqlData + '"'), '--console') `
            -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $scratchRoot 'initialize.stdout.log') `
            -RedirectStandardError (Join-Path $scratchRoot 'initialize.stderr.log')
        if ($init.ExitCode -ne 0) { throw ('DB 초기화 실패. 로그: ' + (Join-Path $scratchRoot 'initialize.stderr.log')) }
    }
    $bootstrap = Join-Path $scratchRoot 'bootstrap.sql'
    $sqlUser = Sql-Literal $dbUser
    $sqlPassword = Sql-Literal $dbPassword
    $sqlRootPassword = Sql-Literal $rootPassword
    $bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS $sqlUser@'127.0.0.1' IDENTIFIED BY $sqlPassword;
ALTER USER $sqlUser@'127.0.0.1' IDENTIFIED BY $sqlPassword;
GRANT ALL ON $database.* TO $sqlUser@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY $sqlRootPassword;
"@
    [IO.File]::WriteAllText($bootstrap, $bootstrapSql, (New-Object Text.UTF8Encoding($false)))
    $mysqlProcess = Start-Process -FilePath $mysqlExe -ArgumentList @(
        ('--basedir="' + $mysqlBase + '"'), ('--datadir="' + $mysqlData + '"'), ('--port=' + $MySqlPort),
        '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin', '--skip-name-resolve',
        ('--init-file="' + $bootstrap + '"'), '--console') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $scratchRoot 'mysql.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'mysql.stderr.log')

    Set-TaskEnvironment 'DB_URL' ('jdbc:mysql://127.0.0.1:' + $MySqlPort + '/' + $database + '?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC')
    Set-TaskEnvironment 'DB_USERNAME' $dbUser
    Set-TaskEnvironment 'DB_PASSWORD' $dbPassword
    Set-TaskEnvironment 'SERVER_PORT' ([string]$BackendPort)
    Set-TaskEnvironment 'SPRING_PROFILES_ACTIVE' 'real'
    Set-TaskEnvironment 'APP_DATA_MODE' 'REAL'
    Set-TaskEnvironment 'DART_API_KEY' $settings.DART_API_KEY
    Set-TaskEnvironment 'FSC_API_KEY' $settings.FSC_API_KEY
    Set-TaskEnvironment 'API_PROXY_TARGET' ('http://127.0.0.1:' + $BackendPort)

    if ($DatabaseOnly) {
        Set-TaskEnvironment 'MYSQL_PWD' $dbPassword
        $databaseReady = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            $mysqlProcess.Refresh()
            if ($mysqlProcess.HasExited) { throw ('MySQL 시작 실패. 로그: ' + (Join-Path $scratchRoot 'mysql.stderr.log')) }
            try {
                $probe = & $mysqlClient '--protocol=TCP' '--host=127.0.0.1' ('--port=' + $MySqlPort) `
                    ('--user=' + $dbUser) ('--database=' + $database) '--connect-timeout=1' `
                    '--batch' '--skip-column-names' '--execute=SELECT 1' 2>$null
                if ($LASTEXITCODE -eq 0 -and $probe -eq '1') { $databaseReady = $true; break }
            } catch { # Windows PowerShell 5.1 may turn startup stderr into an exception.
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $databaseReady) { throw 'MySQL 접속 대기 시간이 초과되었습니다. mysql.stderr.log를 확인하세요.' }
        Write-Host ('READY (DB ONLY): 127.0.0.1:' + $MySqlPort + '/' + $database) -ForegroundColor Green
        Write-Host ('DB_URL=' + $env:DB_URL)
        Write-Host ('DB 사용자·비밀번호는 백엔드 .env.real의 MYSQL_USER·MYSQL_PASSWORD를 사용합니다.')
        Write-Host '이 명령은 MySQL만 실행합니다. 백엔드는 IntelliJ, 화면은 VS Code에서 시작하세요.'
        if (-not $CheckOnly) { Read-Host '사용하는 동안 이 창을 열어 두세요. 백엔드를 먼저 끈 뒤 Enter로 DB 종료' | Out-Null }
        return
    }

    Write-Host '[2/3] REAL 백엔드를 시작합니다.'
    $backendProcess = Start-Process -FilePath $javaExe -ArgumentList @('-jar', ('"' + $jarPath + '"')) `
        -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $scratchRoot 'backend.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'backend.stderr.log')
    $backendReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $mysqlProcess.Refresh(); $backendProcess.Refresh()
        if ($mysqlProcess.HasExited) { throw ('MySQL 시작 실패. 로그: ' + (Join-Path $scratchRoot 'mysql.stderr.log')) }
        if ($backendProcess.HasExited) { throw ('백엔드 시작 실패. 로그: ' + (Join-Path $scratchRoot 'backend.stdout.log')) }
        if (Test-BackendValidation ($env:API_PROXY_TARGET + '/api/v1/companies/abc/financials')) { $backendReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $backendReady) { throw '백엔드 시작 대기 시간이 초과되었습니다.' }

    Write-Host '[3/3] 브라우저 화면과 API 프록시를 시작합니다.'
    $frontendProcess = Start-Process -FilePath $nodeExe -ArgumentList @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', [string]$FrontendPort, '--strictPort') `
        -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $scratchRoot 'frontend.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'frontend.stderr.log')
    $previewUrl = 'http://127.0.0.1:' + $FrontendPort
    $frontendReady = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $frontendProcess.Refresh()
        if ($frontendProcess.HasExited) { throw ('화면 시작 실패. 로그: ' + (Join-Path $scratchRoot 'frontend.stderr.log')) }
        if (Test-BackendValidation ($previewUrl + '/api/v1/companies/abc/financials')) { $frontendReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $frontendReady) { throw '화면 시작 대기 시간이 초과되었습니다.' }
    Write-Host ''
    Write-Host ('READY (REAL): ' + $previewUrl) -ForegroundColor Green
    Write-Host ('API: ' + $env:API_PROXY_TARGET + '/api/v1/companies')
    Write-Host '브라우저 검색창에 기아 또는 000270을 입력하고 기업을 선택하세요.'
    Write-Host '첫 조회는 공시 수집 때문에 시간이 걸릴 수 있습니다.'
    Write-Host ('로그: ' + $scratchRoot)
    if (-not $CheckOnly) { Read-Host '사용하는 동안 이 창을 열어 두세요. 종료하려면 Enter' | Out-Null }
} finally {
    foreach ($ownedProcess in @($frontendProcess, $backendProcess, $mysqlProcess)) {
        if ($null -ne $ownedProcess) {
            $ownedProcess.Refresh()
            if (-not $ownedProcess.HasExited) {
                Stop-Process -Id $ownedProcess.Id
                if (-not $ownedProcess.WaitForExit(10000)) { Write-Warning '프로세스 종료 대기 시간이 초과되었습니다.' }
            }
        }
    }
    foreach ($name in $previousEnv.Keys) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
}
