param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'start-browser-check.ps1') -FrontendPort 5206 -BackendPort 18096 -MySqlPort 13396 -InstanceName 'phase6-browser' -QualityCheck -CheckOnly:$CheckOnly
