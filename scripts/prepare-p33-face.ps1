$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
Set-Location $repo
$collection=-join [char[]](0x440,0x430,0x437,0x43d,0x43e,0x435)
$model=Join-Path (Split-Path $repo -Parent) "assets/models/$collection/190034762488033771.vrm"
if((Get-FileHash -LiteralPath $model).Hash -ne '37C32B19702BD2D8B83E9837ECD98F1F167DF0123B48BFFA0679FE56CF5E21A2'){throw 'Unexpected face model'}
& python "$PSScriptRoot/partition-local-model.py" $model 'local-assets/firefly' 'agnor:models/face'
if($LASTEXITCODE -ne 0){throw 'Face adaptation failed'}
$target=Join-Path $repo 'build/p33-check/client/resourcepacks/agnor-local/assets/agnor/models/face'
New-Item -ItemType Directory -Force $target | Out-Null
Copy-Item -Path 'local-assets/firefly/*.glb','local-assets/firefly/texture-*' -Destination $target
