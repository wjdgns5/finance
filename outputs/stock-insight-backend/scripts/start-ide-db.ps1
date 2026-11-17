param([switch]$CheckOnly)

# Starts only a persistent, isolated MySQL instance for IntelliJ development.
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$outputsRoot = Split-Path -Parent $backendRoot
$launcher = Join-Path $outputsRoot 'stock-insight-frontend\scripts\start-real-local.ps1'
& $launcher -DatabaseOnly -MySqlPort 13397 -InstanceName 'ide-real' -CheckOnly:$CheckOnly
