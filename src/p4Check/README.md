# P4 controlled acceptance

Opt-in `-PpackCheck`; никогда не входит в normal JAR. Требует уже подготовленных P3 Firefly ресурсов/rig из локального реестра; restricted bytes остаются в ignored build. `python scripts/prepare-p4-check.py` копирует исходники только в fixture dirs и создаёт local manifests. Фикстура повреждает только эти копии manifest; исходная модель не меняется.

PowerShell перед командами:

```powershell
$env:JAVA_HOME=Join-Path $env:APPDATA 'ElyPrismLauncher/java/java-runtime-delta'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle'
```

Перед новым сценарием архивируйте `build/p4-check/coord/*` (не оставляйте stale result markers). Для concurrent Gradle обязательно `--no-problems-report` (см. P3 отчёт004).

P4.2: `runP4Server` и затем `runP4A -PpackCheck`; остальные options как ниже. Проверяются enable/invalid reload/disable/rollback, same UUID/relationship23, ≥20 fresh pose frames/phase, PNG. После завершения сохраните server-result как coord/prior.json и архивируйте остальные markers. Restart обоих с `-PpackStage=restart`: manifest server остаётся corrupt, restore обязан пройти из last-known-good с тем же UUID.

P4.3:

```powershell
python scripts/prepare-p4-check.py --sync
./gradlew.bat runP4Server -PpackCheck -PpackStage=sync --console=plain --no-problems-report
./gradlew.bat runP4A -PpackCheck -PpackStage=sync --console=plain --no-problems-report
./gradlew.bat runP4B -PpackCheck -PpackStage=sync --console=plain --no-problems-report
```

Три команды запускаются в отдельных shells. Server loopback25586/offline synthetic fixture, не пользовательский мир. A получает operator, B остаётся без management rights. B starts with intentionally mismatched Firefly manifest. PUBLIC original CC0 triangle/notes служит только проверкой разрешённой передачи, не заменой целевых anime assets. Restricted Firefly никогда не передаётся. Перед повтором для чистого missing/download сценария архивируйте A cache/received-packs/public.zip вне сканируемого каталога. Сервер по окончании требует publicBytes>28KiB и fireflyBytes=0; A fetches, B opts out/reconnects, затем B восстанавливает Firefly через ручную локальную копию manifest и reload. Реальная Ollama отвечает в private P2 path; endpoint должен уже работать, повторно сервер Ollama не запускается.

Integrated: скопируйте завершённый synthetic dedicated world в `build/p4-check/integrated/saves/p4-world`, исключив `agnor`, `playerdata`, `stats`, `advancements`, `session.lock` (не перезаписывайте существующий AGNOR scope). Prepare создаёт отдельный dialogue config. `runP4Integrated -PpackCheck -PpackStage=syncIntegrated --console=plain --no-problems-report`: отдельный WorldId, shared local assets поэтому download не нужен; права/каталог/deny/выбор/real dialogue/recover проверяются в integrated. Для P4.2 integrated используйте default activation stage.

Fresh `server-result.json` и A/B-result.json должны иметь PASS; wrapper exit0 отдельно. PNG просмотра обязательны для rendering evidence. `abort.json` с `{}` останавливает sync fixture с FAIL; deadline6000ticks. Credentials здесь отсутствуют; generated private test History/DB, restricted snapshots и GPU PNG не публиковать.
