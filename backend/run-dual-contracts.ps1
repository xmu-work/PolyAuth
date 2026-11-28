param(
    [string]$StructuredUrl = "http://localhost:8080/api/v1",
    [string]$OopUrl = "http://localhost:8080/api/v1"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$maven = Join-Path $PSScriptRoot "mvnw.cmd"
if (-not (Test-Path $maven)) {
    throw "未找到 Maven Wrapper: $maven"
}

& $maven "-Dtest=DualBackendContractTest" "-Dcontract.baseUrl=$StructuredUrl" test
if ($LASTEXITCODE -ne 0) {
    throw "structured 策略契约测试失败"
}

& $maven "-Dtest=DualBackendContractTest" "-Dcontract.baseUrl=$OopUrl" test
if ($LASTEXITCODE -ne 0) {
    throw "ddd 策略契约测试失败"
}

Write-Host "同一 JAR 的 structured 与 ddd 策略均通过共享契约测试。" -ForegroundColor Green
