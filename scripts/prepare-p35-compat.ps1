param([ValidateSet('sodium','iris','shader')][string]$Mode='sodium')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
Set-Location $repo
$target=Join-Path $repo 'build/p35-check/compat'
New-Item -ItemType Directory -Force $target | Out-Null
$files=@(
    @{name='sodium.jar';id='u1OEbNKx';version='0.6.13+mc1.21.1';url='https://cdn.modrinth.com/data/AANobbMI/versions/u1OEbNKx/sodium-fabric-0.6.13%2Bmc1.21.1.jar';sha512='13032e064c554fc8671573dadb07bc70e6ea2f68706c65c086c4feb1d2f664346a3414cbf9d1367b42b8d063a35e40f2f967ef9af31642e1f0093b852161fe91'},
    @{name='iris.jar';id='zsoi0dso';version='1.8.8+mc1.21.1';url='https://cdn.modrinth.com/data/YL57xq9U/versions/zsoi0dso/iris-fabric-1.8.8%2Bmc1.21.1.jar';sha512='2e6ba2ffa1e1a6799288245a7e0ac68ee8df1d41b98362189df58f535cae34fa9277801e4136633467341b7dae5be0e5c698011b480b3d91b66d3dd4f7567aa6'},
    @{name='ComplementaryReimagined_r5.5.1.zip';id='sAAjYvFB';version='r5.5.1';url='https://cdn.modrinth.com/data/HVnmMxH1/versions/sAAjYvFB/ComplementaryReimagined_r5.5.1.zip';sha512='e3168c3e8b9a59e70dbc7dd27931d8b245e5b16b093c87f1dccd01edc3a8be0fb14153fe8175e822ea6463ac0d715de377d186455580915c6f9d98c02b39194f'}
)
foreach($file in $files){
    $path=Join-Path $target $file.name
    if(!(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA512).Hash.ToLowerInvariant() -ne $file.sha512){
        $temp="$path.tmp"
        Invoke-WebRequest -Uri $file.url -OutFile $temp -UseBasicParsing -TimeoutSec 60
        if((Get-FileHash -LiteralPath $temp -Algorithm SHA512).Hash.ToLowerInvariant() -ne $file.sha512){throw "Checksum mismatch: $($file.name)"}
        Move-Item -LiteralPath $temp -Destination $path -Force
    }
}
$files | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$target/manifest.json" -Encoding utf8
& python -c @'
import pathlib,zipfile,hashlib,json
root=pathlib.Path('build/p35-check/compat')
out=root/'iris-libs';out.mkdir(exist_ok=True)
manifest=[]
with zipfile.ZipFile(root/'iris.jar') as archive:
    for name in ['antlr4-runtime-4.13.1.jar','glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar']:
        data=archive.read('META-INF/jars/'+name)
        (out/name).write_bytes(data)
        manifest.append({'file':name,'sha512':hashlib.sha512(data).hexdigest(),'source':'verified Iris nested JAR'})
(root/'iris-nested-manifest.json').write_text(json.dumps(manifest,indent=2))
'@
if($LASTEXITCODE -ne 0){throw 'Iris nested-library extraction failed'}
if($Mode -ne 'sodium'){
    # A previously cached GemRender remap made without Iris can retain intermediary
    # inherited method names. Evict only that generated, pinned artifact; keep the raw JAR.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $cacheRoot=[IO.Path]::GetFullPath((Join-Path $repo '.gradle/loom-cache/remapped_mods'))
    if(Test-Path -LiteralPath $cacheRoot){
        foreach($cached in Get-ChildItem -LiteralPath $cacheRoot -Recurse -File -Filter 'gemrender-*-5909d53303.jar'){
            $bad=$false;$zip=[IO.Compression.ZipFile]::OpenRead($cached.FullName)
            try{
                $entry=$zip.GetEntry('com/wf/gemrender/iris/guest/GuestRenderer.class')
                if($entry){$stream=$entry.Open();$memory=New-Object IO.MemoryStream;try{$stream.CopyTo($memory);$bad=[Text.Encoding]::UTF8.GetString($memory.ToArray()).Contains('method_60897')}finally{$stream.Dispose();$memory.Dispose()}}
            }finally{$zip.Dispose()}
            if($bad){
                $full=[IO.Path]::GetFullPath($cached.FullName)
                if(!$full.StartsWith($cacheRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected generated remap path'}
                Copy-Item -LiteralPath $full -Destination (Join-Path $target ($cached.Name+'.stale')) -Force
                Remove-Item -LiteralPath $full
                Write-Output 'Evicted stale generated GemRender/Iris remap'
            }
        }
    }
    if(!$env:JAVA_HOME){$env:JAVA_HOME=Join-Path $env:APPDATA 'ElyPrismLauncher/java/java-runtime-delta'}
    $env:GRADLE_USER_HOME=Join-Path $repo '.gradle'
    & "$repo/gradlew.bat" compileP35CheckJava -PposeCheck "-PposeCompat=$Mode" --console=plain
    if($LASTEXITCODE -ne 0){throw 'Iris-aware dev remap preparation failed'}
}
$client=Join-Path $repo 'build/p35-check/clientA'
New-Item -ItemType Directory -Force "$client/config","$client/shaderpacks" | Out-Null
@(("enableShaders="+($Mode -eq 'shader').ToString().ToLowerInvariant()),("shaderPack="+$(if($Mode -eq 'shader'){'ComplementaryReimagined_r5.5.1.zip'}else{''}))) |
    Set-Content -LiteralPath "$client/config/iris.properties" -Encoding ascii
if($Mode -eq 'shader'){Copy-Item -LiteralPath "$target/ComplementaryReimagined_r5.5.1.zip" -Destination "$client/shaderpacks/ComplementaryReimagined_r5.5.1.zip" -Force}
Write-Output "Pinned compatibility artifacts ready: $Mode"
