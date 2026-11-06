$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$target = Join-Path $repo 'build/p3-research/gemrender.jar'
$expected = '5909D533036864A1A7BC3EB3CB65CF738899D855D21B97834702E9CD8EFABE4F'
New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
if (-not (Test-Path -LiteralPath $target)) {
    Invoke-WebRequest 'https://github.com/Warfactory-Official/GemRender/releases/download/v0.1.20/gemrender-0.1.20%2B1.21.1-fabric.jar' -OutFile $target -TimeoutSec 90
}
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected) { throw 'Candidate hash mismatch' }
Write-Output 'Pinned GemRender 0.1.20 Fabric 1.21.1 ready (local experiment only).'
