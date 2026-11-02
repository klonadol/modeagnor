# ADR-0004: Шесть хранилищ памяти и pipeline гномиков

- **Статус:** Accepted; схемы данных и алгоритмы требуют прототипирования
- **Дата:** 2026-11-02
- **Область:** AI / Memory / Persistence

## Контекст

NPC должен помнить диалоги, факты, действия игроков, собственные выводы и последовательность событий. Пять специализированных гномиков обрабатывают эти данные независимо от основного диалога.

## Решение: хранилища

1. **История диалога:** последние N сообщений, default 10, лимит настраивается; сообщения NPC и игроков; контекст разделяется по NPC и игроку.
2. **Лорбук:** пользовательские записи с активацией Always, keywords, exact text match и semantic search; допускаются роли System/User/Assistant.
3. **Дневник:** долговременные воспоминания, созданные Memorizer и прошедшие проверку Judge.
4. **Память действий:** события мира вне диалога с субъектом, действием, объектом, результатом и временем.
5. **Мысли:** структурированные выводы Thinker — состояние, эмоции, желания, цели и планы.
6. **Сводка:** компактная хронология, обновляемая после каждых 10 сообщений диалога, с учётом предыдущей сводки и новых событий.

Все данные изолируются по миру и NPC; отношения и персональные эпизоды — также по UUID игрока. Ник — только отображаемое имя, не первичный ключ.

## Решение: гномики

- **Memorizer:** получает только историю диалога и хранилище мыслей. Он создаёт кандидатов с источником, типом информации, уверенностью и важностью. Он не читает дневник целиком и не использует собственные скрытые рассуждения.
- **Judge:** получает кандидата и найденные существующие записи дневника. Embedding и текстовое совпадение находят потенциальные дубликаты; Judge решает оставить, объединить, разделить или отклонить записи. Семантический порог не заменяет проверку смысла.
- **Thinker:** целевой интервал 30 секунд плюс важные события. Хранит структурированные выводы, планы, желания и эмоции, а не скрытую chain-of-thought.
- **Bot:** преобразует намерения в безопасные игровые действия и визуальные команды. Действия исполняются только через серверный валидатор и whitelist.
- **Summarizer:** после каждых 10 сообщений обновляет сводку на основе предыдущей версии и новых данных. Нужен watermark, чтобы исключить повторную обработку.

## Межфазные зависимости и совместная активация

Схемы шести хранилищ могут быть подготовлены в Фазе 6, но store считается рабочей функцией только после подключения реального producer/consumer:
- Action Memory: schema/API в Фазе 6; ограниченный Event Feed producer и первые записи для Thinker/Summarizer подключаются в Фазе 7; полный World Observer и фактические outcomes от Bot/Action Executor расширяют поток в Фазе 8.
- Thoughts: schema/API в Фазе 6; writer Thinker и reader Memorizer запускаются совместно в Фазе 7.
- Diary: Judge принимает Memorizer candidates и пишет записи, которые читает Thinker; candidate → Judge → Diary → Thinker является единым acceptance path Фазы 7.
- Daily Summary: store создаётся в Фазе 6; Summarizer получает Conversation History из Фазы 5 и Event Feed Фазы 7; Thinker использует сохранённую Summary в рамках той же интеграции.

Четыре когнитивных гномика (Memorizer, Judge, Thinker, Summarizer), Gnome Scheduler и Event Feed вводятся как одна вертикальная интеграция в Фазе 7. Их можно покрывать unit/contract тестами по отдельности, но ни один сервис не объявляется функционально завершённым без общего end-to-end цикла и проверок повторов, restart, watermark, scope и отказов.

Dialogue events, которые возникли в Фазе 5 до появления gnome workers, доставляются через transactional outbox. Для каждого event ID и каждого consumer ID (Memorizer/Summarizer/Thinker) ведётся отдельный delivery state, claim/lease, attempt, idempotency key и watermark. Один consumer не может подтвердить событие за другого. Gnome Scheduler Фазы 7 регистрирует consumers и replay-ит pending deliveries; streaming partial messages не образуют memory events. Durable History write и соответствующий outbox event фиксируются атомарно. Outbox является механизмом запуска/доставки consumer jobs, а не копией содержания в Action Memory: текст и message records остаются в Conversation History; Action Memory получает только отдельные typed world/action events от Event Feed.

Bot остаётся пятым гномиком. В Фазе 7 фиксируется только его typed plan/action-intent contract; runtime Bot реализуется в Фазе 8 одновременно с Goal Scheduler, полным World Observer, Action Validator/Executor, Navigation и Motion Engine bridge.

Thinker goal proposals в Фазе 7 сохраняются в durable Goal Proposal Inbox/Outbox через общий Storage Facade со scope, evidence, ID, expiry и pending status; запись связана с Thought и не является седьмым хранилищем памяти. Фаза 8 атомарно claims/validates proposal и отмечает его обработанное состояние, чтобы исключить повторное исполнение. До готовности Goal Scheduler Фазы 8 proposals не запускают игровые действия и не изменяют мир.

Gnome Task Scheduler и Goal Scheduler — разные компоненты. Первый управляет фоновыми worker jobs гномиков; второй управляет игровыми целями NPC и появляется в Фазе 8. Между ними не используется общая незащищённая in-memory очередь: handoff выполняется через durable proposal record.

## Каноническая единица message count и consumer trigger policy

Для Conversation History одна durable message record считается одним сообщением:
- accepted inbound player message = 1;
- finalized assistant response = 1;
- streaming partial chunks, system prompt entries, transport retries и uncommitted interrupted partials = 0.

Default Summary threshold — каждые 10 committed message records. Threshold определяется по monotonic message sequence/watermark, не по client timestamp; предыдущая сводка фиксирует точный source range. Повторный replay не увеличивает count второй раз.

Phase 7 Gnome Task Scheduler применяет разные trigger policies:
- Memorizer coalesces contiguous unprocessed finalized History ranges (History + Thoughts only) в scoped task.
- Summarizer учитывает каждый committed History record, но создаёт summary task только при достижении threshold; previous summary и watermark фиксируют диапазон.
- Thinker запускается по `ConversationTurnFinalized` (после финального ответа NPC), значимым world/NPC events и периодическому интервалу около 30 секунд. Частые события coalesce-ятся до одной pending task на NPC/scope; streaming chunk не вызывает Thinker.
- Judge получает только validated candidate records от Memorizer, не raw message outbox events.

Outbox delivery для каждого consumer может стать ENQUEUED/acknowledged только атомарно с durable Gnome Task creation/update либо с durable aggregation/watermark update. Worker execution имеет собственный task status/retry; ack доставки не означает успешного завершения LLM задачи.

## Доступность семантического поиска по фазам

Lorebook и Diary schema могут быть созданы в Фазе 6; Always/exact/keyword retrieval доступен тогда же. Semantic-only retrieval не считается готовым до Embedding adapter Фазы 7. До этого такие записи имеют явное состояние NOT_YET_ENABLED либо применяют явно настроенный fallback; отсутствие embedding не должно выглядеть как успешный пустой semantic result.

## Метаданные воспоминаний

Кандидат должен содержать как минимум: `source`, `information_type`, `confidence`, `importance`, `created_at`, `world_id`, `npc_id` и при необходимости `player_id`.

Информация из мыслей может быть предположением или желанием. Нельзя превращать её в установленный факт без подтверждения. Судья обязан учитывать provenance и противоречия.

## Последствия

- Сбой одного гномика не блокирует основной диалог.
- Все долгие операции выполняются вне Minecraft server thread.
- LLM output валидируется до записи или исполнения.
- Нужны очереди, тайм-ауты, лимиты, retry, дедупликация и наблюдаемость.
- Embedding provider и модель могут меняться независимо от LLM диалога.

## Открытые вопросы

- Формат и миграции SQLite.
- Векторный индекс и fallback при недоступности embedding.
- Точное определение «дня» для сводки.
- Политика хранения мыслей и срока жизни временных наблюдений.
- Разрешение конфликтов между общим фактом мира и персональными воспоминаниями.
- Оптимальный способ группировки сообщений и событий в одну задачу Judge.

Эти детали должны быть утверждены отдельными решениями по мере реализации.