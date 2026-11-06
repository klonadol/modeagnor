# ADR-0014: scoped History, единый Context Builder и atomic event record

- **Статус:** Accepted / P2.2.
- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Заменяет:** нет; расширяет ADR-0010/0011/0013 в границах P2.
- **Область:** Persistence / Privacy / Dialogue / Context.

## Контекст и решение
History должна существовать до диалогового UI, но первая запись уже обязана быть атомарна с durable event. Поэтому минимальная таблица `dialogue_event` введена в P2.2 вместе с writer; пользовательский ingress/delivery остаётся в P2.3. Это минимальная критическая зависимость из Blueprint §0.8/5.2/7.2, а не изменение порядка Roadmap. Scheduler/consumer queues/cursors/leases не создаются.

WorldStorage schema v3 добавляет conversation, dialogue_turn, history_message, dialogue_event. Существующие NPC/WorldId/relationships сохраняются. Перед migration v2 создаётся SQLite snapshot, DDL и user_version меняются одной транзакцией. Историческое имя v1-before-v2 backup сохранено для существующего recovery workflow. Restore поддерживает только текущую версию и тот же WorldId; downgrade выполняется offline прежней версией из pre-migration snapshot.

Одна conversation на (world,NPC,player); UUID детерминирован от трёх trusted UUID, не от никнейма. DB принадлежит world; каждый вызов typed HistoryRepository проверяет WorldId, conversation/npc/player и FK. Sender/player берётся серверным consumer из connection; History не авторизует Minecraft-дистанцию, это задача ingress. Message UUID, request UUID, scoped монотонная sequence, created_at, role, model metadata и schema_version сохраняются. `toString` не содержит text. Событие имеет собственные ID/global event_sequence и ссылку на message/scoped sequence, без копии текста.

Accepted inbound = 1 committed message, final assistant = 1. Request replay возвращает прежний turn; изменение текста при том же request ID запрещено. Один ACCEPTED/RUNNING на conversation. Claim разрешён только один раз, finalize — только для RUNNING; повтор того же final идемпотентен, другой final отклоняется. Failure/cancel/restart не удаляют accepted inbound. На старте новой session unresolved jobs помечаются INTERRUPTED, без слепого inference retry. Finalization требует живой NPC, поэтому tombstone запрещает stale reply commit.

Вставка message, event, увеличение sequence и смена final state атомарны в существующем Storage Facade. Recent N — только окно чтения (default 10, 1..100), записи и events не удаляются. До будущей retention/consumer activation все источники защищены. World policy cap 100000 records учитывает резерв одного final для каждого admitted job; переполнение даёт backpressure, не silent truncation.

ContextBuilder — один pure engine без DB/HTTP/Minecraft calls. Получает typed scope/current accepted message, текущие definition/runtime/relationship и scoped chronological recent History. Обязательная server system policy и current input включаются ровно один раз. Character definition пока built-in; внешние untrusted pack descriptions появятся в P4 с отдельной boundary policy. История сохраняет user/assistant роли и не повышается до system. Oldest whole messages исключаются из prompt при лимите, но остаются в DB. Snapshot фиксирует runtime/definition revisions и message IDs. Активные definition/runtime/relationship/history имеют AVAILABLE_WITH_DATA; lorebook/diary/action_memory/thoughts/summary явно NOT_YET_ENABLED.

Policy default: recentN=10, conservative input budget=12000 UTF-8 bytes + 32 framing bytes/message; допустим 6000..16000. Это консервативная верхняя оценка для byte-token text transport, не точный provider tokenizer и не измерение performance. Gateway ограничивает output отдельно. Required policy/current не вытесняются, слишком маленький бюджет отвергается. Чужие scope или future sequence отвергаются. Текущий UI ещё не потребляет этот контракт до P2.3.

## Альтернативы и последствия
Отдельная JSON History нарушила бы единый Storage Facade; запись event после commit допускала бы потерю handoff при crash. Оба пути отвергнуты. Цена — schema migration и protected retained rows; до P5 нет автоматической очистки и History editor. Atomic DB commit не обещает exactly-once network display; P2.3 реализует request reconciliation и message ID dedupe.

## Проверка
[Отчёт 009](../reports/2026-11-05-009-history-context.md): unit contract/transaction/migration/scope tests и real History → Context → Gateway → Ollama после reopen. Полный игровой путь и два живых игрока — следующий P2.3.
