$ErrorActionPreference='Stop'
& "$PSScriptRoot/prepare-p32-check.ps1"
$repo=Split-Path $PSScriptRoot -Parent
$root=Join-Path $repo 'build/p33-check'
New-Item -ItemType Directory -Force "$root/server","$root/client/resourcepacks" | Out-Null
Copy-Item -LiteralPath "$repo/build/p32-check/client/resourcepacks/agnor-local" -Destination "$root/client/resourcepacks" -Recurse -Force
'resourcePacks:["vanilla","file/agnor-local"]' | Set-Content -LiteralPath "$root/client/options.txt" -Encoding ascii
'eula=true' | Set-Content -LiteralPath "$root/server/eula.txt" -Encoding ascii
@('server-ip=127.0.0.1','server-port=25583','online-mode=false','level-name=p33-fixture','view-distance=2','simulation-distance=2') |
    Set-Content -LiteralPath "$root/server/server.properties" -Encoding ascii
