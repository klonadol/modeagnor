$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
& "$PSScriptRoot/prepare-p3-check.ps1"
$repo=Split-Path $PSScriptRoot -Parent
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $repo 'build/p3-research/gemrender.jar'))
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('META-INF/jars/flywheel-fabric-1.21.1-1.0.6.jar'),(Join-Path $repo 'build/p3-research/flywheel.jar'),$true)
} finally { $zip.Dispose() }
