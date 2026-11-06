# P3.1 bounded renderer experiment

Continuation: `scripts/prepare-p3-toolchain.ps1` stages a separate upgraded build.
Run its `runServer -PhostOnly` before `runClient`; see report 013 for commands,
exact matrix, GUI morph upload fix, real screenshot/reload/reset evidence and limits.
`client/` and `host/` are staged roots, excluded from the ordinary AGNOR JAR.

Original fixture: `resources/rig-morph.gltf`. Three vertices, two skin joints,
inverse binds, one POSITION morph, one LINEAR translation/weights clip.
Regenerate with `python scripts/create-p3-fixture.py`. No downloaded character assets.

From the repository, with Java 21 and the repository Gradle user home:

```powershell
powershell -File scripts/prepare-p3-check.ps1
$env:JAVA_HOME = Join-Path $env:APPDATA 'ElyPrismLauncher/java/java-runtime-delta'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
./gradlew.bat p3Headless -Pp3Check --console=plain
./gradlew.bat compileClientJava -Pp3Check -Pp3Client --console=plain
```

The second command is deliberately a compatibility probe: GemRender 0.1.20 fails
on Loom 1.7.4 because it was built with Loom 1.16.3. A failure is not a successful
client override or visual acceptance. Do not bypass the version guard.

`p3Headless` uses only the pinned raw GemRender JAR, JOML 1.10.5 and Gson 2.10.1,
without Minecraft/Fabric/LWJGL on its runtime classpath. This does not prove
dedicated-server mod loading: the published JAR is marked client-only.
No check source or candidate dependency enters the normal mod JAR.

See ADR-0017 and report 012 for source pins, evidence and the next bounded proof.
