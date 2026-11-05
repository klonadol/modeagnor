# ADR-0004: Шесть хранилищ памяти и pipeline гномиков

> Фазовые предписания пересмотрены 2026-11-05 в [ADR-0009](ADR-0009-dependency-driven-plan.md); функциональное решение сохраняется.

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

## Зависимости и поэтапная активация (пересмотр ADR-0009)

Каждый store создаётся с writer: History — Dialogue P2; Lorebook — пользователь/pack P5; Action Memory — Event Feed P6.1; Thoughts — Thinker P6.2; Diary — Judge P6.3; Summary — Summarizer P6.4. Общий Storage Facade вводится с NPC P1, но не создаёт заранее AI queues и пустые repositories.

Scheduler + Thinker/Thoughts, Memorizer + Judge/Diary, Summarizer/Summary принимаются последовательными срезами. До активации источника ContextBuilder показывает NOT_YET_ENABLED; после активации допустим AVAILABLE_EMPTY. Итог P6 требует полного feedback-цикла и restart/scope/failure tests; он не запрещает завершать отдельные срезы раньше.

History commit атомарен с outbox event. До workers хранится protected replay range; per-consumer cursor/delivery/lease создаётся при активации, ack атомарен с durable task/aggregation. Один consumer не подтверждает другого. Recent-N не удаляет непрочитанный source; privacy deletion явно завершает ссылку как SOURCE_DELETED. Текст не копируется в Action Memory. Детали — Blueprint §0.8.

Thinker пишет validated proposals атомарно с Thought в Goal Proposal Inbox: ID/scope/evidence/expiry/PENDING. Это не седьмое хранилище памяти. До Goal Scheduler P7 proposals не меняют мир. Gnome Scheduler управляет background inference, Goal Scheduler — игровыми целями. P7 сначала проверяет ручную цель/validator/executor/navigation, затем подключает Bot и Thinker; в конце обязателен полный автономный цикл.

## Каноническая единица message count и consumer trigger policy

Для Conversation History одна durable message record считается одним сообщением:
- accepted inbound player message = 1;
- finalized assistant response = 1;
- streaming partial chunks, system prompt entries, transport retries и uncommitted interrupted partials = 0.

Default Summary threshold — каждые 10 committed message records. Threshold определяется по monotonic message sequence/watermark, не по client timestamp; предыдущая сводка фиксирует точный source range. Повторный replay не увеличивает count второй раз.

P6 Gnome Task Scheduler применяет разные trigger policies:
- Memorizer coalesces contiguous unprocessed finalized History ranges (History + Thoughts only) в scoped task.
- Summarizer учитывает каждый committed History record, но создаёт summary task только при достижении threshold; previous summary и watermark фиксируют диапазон.
- Thinker запускается по `ConversationTurnFinalized` (после финального ответа NPC), значимым world/NPC events и периодическому интервалу около 30 секунд. Частые события coalesce-ятся до одной pending task на NPC/scope; streaming chunk не вызывает Thinker.
- Judge получает только validated candidate records от Memorizer, не raw message outbox events.

Outbox delivery для каждого consumer может стать ENQUEUED/acknowledged только атомарно с durable Gnome Task creation/update либо с durable aggregation/watermark update. Worker execution имеет собственный task status/retry; ack доставки не означает успешного завершения LLM задачи.

## Доступность семантического поиска по фазам

Lorebook создаётся в P5, Diary — с Judge в P6.3; Always/exact/keyword Lorebook retrieval доступен с P5, keyword Diary retrieval — с его writer. Semantic-only retrieval не считается готовым до Embedding adapter P6. До этого такие записи имеют явное состояние NOT_YET_ENABLED либо применяют явно настроенный fallback; отсутствие embedding не должно выглядеть как успешный пустой semantic result.

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

- Backend (SQLite/JSON) и минимальные миграции — P1.1/P1.2; последующие schema extensions — с writers.
- Векторный индекс и fallback при недоступности embedding.
- Точное определение «дня» для сводки.
- Политика хранения мыслей и срока жизни временных наблюдений.
- Разрешение конфликтов между общим фактом мира и персональными воспоминаниями.
- Оптимальный способ группировки сообщений и событий в одну задачу Judge.

Эти детали должны быть утверждены отдельными решениями по мере реализации.
