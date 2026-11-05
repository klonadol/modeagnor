param([ValidateSet('build','runClient','runServer','test','clean','probeClasses','runProbeServer','runProbeA','runProbeB')][string]$Task = 'build')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
}
if (-not $env:JAVA_HOME) {
    $launcherJava = Join-Path $env:APPDATA 'ElyPrismLauncher\java\java-runtime-delta'
    if (Test-Path (Join-Path $launcherJava 'bin\javac.exe')) { $env:JAVA_HOME = $launcherJava }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME to a Java 21 JDK (including javac).'
}
if ($Task -match 'Probe') {
    & .\gradlew.bat $Task -Pphase0 --console=plain
} else {
    & .\gradlew.bat $Task --console=plain
}
exit $LASTEXITCODE
