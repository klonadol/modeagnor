# Отчёт 011: P2.4 и завершение фазы P2

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10).
- **Статус:** Done P2.4 / Verified P2 в границах действующего Roadmap.
- **Ветка / HEAD до P2.4:** main / `58e00e5`; HEAD после — коммит этого отчёта.
- **ADR:** [0016](../decisions/ADR-0016-cloud-mode-failure-policy.md), 0013–0015.
- **Предыдущие этапы:** [008 P2.1](2026-11-05-008-provider-gateway-verified.md), [009 P2.2](2026-11-05-009-history-context.md), [010 P2.3](2026-11-05-010-dialogue-path.md).

## 1–2. Цель и исходное состояние
Последовательно довести P2.1–P2.4 до работающего local/cloud dialogue без перехода к P3. P2.1 первоначально была заблокирована внешней Ollama; после отдельного восстановления среды сначала проверен production Gateway (008), затем History/context (009), затем gameplay path (010). К P2.4 перешли после этих проверок. Рабочие изменения сохранялись отдельными локальными commits; чужие изменения не удалялись/reset не выполнялся, новых веток/PR/публикации нет.

## 3. Реализация P2.4
Добавлен OpenRouter adapter в существующий Gateway и backward-compatible optional adapter/host-file secret references. Capabilities проверяются metadata-only до private prompt; requests bounded, non-streaming, с отключённым reasoning. Нейтральный final result и safe errors общие с Ollama. Named credential читается только server-side из исходного внешнего файла, без копирования или env передачи в Gradle. Успешный payload с echo credential отвергается. Исходники будущих AI фаз не создавались.

## 4. Изменённые исходники
`provider/{DialoguePreset,ProviderGateway,HostSecrets}.java`, `OpenRouterGatewayTest`, opt-in `CloudLiveCheck` и cloudLive Gradle task. Dev-only `P2MockProvider`, дополнения P2ServerCheck/P2ClientCheck и scripts/run-p2-check.ps1 для controlled failures и cloud gameplay. Status/docs/config guide синхронизированы. Production protocol/storage/context предыдущих шагов не переписаны.

## 5. Решения
ADR-0016 дополняет 0013: Ollama/OpenRouter modes; explicit external secret reference; streaming не заявлен. До P6 сохраняется protected History/event range, не создаются consumer cursors/jobs. Byte budget/row caps являются policy bounds, а не performance targets.

## 6. Реальные providers и сценарии

### Local (008–010)
Ollama 0.40.14, `ternary-bonsai-text-20k:latest`, единственный исходный Ternary-Bonsai-2-9B-Q2_0.gguf, без projector. Production Gateway → реальный final ответ с think:false. History/context test после reopen вспомнил `Cedar47`. Dedicated D1/D2: два игрока говорят с одной NPC, видят только свои codes, retry не добавляет записи/финалы, после restart LLM вспоминает каждому отдельный code. Integrated I1/I2 повторяют пользовательский путь с local player. Подробные ограничения D2 wrapper — отчёт 010.

### Cloud
Найден явно предназначенный файл `C:/Users/User/Desktop/silly/API (1).txt`; исследованы только безопасные признаки структуры. Выбрана единственная подписанная секция OpenRouter, другие credentials не использовались для запросов. Проверена публичная metadata модели `google/gemini-2.5-flash-lite` (text output, доступные endpoints). Один opt-in `cloudLive` запрос через production Gateway: **PASS**, error=NONE, elapsedMillis=2277, корректный final ответ на synthetic prompt. Evidence сохраняет только provider/model/error/latency/nonempty, без ключа или full context.

Затем `scripts/run-p2-check.ps1 -WorldTag I -Stage 4`: настоящий integrated Minecraft player → `/agnor say` → trusted handler → прежняя scoped History/ContextBuilder → реальный OpenRouter → finalized record/event → private production chat UI. Модель вспомнила собственный code из сохранённого local dialogue. После cloud turn — 6 messages и 6 events, final metadata различает локальную и облачную модели. Client/server assertions PASS, orchestration exit 0. Всего два коротких cloud inference (64/96 output-token caps), без массовых/необязательных запросов и автоматических retries.

### Отказы в настоящем Minecraft
`-WorldTag D -Stage 3`: один настоящий dedicated client, controlled loopback mock только для негативных случаев. Это **не** real-model evidence.

- Timeout 2500ms при mock delay 6000ms: FAILED/TIMEOUT; два ProbeProtocol snapshots подтвердили advance server clock **40 ticks** во время ожидания. Timeout fixture получил ровно один chat request, автоматического повтора нет.
- Teleport игрока за пределы permitted range во время inference: нормальный поздний текст отвергнут как CANCELLED/STALE, без assistant commit.
- `/agnor cancel` после ACCEPTED: CANCELLED, без финального сообщения.
- Разрыв настоящего play connection после ACCEPTED: production disconnect отменяет job; сервер подтверждает CANCELLED и отсутствие late final.
- Во всех четырёх случаях accepted inbound+event сохранены. Последний прогон завершился client/server PASS и orchestration exit 0. Новые messages/events ровно +4, assistant records не добавились.

## 7. Ошибки, диагностика и исправления стенда
Все неудачные прогоны сохранены, не названы PASS:

1. Отчёт 010: D2 gameplay и DB checks PASS, но один Gradle wrapper завис после BUILD SUCCESSFUL. Thread dump — ClientInputForwarder/PipedInputStream.receivedLast. Завершён только уже закончивший build test daemon, wrapper exit 1 честно зафиксирован. Dev JavaExec получил пустой stdin, wrapper — EOF. Последующие D3/I4 завершились с exit 0; ошибка wrapper не маскируется игровыми markers.
2. Первый D3 упал до join: Main dev entrypoint запустил mock и в dedicated, и в client, второй получил BindException. Перенесён запуск mock в SERVER_STARTING. Original logs/crash сохранены в `archive/bootstrap-D3` и client crash-reports.
3. Второй D3 подтвердил timeout (40 ticks) и stale, затем получил корректный DENIED: fixture возвратил игрока на Y=80 при фактической NPC на Y=-60. Исправлен только fixture: начало у сохранённой NPC, точный возврат в прежние координаты. Production access policy не ослаблялась. Logs/markers — `archive/reset-height-D3`.
4. Последний D3 использовал накопленные записи предыдущей неудачной попытки; сообщения не удалялись ради PASS. Проверяется исходное число +4. Дубликаты/поздние финалы отсутствуют.

## 8. Финальные проверки
- `gradlew.bat test cloudLive --console=plain`: PASS, настоящий cloud Gateway.
- `gradlew.bat test --console=plain`: **38 ordinary tests PASS, 3 opt-in live skipped**. Последние live результаты подтверждены отдельными tasks/runtime logs, skip не считается PASS.
- `gradlew.bat build --console=plain`: PASS, финальная сборка 8s; JDK 21, wrapper 8.8, repo-local Gradle home.
- Production JAR: dev harness/test classes отсутствуют; common sources не импортируют client API.
- SQLite после всех прогонов: dedicated scopes `(4,4)` и `(10,10)` messages/events (включая сохранённые failure records); integrated `(6,6)`. integrity_check=ok, FK errors=0. Final models — local Ollama и OpenRouter в integrated DB; negative turns только terminal inbound.
- Скан 175 source/evidence файлов по **фактическому выбранному credential**, без вывода значения: **0 leaked files**. Ключ не попадал в command-line/env/project config/DTO/отчёт; secret source read-only.
- `git diff --check`: PASS. Исторические reports не переписаны как успешные; рабочие статусы обновлены отдельно.

## 9. Ограничения / НЕ ПРОВЕРЕНО
Streaming намеренно не реализован/не заявлен. Не проверены все модели/провайдеры, online identity authentication, длительный soak/масштаб, installed public release-JAR matrix, физический disk-full/ACL и forced process death в момент commit. Все synthetic runtime проверки выполнялись в isolated dev saves; offline identities только на loopback. Обычный Minecraft client chat log содержит собственный видимый диалог; он не включается в безопасный server diagnostic bundle. JVM не обещает физическое обнуление immutable secret strings. Эти ограничения не подменяют gates последующих P3/P9.

## 10. Итог и следующий шаг
**P2.1–P2.4 завершены в границах Roadmap.** Подтверждён полный путь player command → trusted request → единый ContextBuilder → реальный provider → final History/event commit → private UI, локальный и облачный режим, privacy двух игроков, restart, retry/dedupe и отказы. P1 regression tests проходят. Работа останавливается **перед P3**; renderer/motion/gnomes/TTS не начаты.

Evidence: `build/p2-evidence/`, `build/p2-check/{D1,D2,D3,I1,I2,I4}-*.log`, coord markers, archive failed attempts, isolated DBs, `build/test-results/{test,providerLive,historyLive,cloudLive}/`, `build/libs/agnor-0.1.0-dev.jar`. Последующие expensive game/provider runs не нужны без новых изменений или конкретной причины.
