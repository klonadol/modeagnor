param([switch]$Dialogue)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
Set-Location $repo
if(!(Test-Path -LiteralPath 'local-assets/firefly/capabilities.json')){& "$PSScriptRoot/prepare-p33-face.ps1"}
$source=Join-Path $repo 'build/p33-check/client/resourcepacks/agnor-local'
if(!(Test-Path -LiteralPath $source)){throw 'Prepare the accepted P3.3/3.4 local resource pack first'}
$root=Join-Path $repo 'build/p35-check'
New-Item -ItemType Directory -Force "$root/server","$root/coord" | Out-Null
foreach($role in @('A','B')){
    $target=Join-Path $root "client$role/resourcepacks/agnor-local"
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item -Path "$source/*" -Destination $target -Recurse -Force
    @('resourcePacks:["vanilla","file/agnor-local"]','guiScale:2','maxFps:60','renderDistance:4','simulationDistance:5','entityDistanceScaling:1.0','pauseOnLostFocus:false','soundCategory_master:0.0') |
        Set-Content -LiteralPath "$root/client$role/options.txt" -Encoding ascii
}
'eula=true' | Set-Content -LiteralPath "$root/server/eula.txt" -Encoding ascii
@('server-ip=127.0.0.1','server-port=25584','online-mode=false','level-name=p35-fixture','level-type=minecraft:flat','generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[]}','view-distance=4','simulation-distance=2','spawn-protection=0') |
    Set-Content -LiteralPath "$root/server/server.properties" -Encoding ascii
$save=Join-Path $root 'server/p35-fixture/agnor'
New-Item -ItemType Directory -Force $save | Out-Null
@('version=1',("enabled="+$Dialogue.IsPresent.ToString().ToLowerInvariant()),'endpoint=http://127.0.0.1:11434','adapter=OLLAMA','model=ternary-bonsai-text-20k:latest','timeoutMillis=90000','maxOutputTokens=32','workers=1','queue=8','maxResponseBytes=65536') |
    Set-Content -LiteralPath "$save/main-dialogue.properties" -Encoding ascii
