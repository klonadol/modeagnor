# Отчёт 010: P2.3 — реальный диалоговый путь и durable events

- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Статус:** Done P2.3; P2.4 ещё не начата.
- **Ветка / HEAD до:** main / `27e0bdd`; HEAD после — коммит этого отчёта.
- **ADR:** [0015](../decisions/ADR-0015-dialogue-protocol.md), 0013/0014.

## 1–2. Цель и исходное состояние
Подключить принятую P2.1/P2.2 к реальному игроку. До работы Gateway и History/context проверены независимо, UI consumer отсутствовал. Использованы действующие NPC commands/selection/access/runtime IO и world DB; существующий P1 harness не изменён.

## 3–5. Реализация и файлы
`DialogueService`, `DialogueWire/Protocol/Commands`, client `DialogueClient`/AgnorClient, lifecycle Agnor, typed History/context work в NpcRuntime, selected getter в NpcManagement, безопасный model getter Gateway. `/agnor say/retry/history/cancel` и C2S входят в один handler. UI адресный, final только после durable commit, request reconciliation без повторного LLM. Context settings N/budget, safe errors, privacy, cancel/stop hooks, bounded client MessageId dedupe. ADR-0015 содержит точный контракт. `DialogueWireTest`; opt-in `scripts/p2-check.gradle`, `scripts/run-p2-check.ps1`, `src/p2Check` не включены в production JAR.

## 6. Проверки и реальные сценарии
`gradlew.bat build`: PASS (финальный 10s), 36 ordinary tests PASS, 2 explicit-live skipped. Wire test проверяет UTF-8 roundtrip/size/trailing/truncation/enum/text bounds и отсутствие text в failure DTO/toString.

`scripts/run-p2-check.ps1 -WorldTag D -Stage 1`: два реальных клиента P2A/P2B на loopback dedicated 127.0.0.1:25579. A создаёт NPC реальной командой, B выбирает её. Каждый отправляет `/agnor say` со своим synthetic private code. Production server service/context/Gateway/Ollama выполняют inference, History+event commit и addressed S2C. Client harness передаёт каждый packet production DialogueClient, который реально вызывает chat UI. Чужой code на любом клиенте вызывает FAIL. Оба получили свои финалы. RETRY вернул тот же MessageId/sequence, production presentation не показала второй final. History C2S хронологична; version/world guards проверены.

`-WorldTag D -Stage 2`: новые server/client процессы, прежние WorldId/NpcId/player UUID. Оба увидели собственные 2 records и затем спросили сохранённый code. Реальные ответы `AlderAzure` и `BirchBronze`, final sequence=4. Сервер проверил 4 messages и 4 events на каждый private scope. Minecraft tasks всех трёх процессов напечатали BUILD SUCCESSFUL; gameplay server/client PASS markers записаны. **Ограничение оркестрации D2:** один Gradle wrapper не вышел после завершения клиента. Thread dump показал daemon ClientInputForwarder/PipedInputStream.receivedLast deadlock, после `The daemon has finished executing the build`. Завершён только этот test daemon 30564; wrapper получил exit 1 и orchestrator честно вернул ошибку. Этот exit не назван PASS. Игровые/DB assertions завершились до зависания и проверены отдельно. Для следующих запусков stdin JavaExec заменён пустым stream, wrapper получает EOF.

`-WorldTag I -Stage 1`, затем `-Stage 2`: real integrated server/local player на отдельном dev save. Командный диалог/retry/history/guards → stop/restart → восстановленная история → новый реальный ответ с прежним code. Оба orchestration exits=0, client/server markers PASS. Никакие пользовательские saves не менялись; fixture создан из прежнего dev world без agnor DB/session.lock. Использована ранее принятая dev EULA, новая автоматически не принималась.

Независимая SQLite сверка после всех прогонов: dedicated scopes `(4 messages,4 events)` ×2; integrated `(4,4)` ×1; schema=3, integrity_check=ok, foreign_key_check без ошибок. Это два разных save roots/WorldId. Игровая матрица P1 не повторялась.

## 7. Дефекты
При review исправлены shutdown disconnect после удаления service map, отсутствие terminal feedback для connected stale recipient, возможность просмотра History/final reconciliation без включённого provider, safe terminal-persistence diagnostics. Happy path integrated проверял последнюю версию этих guards. Исправлен dev launcher stdin deadlock по thread dump; его failure сохранён выше, не заменён фиктивным exit 0.

## 8. НЕ ПРОВЕРЕНО
Управляемые provider timeout/network fail, disconnect во время inference, stale-range case и реальный cloud ещё впереди (P2.4). Длительная нагрузка/online authentication/публичный release install не заявляются. Обычный локальный Minecraft chat log содержит собственный видимый текст, поэтому не является безопасным bundle для публикации. Полный приватный context/Gateway credentials в серверных logs/DTO отсутствуют.

## 9. Итог
P2.3 функционально и runtime подтверждена; можно переходить к P2.4. D2 Gradle wrapper limitation явно отделена от завершившихся игровых assertions, stdin исправлен для дальнейших запусков. P2 не объявлена завершённой, P3 не начата.

## 10. Evidence
`build/p2-check/{D1,D2}-{Server,A,B}.log`, `I{1,2}-Integrated.log`, `coord/*SERVER-PASS`, клиентские done markers; isolated state.db и `build/test-results/test/`. Данные synthetic, ключи не использовались.
