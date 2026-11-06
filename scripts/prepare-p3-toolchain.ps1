$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot/prepare-p3-check.ps1"
$trial = Join-Path $repo 'build/p3-toolchain'
$source = Join-Path $repo 'build/p3-research/GemRender'
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $repo 'build/p3-research/gemrender.jar'))
try {
    $entry = $archive.GetEntry('META-INF/jars/flywheel-fabric-1.21.1-1.0.6.jar')
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $repo 'build/p3-research/flywheel.jar'), $true)
} finally { $archive.Dispose() }
if (-not (Test-Path -LiteralPath (Join-Path $source 'gradlew.bat'))) { throw 'Pinned upstream source snapshot required (report 012)' }
New-Item -ItemType Directory -Force "$trial/gradle/wrapper" | Out-Null
Copy-Item -LiteralPath "$repo/settings.gradle" -Destination "$trial/settings.gradle"
Copy-Item -LiteralPath "$PSScriptRoot/p3-toolchain.gradle" -Destination "$trial/build.gradle"
Copy-Item -LiteralPath "$source/gradlew.bat" -Destination "$trial/gradlew.bat"
Copy-Item -LiteralPath "$source/gradle/wrapper/gradle-wrapper.jar" -Destination "$trial/gradle/wrapper/gradle-wrapper.jar"
Copy-Item -LiteralPath "$source/gradle/wrapper/gradle-wrapper.properties" -Destination "$trial/gradle/wrapper/gradle-wrapper.properties"
New-Item -ItemType Directory -Force "$trial/src/main/java/dev/agnor/p3", "$trial/src/main/resources/assets/agnor_p3" | Out-Null
Copy-Item -LiteralPath "$repo/src/p3Check/client/dev/agnor/p3/PaletteScreenCheck.java" -Destination "$trial/src/main/java/dev/agnor/p3/PaletteScreenCheck.java"
Copy-Item -LiteralPath "$repo/src/p3Check/resources/rig-morph.gltf" -Destination "$trial/src/main/resources/assets/agnor_p3/rig-morph.gltf"
@'
{"schemaVersion":1,"id":"agnor_p3","version":"1","environment":"client","entrypoints":{"client":["dev.agnor.p3.PaletteScreenCheck"]}}
'@ | Set-Content -LiteralPath "$trial/src/main/resources/fabric.mod.json" -Encoding utf8
Write-Output 'Isolated toolchain trial prepared in build/p3-toolchain.'
New-Item -ItemType Directory -Force "$trial/host/java/dev/agnor/p3", "$trial/host/resources", "$trial/server" | Out-Null
Copy-Item -LiteralPath "$repo/src/p3Check/host/dev/agnor/p3/HostPaletteCheck.java" -Destination "$trial/host/java/dev/agnor/p3/HostPaletteCheck.java"
Copy-Item -LiteralPath "$repo/src/p3Check/resources/rig-probe.json" -Destination "$trial/host/resources/rig-probe.json"
'{"schemaVersion":1,"id":"agnor_p3_host","version":"1","environment":"server","entrypoints":{"main":["dev.agnor.p3.HostPaletteCheck"]}}' | Set-Content -LiteralPath "$trial/host/resources/fabric.mod.json" -Encoding utf8
'eula=true' | Set-Content -LiteralPath "$trial/server/eula.txt" -Encoding ascii
@'
server-ip=127.0.0.1
server-port=25581
online-mode=false
level-name=p3-fixture
view-distance=2
simulation-distance=2
max-players=1
'@ | Set-Content -LiteralPath "$trial/server/server.properties" -Encoding ascii
