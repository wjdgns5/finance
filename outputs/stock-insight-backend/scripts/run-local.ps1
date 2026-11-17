param([switch]$SkipDocker, [string]$EnvironmentFile = '.env')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if ($EnvironmentFile -ne '.env' -and -not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw ('환경 파일이 없습니다: ' + $EnvironmentFile)
}

# .env를 코드로 실행하지 않고, 허용한 설정 이름만 환경변수로 읽습니다.
$allowed = @('MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'MYSQL_PORT', 'SERVER_PORT',
    'SPRING_PROFILES_ACTIVE', 'APP_DATA_MODE', 'DART_API_KEY', 'FSC_API_KEY', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD')
if (Test-Path -LiteralPath $EnvironmentFile) {
    foreach ($line in Get-Content -LiteralPath $EnvironmentFile) {
        if ($line -match '^([A-Z_]+)=(.*)$' -and $allowed -contains $Matches[1]) {
            $key = $Matches[1]
            $value = $Matches[2]
            if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($key))) {
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
}

if (($env:SPRING_PROFILES_ACTIVE -eq 'real' -and $env:APP_DATA_MODE -ne 'REAL') -or
    ($env:APP_DATA_MODE -eq 'REAL' -and $env:SPRING_PROFILES_ACTIVE -ne 'real')) {
    throw '실데이터 실행은 SPRING_PROFILES_ACTIVE=real과 APP_DATA_MODE=REAL을 함께 설정해야 합니다.'
}

if (-not $SkipDocker) {
    if ($env:SPRING_PROFILES_ACTIVE -eq 'real') {
        & docker compose -p stock-insight-real --env-file $EnvironmentFile up -d --wait
    } else { & docker compose up -d --wait }
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 컨테이너 시작에 실패했습니다. Docker Desktop 상태를 확인해 주세요.' }
}
if (-not $env:MYSQL_PORT) { $env:MYSQL_PORT = '13306' }
if (-not $env:MYSQL_DATABASE) {
    $env:MYSQL_DATABASE = if ($env:SPRING_PROFILES_ACTIVE -eq 'real') { 'stock_insight_real' } else { 'stock_insight_mock' }
}
if (-not $env:MYSQL_USER) { $env:MYSQL_USER = 'stock_insight' }
if (-not $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD = 'local_mock_password' }
if (-not $env:DB_URL) {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:' + $env:MYSQL_PORT + '/' + $env:MYSQL_DATABASE + '?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
}
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = $env:MYSQL_USER }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = $env:MYSQL_PASSWORD }
& .\gradlew.bat bootRun
if ($LASTEXITCODE -ne 0) { throw '백엔드 실행에 실패했습니다. 위 오류와 README 점검 항목을 확인해 주세요.' }
