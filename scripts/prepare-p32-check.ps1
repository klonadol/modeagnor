param([string]$Model='')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
if(-not $Model) {
    $collection=-join [char[]](0x440,0x430,0x437,0x43d,0x43e,0x435)
    $Model=Join-Path (Split-Path $repo -Parent) ("assets/models/$collection/genshin_impact_-_furina.glb")
}
Set-Location $repo
if((Get-FileHash -LiteralPath $Model -Algorithm SHA256).Hash -ne '8065C134CC6D6E8E03A0A073A3953F388E8AE6857B7A5D967E4AA7226A8A1F45') {
    throw 'This dev presentation descriptor is pinned to the audited Furina GLB; audit another model before changing it.'
}
& "$PSScriptRoot/prepare-renderer.ps1"
& python "$PSScriptRoot/partition-local-model.py" $Model 'local-assets/furina'
if($LASTEXITCODE -ne 0){throw 'Model adaptation failed'}
$check=Join-Path $repo 'build/p32-check'
$target=Join-Path $check 'client/resourcepacks/agnor-local/assets/agnor/models/dev'
New-Item -ItemType Directory -Force $target, "$check/server" | Out-Null
Copy-Item -Path 'local-assets/furina/*.glb','local-assets/furina/texture-*' -Destination $target
'{"pack":{"pack_format":34,"description":"AGNOR local restricted model fixture"}}' | Set-Content -LiteralPath "$check/client/resourcepacks/agnor-local/pack.mcmeta" -Encoding utf8
if(-not(Test-Path -LiteralPath "$check/client/options.txt")) {
    'resourcePacks:["vanilla","file/agnor-local"]' | Set-Content -LiteralPath "$check/client/options.txt" -Encoding ascii
}
'eula=true' | Set-Content -LiteralPath "$check/server/eula.txt" -Encoding ascii
@('server-ip=127.0.0.1','server-port=25582','online-mode=false','level-name=p32-fixture','view-distance=3','simulation-distance=3','max-players=2') |
    Set-Content -LiteralPath "$check/server/server.properties" -Encoding ascii
Write-Output 'P3.2 fixture ready; archive client-done before a new run. Assets remain local.'
