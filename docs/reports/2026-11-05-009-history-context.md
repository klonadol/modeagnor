# Отчёт 009: P2.2 — History и единый Context Builder

- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Статус:** Done P2.2 в границах Roadmap.
- **Ветка / HEAD до:** main / `05a8011`; HEAD после — коммит отчёта.
- **ADR:** [0014](../decisions/ADR-0014-conversation-history-context.md), 0010/0011/0013.

## 1–2. Цель и исходное состояние
После принятой P2.1 реализовать scoped durable History и единственный Context Builder. До работы History отсутствовала, WorldStorage schema v2 обслуживала NPC. Dirty changes отсутствовали. UI ещё не consumer; игровой путь по Roadmap относится к P2.3.

## 3. Реализация
Typed ConversationScope/HistoryMessage/HistoryRepository; schema v3 и pre-migration backup. Request dedupe, payload-conflict/busy checks, single claim/finalization, terminal/restart policy, monotonic sequence, recent N default 10, protected event references, backpressure с резервом final. Минимальный event record создан с writer как необходимая атомарная зависимость; consumer/delivery tables отсутствуют.

Единый pure ContextBuilder собирает built-in definition/system policy, runtime pose/revision, own relationship и recent History с сохранением ролей и scope. Required system/current не вытесняются, current не дублируется; context budget и future-source statuses явны. HTTP только через существующий Gateway.

## 4. Изменённые файлы
`dialogue/{ConversationScope,HistoryMessage,ContextBuilder}.java`, `storage/{HistorySchema,HistoryRepository,WorldStorage}.java`; `HistoryRepositoryTest`, `ContextBuilderTest`, opt-in `HistoryContextLiveCheck`; build.gradle historyLive; ADR/status docs.

## 5. Решения
ADR-0014: один world DB, atomic message+event, protected retention, canonical counts, restart без inference retry, conservative byte budget. Границы NPC architecture не переписаны.

## 6. Проверки
`gradlew.bat test historyLive --console=plain`: BUILD SUCCESSFUL (13s). 35 ordinary tests PASS, 2 live opt-in skipped. Отдельный historyLive: PASS, два реальных Ollama turns, между ними WorldStorage close/reopen. Второй context включил сохранившуюся историю, модель ответила `Cedar47`; четыре History records сохранены.

Новые controlled checks: accepted/final sequences/counts; request/final replay и mismatch; player/world isolation; atomic rollback inbound/final при failure event insert; unresolved job restart → INTERRUPTED без второго claim; v2 migration success и deliberate schema collision rollback с сохранением NPC/WorldId/sentinel/pre-migration backup; roles/single-current/budget/future source state/foreign scope rejection.

## 7. Дефекты
Контрактные и live проверки прошли с первого прогона. При review capacity admission уточнён: учтён резерв final для всех in-flight jobs. На этом срезе нет скрытого удаления sources по N.

## 8. НЕ ПРОВЕРЕНО
Живой Minecraft UI/два игрока/доставка и cancel от disconnect ещё не подключены; cloud относится к P2.4. Real test использует production storage/context/gateway, но synthetic runtime snapshot, а не игровую entity; это не выдаётся за gameplay scenario.

## 9. Итог
P2.2 завершена; переход к P2.3 разрешён. Следующий шаг — server ingress, command/UI path, addressed reply/reconciliation и реальные integrated/dedicated scenarios. P3 не начата.

## 10. Evidence
`build/p2-evidence/history-context-live.txt`, `build/test-results/historyLive/`, `build/test-results/test/`; все данные synthetic, credentials не использовались.
