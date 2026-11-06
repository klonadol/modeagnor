# P3.2 real NPC rendering fixture

Run `powershell -ExecutionPolicy Bypass -File scripts/prepare-p32-check.ps1`.
The default uses the user's local restricted Furina GLB; models/textures stay in
ignored local-assets and the fixture client's resource pack. Do not distribute them.

With JAVA_HOME=JDK21 and GRADLE_USER_HOME=repo/.gradle, start:

```powershell
./gradlew.bat runP32Server -PnpcRenderCheck --console=plain
./gradlew.bat runP32Client -PnpcRenderCheck --console=plain
```

The client joins loopback:25582, captures before/after reload, then exits. The server
uses the production NpcRuntime and shuts down after client-done. Archive the marker
and images before rerunning; restart uses the same save and logs P32_RESTORED UUID.
For the no-GemRender fallback add `-PnpcFallback` to both commands.
No fixture entrypoint or model data is packaged in the normal AGNOR JAR.
