param(
    [ValidateSet('Mock', 'Real')][string]$Mode = 'Mock',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
# 이전 단계의 포트와 DB 파일을 재사용하지 않는 Phase 5 전용 진입점입니다.
if ($Mode -eq 'Mock') {
    & (Join-Path $PSScriptRoot 'start-browser-check.ps1') -FrontendPort 5194 -BackendPort 18094 -MySqlPort 13394 -InstanceName 'phase5-browser-mock' -CheckOnly:$CheckOnly
} else {
    & (Join-Path $PSScriptRoot 'start-real-local.ps1') -FrontendPort 5195 -BackendPort 18095 -MySqlPort 13395 -InstanceName 'phase5-browser-real' -CheckOnly:$CheckOnly
}
