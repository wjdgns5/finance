param(
    [int]$FrontendPort = 5174,
    [int]$BackendPort = 18081,
    [int]$MySqlPort = 13336,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$InstanceName = 'browser-check-local',
    [switch]$QualityCheck,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$frontendRoot = Split-Path -Parent $PSScriptRoot
$outputsRoot = Split-Path -Parent $frontendRoot
$workspaceRoot = Split-Path -Parent $outputsRoot
$backendRoot = Join-Path $outputsRoot 'stock-insight-backend'
$scratchRoot = Join-Path (Join-Path $workspaceRoot 'work') $InstanceName
$mysqlBase = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0'
$mysqlExe = Join-Path $mysqlBase 'bin\mysqld.exe'
$javaHomeForCheck = Join-Path $env:ProgramFiles 'Amazon Corretto\jdk21.0.12_8'
if (-not $javaHomeForCheck -or -not (Test-Path -LiteralPath (Join-Path $javaHomeForCheck 'bin\java.exe'))) {
    $javaHomeForCheck = $env:JAVA_HOME
}
$javaExe = Join-Path $javaHomeForCheck 'bin\java.exe'
$nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
$jarPath = Join-Path $backendRoot 'build\libs\stock-insight-backend-0.6.0.jar'
$vitePath = Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'
if ($QualityCheck) { $vitePath = Join-Path $PSScriptRoot 'quality-proxy.mjs'; if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'dist\index.html'))) { throw 'Build the frontend before the quality check.' } }

foreach ($required in @($mysqlExe, $javaExe, $jarPath, $vitePath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw ('Required file is missing: ' + $required) }
}
foreach ($port in @($FrontendPort, $BackendPort, $MySqlPort)) {
    if ($port -lt 1024 -or $port -gt 65535) { throw 'Ports must be between 1024 and 65535.' }
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } catch { throw ('Port is already in use: ' + $port + '. Use another port or stop the previous launcher with Enter.') } finally { $listener.Stop() }
}
if (@($FrontendPort, $BackendPort, $MySqlPort | Select-Object -Unique).Count -ne 3) {
    throw 'Frontend, backend and MySQL ports must differ.'
}
New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null
$mysqlData = Join-Path $scratchRoot 'mysql-data'
$mysqlProcess = $null
$backendProcess = $null
$frontendProcess = $null

try {
    Write-Host '[1/3] Preparing the isolated local test database...'
    # 설치된 MySQL 서비스의 데이터가 아닌 검증 전용 경로를 사용합니다.
    if (-not (Test-Path -LiteralPath (Join-Path $mysqlData 'auto.cnf'))) {
        New-Item -ItemType Directory -Path $mysqlData -Force | Out-Null
        & $mysqlExe --initialize-insecure "--basedir=$mysqlBase" "--datadir=$mysqlData" --console
        if ($LASTEXITCODE -ne 0) { throw 'Test MySQL initialization failed.' }
    }
    $bootstrapPath = Join-Path $scratchRoot 'bootstrap.sql'
    $bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS stock_insight_browser_mock CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'stock_insight'@'127.0.0.1' IDENTIFIED BY 'local_mock_password';
GRANT ALL ON stock_insight_browser_mock.* TO 'stock_insight'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY 'local_root_password';
"@
    [IO.File]::WriteAllText($bootstrapPath, $bootstrapSql, (New-Object Text.UTF8Encoding($false)))
    $mysqlArgs = @('--basedir="' + $mysqlBase + '"', '--datadir="' + $mysqlData + '"',
        ('--port=' + $MySqlPort), '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin',
        '--skip-name-resolve', ('--init-file="' + $bootstrapPath + '"'), '--console')
    $mysqlProcess = Start-Process -FilePath $mysqlExe -ArgumentList $mysqlArgs -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $scratchRoot 'mysql.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'mysql.stderr.log')
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:' + $MySqlPort + '/stock_insight_browser_mock?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
    $env:DB_USERNAME = 'stock_insight'
    $env:DB_PASSWORD = 'local_mock_password'
    $env:SERVER_PORT = [string]$BackendPort
    $env:API_PROXY_TARGET = 'http://127.0.0.1:' + $BackendPort
    $env:APP_DATA_MODE = 'MOCK'
    $env:SPRING_PROFILES_ACTIVE = 'mock'
    Write-Host '[2/3] Starting the Mock backend; waiting for data readiness...'
    $backendProcess = Start-Process -FilePath $javaExe -ArgumentList @('-jar', ('"' + $jarPath + '"')) `
        -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $scratchRoot 'backend.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'backend.stderr.log')

    $backendReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $mysqlProcess.Refresh()
        $backendProcess.Refresh()
        if ($mysqlProcess.HasExited) { throw ('MySQL exited. See ' + (Join-Path $scratchRoot 'mysql.stderr.log')) }
        if ($backendProcess.HasExited) { throw ('Backend exited. See ' + (Join-Path $scratchRoot 'backend.stdout.log')) }
        try {
            $check = Invoke-RestMethod -Uri ($env:API_PROXY_TARGET + '/api/v1/companies?query=990001') -TimeoutSec 2
            if ($check.data.Count -gt 0) { $backendReady = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $backendReady) { throw 'Backend readiness check timed out.' }

    Write-Host '[3/3] Starting the frontend and checking the API proxy...'
    $frontendProcess = Start-Process -FilePath $nodeExe -ArgumentList @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', [string]$FrontendPort, '--strictPort') `
        -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $scratchRoot 'frontend.stdout.log') `
        -RedirectStandardError (Join-Path $scratchRoot 'frontend.stderr.log')
    $previewUrl = 'http://127.0.0.1:' + $FrontendPort
    $frontendReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $frontendProcess.Refresh()
        if ($frontendProcess.HasExited) { throw ('Frontend exited. See ' + (Join-Path $scratchRoot 'frontend.stderr.log')) }
        try {
            $check = Invoke-RestMethod -Uri ($previewUrl + '/api/v1/companies/990001/financials?years=5') -TimeoutSec 2
            if ($check.meta.mode -eq 'MOCK' -and $check.data.years.Count -eq 5) { $frontendReady = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $frontendReady) { throw 'Frontend API proxy readiness check timed out.' }
    Write-Host ''
    Write-Host ('READY: ' + $previewUrl) -ForegroundColor Green
    Write-Host 'Keep this terminal open during the browser check.'
    Write-Host ('Logs: ' + $scratchRoot)
    if (-not $CheckOnly) {
        Read-Host 'Press Enter to stop the three owned test processes' | Out-Null
    }
} finally {
    # 이 스크립트가 시작한 프로세스만 중지하고 검증 데이터 경로는 보존합니다.
    foreach ($ownedProcess in @($frontendProcess, $backendProcess, $mysqlProcess)) {
        if ($null -ne $ownedProcess) {
            $ownedProcess.Refresh()
            if (-not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id }
        }
    }
}
