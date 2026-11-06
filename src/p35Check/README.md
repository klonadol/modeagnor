# P3.5 gameplay acceptance

Opt-in fixtures are excluded from the production JAR. Local user assets remain
TEMPORARY_RESTRICTED; see the asset replacement register and ADR-0021.

Prepare the accepted P3.3/3.4 resource pack with `prepare-p33-check.ps1` and
`prepare-p33-face.ps1`, then run `scripts/prepare-p35-check.ps1`. For real dialogue
pass `-Dialogue` to the latter; it configures the local Ollama endpoint/model
already tested in P2. Windows PowerShell scripts may need `-ExecutionPolicy Bypass`.
Use Java21 and repository-local `GRADLE_USER_HOME` as in the other fixtures.

Start the server first and wait for `Done` and `AGNOR host motion enabled`, then
start the client(s) promptly in separate terminals:

```powershell
./gradlew.bat runP35Server -PposeCheck -PposeStage=multi --console=plain --no-problems-report
./gradlew.bat runP35A -PposeCheck -PposeStage=multi --console=plain --no-problems-report
./gradlew.bat runP35B -PposeCheck -PposeStage=multi --console=plain --no-problems-report
```

The server is restricted to loopback:25584. `smoke` is the smaller one-client
live root/blink/stream proof. `multi` measures0/1/5NPC, performs real SAY/RETRY,
same-process B reconnect, wrong-rig neutral presentation and stale-frame rejection.
Shared sequence hashes are checked against the authoritative server frame. The
fixture does not broadcast private dialogue text; B must receive no private reply.

After a compatibility run, rerun `prepare-p35-check.ps1 -Dialogue` before `multi`:
compatibility preparation intentionally disables dialogue. The server task checks
this configuration before launch. The final budget run and its predeclared limits
are documented in `docs/reports/2026-11-06-004-phase3-acceptance.md`.

For integrated acceptance, copy the **closed synthetic** dedicated world to
`build/p35-check/integrated/saves/p35-world`, the resource pack/options into that
run directory, then run `runP35Integrated -PposeCheck -PposeStage=integrated`.
It runs the same0/1/5NPC and real dialogue/retry path in one process.

Compatibility uses one client. Run `prepare-p35-compat.ps1 -Mode sodium|iris|shader`
first; dependencies and the shaderpack are SHA512-pinned official Modrinth files.
Server flags: `-PposeCheck -PposeStage=compat-sodium|compat-iris-off|compat-iris-on`.
Client A also needs `-PposeCompat=sodium|iris|shader` respectively. Iris shader
state and actual mod versions are read from the running process. Compatibility
measurement waits for actual rendered world frames before starting its clock.

The helper extracts the three pinned libraries nested in Iris (ANTLR runtime,
glsl-transformer and jcpp) for Loom's dev file dependency. Iris is an opt-in
`modClientImplementation` so remapping sees ExtendedShader's inheritance. Before
starting the server, the helper detects an obsolete `method_60897` call in the
generated named GemRender cache, backs up/removes only that generated JAR when
needed, and prewarms the Iris-aware remap. The pinned upstream JAR is unchanged.
ONE waits for actual mesh submissions before its warm-up/measurement window.

Outputs: `build/p35-check/coord/{server,A,B}-result.json`, view captures and small
coordination markers. Archive them after a successful run because the next run
clears its markers. Use server+A for single-client/integrated cases; B outputs
belong only to `multi`. JSON/PNG gates reject missing or failed results, including
Minecraft callbacks that return Java exit0 after a crash. Windows target-file
sharing conflicts are retried on later fixture ticks without blocking the game.

GPU queries measure the **whole-world render pass**, with a baseline and warm-up;
they are not isolated skinning timings. Host metrics exclude fixture file IO and
report real payload bytes before Minecraft framing. Chain-local physics fallback
counts are separate from whole-NPC failures. See report003 for exact evidence,
failed attempts and the selected renderer matrix.

For concurrent Gradle invocations use `--no-problems-report` (verified by Gradle9.7.1 CLI help). Otherwise successful Java fixtures can race while Gradle replaces the shared HTML problems report. This does not change test result gates. Report004 records the one wrapper failure separately from scenario PASS.
