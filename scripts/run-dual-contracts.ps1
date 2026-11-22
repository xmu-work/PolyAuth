param(
    [string]$StructuredUrl = "http://localhost:8080/api/v1",
    [string]$OopUrl = "http://localhost:8081/api/v1"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$maven = Join-Path $root "mvnw.cmd"
if (-not (Test-Path $maven)) {
    throw "未找到 Maven Wrapper: $maven"
}

& $maven -pl contract-tests test "-Dcontract.baseUrl=$StructuredUrl"
if ($LASTEXITCODE -ne 0) {
    throw "结构化后端契约测试失败"
}

& $maven -pl contract-tests test "-Dcontract.baseUrl=$OopUrl"
if ($LASTEXITCODE -ne 0) {
    throw "OOP 后端契约测试失败"
}

Write-Host "两套后端均通过共享契约测试。" -ForegroundColor Green
