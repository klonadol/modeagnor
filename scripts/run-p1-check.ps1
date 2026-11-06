param(
    [ValidateSet('A','B','I')][string]$WorldTag = 'A',
    [ValidateSet(1,2)][int]$Stage = 1,
    [ValidateSet('Orchestrate','Server','A','B','Integrated')][string]$Role = 'Orchestrate'
)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
Set-Location $taskRepo
$env:GRADLE_USER_HOME = Join-Path $taskRepo '.gradle'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:APPDATA 'ElyPrismLauncher/java/java-runtime-delta' }
$taskEvidence = Join-Path $taskRepo 'build/p1-check'
New-Item -ItemType Directory -Force -Path $taskEvidence | Out-Null
if ($Role -ne 'Orchestrate') {
    $taskName = if ($Role -eq 'Server') { 'runP1Server' } elseif ($Role -eq 'Integrated') { 'runP1Integrated' } else { "runP1$Role" }
    & .\gradlew.bat $taskName -Pp1Check "-Pp1World=$WorldTag" "-Pp1Stage=$Stage" --console=plain *> (Join-Path $taskEvidence "$WorldTag$Stage-$Role.log")
    exit $LASTEXITCODE
}
$taskCoord = Join-Path $taskEvidence 'coord'
if (Test-Path -LiteralPath (Join-Path $taskCoord "$WorldTag$Stage-SERVER-PASS")) { throw 'Completed fixture already exists; archive it before rerunning' }
$taskShell = (Get-Process -Id $PID).Path
$taskChildren = @()
function Start-FixtureProcess([string]$childRole) {
    $taskArguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $PSCommandPath + '"'),'-WorldTag',$WorldTag,'-Stage',"$Stage",'-Role',$childRole)
    Start-Process -FilePath $taskShell -ArgumentList $taskArguments -WorkingDirectory $taskRepo -WindowStyle Hidden -PassThru
}
try {
    if ($WorldTag -eq 'I') { $taskChildren += Start-FixtureProcess 'Integrated' }
    else {
        $taskChildren += Start-FixtureProcess 'Server'
        $taskServerLog = Join-Path $taskEvidence "$WorldTag$Stage-Server.log"
        $taskDeadline = (Get-Date).AddMinutes(3)
        while ($true) {
            if ((Test-Path -LiteralPath $taskServerLog) -and (Select-String -LiteralPath $taskServerLog -Pattern 'Done \(' -Quiet)) { break }
            if ($taskChildren[0].HasExited -or (Get-Date) -gt $taskDeadline) { throw 'Fixture server failed to become ready; inspect its log' }
            Start-Sleep -Seconds 2
        }
        Write-Output "Server ready: world=$WorldTag stage=$Stage"
        $taskChildren += Start-FixtureProcess 'A'
        $taskChildren += Start-FixtureProcess 'B'
    }
    $taskDeadline = (Get-Date).AddMinutes(8)
    while (@($taskChildren | Where-Object { -not $_.HasExited }).Count -gt 0) {
        if ((Get-Date) -gt $taskDeadline) { throw 'P1 fixture process timeout' }
        Start-Sleep -Seconds 5
    }
    foreach ($taskChild in $taskChildren) { if ($taskChild.ExitCode -ne 0) { throw "Fixture child exit code $($taskChild.ExitCode)" } }
    if (-not (Test-Path -LiteralPath (Join-Path $taskCoord "$WorldTag$Stage-SERVER-PASS"))) { throw 'No server PASS evidence' }
    if (Test-Path -LiteralPath (Join-Path $taskCoord "$WorldTag$Stage-SERVER-FAIL")) { throw 'Server recorded FAIL' }
    Write-Output "P1 FIXTURE PASS world=$WorldTag stage=$Stage"
} finally {
    foreach ($taskChild in $taskChildren) { if (-not $taskChild.HasExited) { $taskChild.Kill($true) } }
}
