# Отчёт 006: управление NPC и завершение P1

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10).
- **Статус:** Done — P1.4 и интеграционный сценарий P1 в перечисленных границах.
- **Ветка / HEAD до и после:** main / `338b491517a23720b7ecacfe32fc8c7d13ef8ad6`. Коммит, reset, переключение ветки или публикация не выполнялись.
- **ADR:** [0010](../decisions/ADR-0010-world-scoped-storage.md), [0011](../decisions/ADR-0011-npc-lifecycle.md), [0012](../decisions/ADR-0012-npc-management-protocol.md).

## Цель, исходное состояние и последовательность

Продолжить roadmap последовательно, не начинать зависимые задачи до реализации предыдущих; разрешено продолжать до завершения фазы. Вход P1.3 уже реализован и подтверждён отчётом 005 (typed repository, stable UUID, lifecycle/relationships, integrated/dedicated restart). Существующие dirty/untracked файлы предыдущих работ сохранены. Новая работа — P1.4, затем полный пользовательский сценарий P1. P2 и будущие подсистемы не начинались.

## Реализация

- `NpcManagement`: единый server-thread обработчик команд и C2S. Trusted actor/права берутся из connection; scope — из текущего runtime, проверяется до SQL. Delete разрешён owner/admin level ≥2, select/own relationship — в той же dimension и радиусе 64 блока. После async inspect заново проверяются текущие connection/runtime и права.
- `/agnor spawn`, `/agnor select [UUID]`, `/agnor delete [UUID]`, `/agnor relationship <UUID> <-100..100>`. Spawn/select/relationship выбирают NPC для текущего соединения; select/delete без UUID используют selection. Disconnect/stop очищает connection state. Feedback читаемый, без SQL/paths/config/exception text.
- `NpcWire`/`NpcProtocol`: отдельный versioned C2S/S2C, request ровно 65 bytes, response ≤256 bytes; identifiers ≤64 ASCII bytes. INFO даёт WorldId, остальные действия требуют exact scope. Результат адресный, без других players' relationship records или owner UUID. Version/action/sequence/opID/NpcId/affinity и decode bounds валидируются; неподдерживаемые dimension identifiers отвергаются без effect. Failure snapshot пустой.
- Ограничение ingress: одна in-flight operation и минимум 10 server ticks между admitted requests на connection. В SQL create transaction проверяются 8 active NPC на owner / 64 на save после replay detection. Это policy bounds, не измерение performance.
- `NpcRuntime.inspect` выдаёт typed snapshot с актуальным pose entity, без SQL на server tick. Существующая async storage/session/lifecycle модель сохранена.
- Unit tests codec/policy/cap; отдельный `src/p1Check` и opt-in run configs. Test filesystem используется только для координации; NPC effects идут через реальные Minecraft commands/custom play packets, а не через прямые repository вызовы стенда.

Подробный контракт и решения — ADR-0012. Файлы: `network/{NpcWire,NpcProtocol,NpcManagement,NpcFeedback}.java`, `character/NpcAccess.java`, изменения NpcRuntime/NpcRepository/Agnor/AgnorClient; `src/test/.../{NpcWireTest,NpcAccessTest,NpcRepositoryTest}`; `scripts/{p1-check.gradle,run-p1-check.ps1}`, opt-in build.gradle и `src/p1Check/`; docs/README/workspace указатель.

## Unit/build

JDK 21 из `%APPDATA%/ElyPrismLauncher/java/java-runtime-delta`, локальный `GRADLE_USER_HOME=<repo>/.gradle`.

```powershell
.\gradlew.bat build p1CheckClasses -Pp1Check --console=plain
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
```

Финальная build: **exit 0**, `BUILD SUCCESSFUL in 9s`, 7 tasks executed / 3 up-to-date. **22 tests**, failures/errors/skipped=0: DataRoots 3, DefinitionRegistry 2, WorldStorage 8, NpcRepository 5, NpcWire 3, NpcAccess 1. Новые проверки: exact request size/truncation/trailing bytes, version/action/sequence/opID/NpcId/affinity, response max identifiers и 224-byte maximum v1, malformed identifiers/oversized/truncated response, owner/admin/range/dimension policy, cap/replay при заполненном owner лимите.

Во время подготовки harness была compile error: `unregisterGlobalReceiver` требует Identifier, а был передан CustomPayload.Id. Исправлено на `.ID.id()` до игровых запусков. Это не принятый PASS. Финальное усиление malformed guards (пустой NpcId, response affinity/status и failure sanitization) проверено unit/build после игровых прогонов; валидный wire format и command path ими не менялись. Полная игровая матрица ради этих guards повторно не запускалась.

## Реальные игровые прогоны

Стенд `-Pp1Check` и отдельный test mod не входят в normal JAR. Dedicated — исключительно `127.0.0.1:25577`, offline-mode только для двух synthetic dev identities. Скопирована ранее принятая dev EULA из `run/eula.txt`, новая EULA автоматически не принималась. Пользовательские сохранения и установки не менялись. Client options скопированы из предыдущего dev fixture для уже завершённого accessibility onboarding.

Основные команды после подготовки loopback server.properties:

```powershell
.\scripts\run-p1-check.ps1 -WorldTag A -Stage 1
.\scripts\run-p1-check.ps1 -WorldTag A -Stage 2
# Между A2 и B1: level-name=world-B в isolated server.properties
.\scripts\run-p1-check.ps1 -WorldTag B -Stage 1
.\scripts\run-p1-check.ps1 -WorldTag B -Stage 2
.\scripts\run-p1-check.ps1 -WorldTag I -Stage 1
.\scripts\run-p1-check.ps1 -WorldTag I -Stage 2
```

`Stage 1`: оба клиента входят на dedicated server; A создаёт NPC командой, назначает своё отношение; B select получает собственное default=0 без owner relation, чужой delete получает DENIED, B записывает своё отдельное отношение. A select подтверждает, что его record не изменён. Version mismatch, чужой WorldId и неизвестное action дают ошибки до effect. В мире B дополнительно передан конкретный WorldId/NpcId мира A — WORLD_MISMATCH. Оба клиента считают неожиданный/broadcast sequence ошибкой. Сервер штатно сохраняется.

`Stage 2`: новые server/client процессы; те же player UUID, WorldId/NpcId и разные отношения восстановлены. A удаляет свою дополнительную NPC; B сначала снова получает DENIED для первой NPC, затем test server назначает B operator и admin delete проходит. В B2 дополнительно подтверждены no-argument delete выбранной NPC и BUSY для второго одновременного запроса. Окончательное состояние мира — tombstones, а не новые дубликаты.

Integrated: отдельный `build/p1-check/integrated/saves/p1-world`, скопирован только dev world data без `agnor` и session.lock, то есть новый WorldId и DB. QuickPlay открывает singleplayer save; реальный local player создаёт NPC командой, меняет own relation/select, получает ожидаемые validation errors. После stop/restart проверены прежние WorldId/NpcId/relationship, BUSY и delete выбранной NPC командой без UUID. Это проверка command/API/state потока, не визуальная оценка placeholder.

| Прогон | Серверный marker | Client PASS | Exit codes / server task time |
|---|---|---|---|
| Dedicated A1 | PASS world=A stage=1 | A и B | Все 0 / 1m42s |
| Dedicated A2 restart | PASS world=A stage=2 | A и B | Все 0 / 1m21s |
| Dedicated B1 | PASS world=B stage=1 | A и B | Все 0 / 1m41s |
| Dedicated B2 restart | PASS world=B stage=2 | A и B | Все 0 / 1m28s |
| Integrated I1 | PASS world=I stage=1 integrated=true | A (local player) | 0 / 52s |
| Integrated I2 restart | PASS world=I stage=2 integrated=true | A (local player) | 0 / 52s |

Эти времена — длительность dev Gradle tasks со startup/shutdown, **не performance budgets/PASS**. Другие renderer/IK/model/physics/provider проверки не запускались.

## Evidence и воспроизведение

Локальные ignored артефакты:

- `build/p1-check/{A1,A2,B1,B2}-{Server,A,B}.log`, `I{1,2}-Integrated.log`;
- `build/p1-check/coord/`: server/client PASS markers, WorldId/NpcId, owner/admin milestones;
- `build/p1-check/server/{world-A,world-B}/agnor/state.db`;
- `build/p1-check/integrated/saves/p1-world/agnor/state.db`;
- `build/test-results/test/TEST-*.xml`, `build/libs/agnor-0.1.0-dev.jar`.

Подготовка dedicated server: server-ip=127.0.0.1, server-port=25577, max-players=2, view/simulation distance 2, level-name=world-A (затем world-B), flat generator с bedrock/dirt/grass layers; принятое dev eula.txt. Скрипт оркестрации запускает только свои скрытые child processes, ждёт ready/PASS/exit, ограничивает ожидание и не принимает EULA. Для нового полного прогона архивировать прежний `build/p1-check`: completed markers и tombstones намеренно сохраняются, скрипт не выдаёт старые результаты за новые. Integrated fixture требует отдельный root с dev world в `saves/p1-world`, без AGNOR DB и session.lock, и options с завершённым onboarding.

### Финальная независимая сверка

Проверены все 14 process logs шести сценариев: ожидаемые server/client PASS и BUILD SUCCESSFUL, ни одного fixture FAIL marker. Три DB открыты read-only: integrity_check=ok, foreign_key_check пуст, schema=2, WorldId соответствует coordination record и различается во всех трёх roots. В мирах A/B ровно по две NPC, обе tombstone, по четыре create/delete operation записи, два отдельных relationship records (`21/-7` и `31/-17`). В integrated мире одна deleted NPC, две operation записи, собственное отношение `21`. Owner/player UUID сверены с synthetic connection identities; исходный NpcId совпадает с возвращённым клиенту. Таким образом denied/foreign-world запросы не добавили operation records и не изменили чужое состояние. В общем `mods/agnor` приватных DB нет.

Normal JAR содержит `NpcManagement` и nested SQLite driver, не содержит lifecycle/probe/P1 test harness classes. XML подтверждает 22 tests без failures/errors/skipped. Разрешены 137 локальных ссылок в актуальных документах; `git diff --check` прошёл (только уведомления Git LF/CRLF). Результат с SHA-256 логов/JAR — `build/p1-check/final-checks.json`. После этого менялась только документация; новая сборка ради текста не запускалась.

## Границы и итог

Весь сценарий P1 от команды и trusted server identity до SQL/state/restart и обратного private snapshot пройден. Результат использует явно временный placeholder, без AI/renderer/моделей. Пользовательская поверхность сейчас командная; графический редактор не добавлялся.

Online authentication, raw malicious packet injection, OS-level disk-full/ACL, forced crash в середине физического commit, другие моды/ОС и installed release-JAR запуск **НЕ ПРОВЕРЕНЫ**; ограничения сохраняются для соответствующих последующих задач/P9. Synthetic dev identities не названы проверкой Mojang authentication. Наличие Ollama launcher в workspace указателе сохранено; API/model в P1 не запускались.

**P1.4 и фаза P1 завершены в границах roadmap. Следующая задача — P2.1.**
