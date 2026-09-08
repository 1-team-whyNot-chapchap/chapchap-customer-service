param(
    [string]$AuthRepository = "",
    [string]$CustomerAiRepository = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$workspaceRoot = Split-Path -Parent $projectRoot
if ([string]::IsNullOrWhiteSpace($AuthRepository)) {
    $AuthRepository = Join-Path $workspaceRoot "chapchap-auth-service"
}
if ([string]::IsNullOrWhiteSpace($CustomerAiRepository)) {
    $CustomerAiRepository = Join-Path $workspaceRoot "Customer-Ai"
}

$authRoot = (Resolve-Path -LiteralPath $AuthRepository).Path
$customerAiRoot = (Resolve-Path -LiteralPath $CustomerAiRepository).Path
$initScript = Join-Path $projectRoot "src\isolatedContract\init.gradle"
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$materialDirectory = [System.IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ("chapchap-internal-auth-" + [Guid]::NewGuid().ToString("N"))))

if (!$materialDirectory.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "임시 인증 자료 경로가 시스템 임시 폴더 밖입니다."
}

New-Item -ItemType Directory -Path $materialDirectory | Out-Null
try {
    & (Join-Path $authRoot "gradlew.bat") `
        -p $authRoot `
        --no-daemon `
        --init-script $initScript `
        "-PisolatedContractSource=$projectRoot\src\isolatedContract\auth\java" `
        "-PisolatedContractOutput=$materialDirectory" `
        isolatedContractExport
    if ($LASTEXITCODE -ne 0) { throw "Auth-Service 격리 자료 생성 실패" }

    & (Join-Path $projectRoot "gradlew.bat") `
        -p $projectRoot `
        --no-daemon `
        --init-script $initScript `
        "-PisolatedContractSource=$projectRoot\src\isolatedContract\customer\java" `
        "-PisolatedContractOutput=$materialDirectory" `
        isolatedContractExport
    if ($LASTEXITCODE -ne 0) { throw "Customer-Service 격리 자료 생성 실패" }

    $python = Join-Path $customerAiRoot ".venv\Scripts\python.exe"
    & $python `
        (Join-Path $projectRoot "src\isolatedContract\python\verify_internal_auth_contract.py") `
        --material-dir $materialDirectory `
        --customer-ai-root $customerAiRoot
    if ($LASTEXITCODE -ne 0) { throw "Customer-AI 양방향 JWT 검증 실패" }
}
finally {
    if (Test-Path -LiteralPath $materialDirectory) {
        $resolvedMaterialDirectory = [System.IO.Path]::GetFullPath($materialDirectory)
        if (!$resolvedMaterialDirectory.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "임시 인증 자료 삭제 대상이 시스템 임시 폴더 밖입니다."
        }
        Remove-Item -Recurse -Force -LiteralPath $resolvedMaterialDirectory
    }
}
