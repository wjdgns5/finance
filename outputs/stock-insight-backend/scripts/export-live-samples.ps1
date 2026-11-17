param(
    [Parameter(Mandatory = $true)]
    [ValidateCount(3, 3)]
    [ValidatePattern('^[0-9]{6}$')]
    [string[]]$StockCodes,
    [string]$ApiBase = 'http://127.0.0.1:8081/api/v1/companies'
)

$ErrorActionPreference = 'Stop'
$endpoint = [Uri]$ApiBase
if (-not $endpoint.IsLoopback -or $endpoint.Scheme -ne 'http') {
    throw '실행 중인 로컬 백엔드의 http 주소를 사용하세요.'
}
$project = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $project 'docs\live-samples'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$utf8 = New-Object Text.UTF8Encoding($false)

foreach ($stockCode in $StockCodes) {
    $financials = Invoke-RestMethod -Uri ($ApiBase + '/' + $stockCode + '/financials') -TimeoutSec 180
    if ($financials.meta.mode -ne 'REAL') { throw 'MOCK 응답을 실데이터 샘플로 저장하지 않습니다.' }
    if ($financials.data.years.Count -eq 0) { throw ('저장된 연간 자료가 없습니다: ' + $stockCode) }
    foreach ($year in $financials.data.years) {
        $source = Invoke-RestMethod -Uri ($ApiBase + '/' + $stockCode + '/financials/' + $year.fiscalYear + '/source?basis=' + $year.basis) -TimeoutSec 30
        if ($source.meta.mode -ne 'REAL' -or $source.data.financials.status -ne '000') {
            throw '실제 원본 재무 응답인지 확인할 수 없습니다.'
        }
        $json = $source | ConvertTo-Json -Depth 60
        if ($json -match '(?i)"(?:crtfc_key|serviceKey|api[-_]?key)"\s*:') {
            throw '인증 관련 필드가 발견되어 파일 저장을 중단했습니다.'
        }
        $path = Join-Path $destination ($stockCode + '-' + $year.fiscalYear + '-' + $year.basis + '.json')
        [IO.File]::WriteAllText($path, $json, $utf8)
        Write-Output ('원본 응답 저장: ' + $path)
    }
}
Write-Output '저장은 완료됐습니다. 실제 공시 화면과의 3종목 수치 대조는 체크리스트에 별도로 기록하세요.'
