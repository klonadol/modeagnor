# P3.3 controlled host pose/face acceptance

Prepare with `scripts/prepare-p33-check.ps1` and `scripts/prepare-p33-face.ps1`
(Windows PowerShell: add `-ExecutionPolicy Bypass`). Assets are pinned local user
files, TEMPORARY_RESTRICTED, and stay outside Git/JAR. Java21 and repository
GRADLE_USER_HOME are required.

Run each stage's server first, then its client:

```powershell
./gradlew.bat runP33Server -PmotionCheck -PmotionCharacter=firefly -PmotionStage=a --console=plain
./gradlew.bat runP33Client -PmotionCheck -PmotionCharacter=firefly -PmotionStage=a --console=plain
```

Stages: `a` clip/crossfade/masks/additive; `b` two-bone reach/limits/unreachable;
`c` foot/look/reach arbitration/cancel/expiry; `d` eyes/expressions/blink/mouth/reset.
Stage d always selects Firefly; omitting motionCharacter for a–c selects the earlier
Furina baseline. Host checks happen on a real dedicated server at loopback:25583.
It computes original-rig poses and per-part palettes, saves atomically, and stops.
The client renders only host-baked matrices/morphs and exits after capture.

The Gradle tasks verify stage/revision/partition count and reject stale data.
A server exception with Java exit0 cannot satisfy the output gate. Screenshots:
`build/p33-check/pose-proof-a.png` through `pose-proof-d.png`; host JSON is beside
them. These JSON files are a fixture transport, not the P3.5 gameplay protocol.

See ADR-0019 and report 2026-11-06-001 for accepted evidence, failed attempts,
recovery after power-off, Blender checkpoints and remaining phase work.

P3.4 continues sequentially: `e` gait (idle/start/walk/run/turn/stop), then `f`
secondary (same Firefly, head turns, ten explicit chains/91segments, four core
sphere proxies, pause/resume/teleport). Always pass `-PmotionCharacter=firefly`.
For side capture add `'-PmotionView=1.5707963'`, for three-quarter
`'-PmotionView=0.7853982'` (quote decimal Gradle properties in PowerShell).
Client tasks now require a fresh screenshot. Copy `pose-proof-e.png`/`f.png`
after each successful run to retain separate views. Metrics are in
`gait-metrics.json` and `secondary-metrics.json`. These scenes normalize away
root translation; numerical stance anchors/yaw test world contact invariants.
See ADR-0020/report002 for profile limits and rejected full-chain experiment.
