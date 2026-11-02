# AGNOR Implementation Blueprint
## Полная спецификация устройства, взаимодействия и отказоустойчивости

- **Статус:** Living technical specification; проектирование, не реализованный код.
- **Версия:** 0.1
- **Платформа:** Minecraft 1.21.1, Fabric, Java 21.
- **Связанные документы:** ARCHITECTURE.md, ROADMAP.md, DECISIONS.md, ADR-0001…ADR-0008.
- **Назначение:** определить внутреннее устройство подсистем, входы/выходы, владение данными, вызовы, жизненный цикл, отказные сценарии и критерии проверки до начала реализации.

> Документ описывает целевое устройство, а не существующий код. Open означает реальный незакрытый вопрос. Нельзя выдавать предположение за принятое решение или реализацию.

---

## 0. Метод и критерии полноты

Это не построчный псевдокод и не список имён переменных. Спецификация описывает исполняемую логику на уровне компонентов и контрактов.

Для каждой функции обязательны:
1. Назначение и границы ответственности: что делает и что ей запрещено.
2. Владелец: server, client или shared; кто создаёт и останавливает.
3. Триггеры: событие, расписание, пользовательская команда или внутренний вызов.
4. Входы: точные источники, schema/version, scope, доверие, свежесть и лимиты.
5. Предусловия: права, состояние мира, доступность зависимостей.
6. Алгоритм: этапы, ветвления, приоритеты, отмена и точки фиксации.
7. Выходы: результат, события и побочные эффекты.
8. Зависимости: интерфейсы и downstream consumers; запрещённые циклы.
9. Ошибки: классификация, retry, fallback, recovery.
10. Конкурентность: очередь, deduplication key, serialization и stale-result policy.
11. Persistence: что и когда сохраняется, транзакции и миграции.
12. Наблюдаемость: correlation ID, метрики и безопасные логи.
13. Проверки: happy path, негативные, конкурентные, restart, multiplayer и end-to-end.

Статусы: Agreed, Designed, Open, Validated, Implemented, Verified. Последние два применяются только после реального изменения кода и проверок. Числовые лимиты, тайм-ауты, очереди, tick budgets и retention задаются конфигурацией и измеряются; до профилирования это параметры, не выдуманные константы.

## 1. Системная модель и инварианты

### 1.1 Runtime стороны

**Server authority** — integrated server в одиночной игре и dedicated server в мультиплеере. Только сервер владеет NPC runtime state, AI tasks, целями, памятью, отношениями, вызовами LLM/embedding/TTS, валидацией действий, навигацией и авторитетной позой.

**Client presentation** отвечает за UI, рендеринг, локальную интерполяцию полученных snapshots, воспроизведение аудио и отправку запросов. Клиент не является источником истины для памяти, решений, физики или изменения мира.

**Shared contracts** содержат versioned DTO, схемы, идентификаторы и типизированные команды. Shared не означает, что клиенту разрешено видеть серверные данные.

### 1.2 Запрещённые зависимости

- Client не изменяет серверное состояние напрямую.
- LLM output не вызывает Minecraft API и не исполняется как код.
- HTTP/network callback не изменяет мир вне server thread.
- Renderer не вызывает память, Provider Gateway или AI decisions.
- Storage не вызывает LLM и не знает UI.
- Content Pack не содержит секреты или произвольный исполняемый код.
- Гномик не читает хранилища вне собственного контракта.
- Client prediction не становится источником authoritative state.

### 1.3 Идентификаторы и области данных

Ключи: World UUID, NPC UUID, Player UUID, Conversation ID, Event ID, Task ID, Correlation ID, Content Pack ID + version, Provider Profile ID. Ник игрока — только display field. Каждое чтение/запись памяти явно указывает scope. Отсутствие player scope не должно автоматически расширять доступ.

### 1.4 Обязательные инварианты

1. Сервер — единственный источник истины.
2. Внешний вызов никогда не блокирует Minecraft server thread.
3. Непроверенный ответ модели не исполняется и не записывается как факт.
4. Каждая фоновая операция имеет deduplication или доказанную безопасность повтора.
5. Любая запись памяти содержит provenance, scope и timestamp.
6. Private data одного игрока не попадает в контекст другого без явного разрешённого правила.
7. Секреты не входят в DTO, client logs, Content Packs и публичные artifacts.
8. Сетевой пакет не доказывает полномочия отправителя.
9. Отказ AI/API/renderer не должен останавливать Minecraft server.
10. Устаревший async result не перезаписывает новое состояние.
11. Неизвестная версия данных приводит к контролируемому отказу, не к частичному повреждению.
12. Клиентское визуальное состояние может отставать, но не меняет серверную реальность.

## 2. Карта подсистем и владение ответственностью

| Подсистема | Runtime | Владеет | Не владеет |
|---|---|---|---|
| Mod Lifecycle / Core | Server + client bootstrap | Регистрация, startup, shutdown, wiring | AI decisions |
| Character Registry / Runtime | Server | UUID, definition, runtime state, NPC lifecycle | Renderer |
| World Event Observer | Server | Нормализация игровых событий | Долговременная память |
| Conversation Orchestrator | Server | Диалоговый workflow | HTTP transport |
| Context Builder | Server | Сбор разрешённого контекста | Хранение данных |
| Provider Gateway | Server | Адаптеры, capabilities, secrets | Смысловые решения |
| Memory Services | Server | Шесть хранилищ, транзакции | Поведение NPC |
| Gnome Scheduler | Server | Очереди, приоритеты, дедупликация | Смысловой анализ |
| Five Gnomes | Server workers | Специализированные преобразования | Прямое изменение мира |
| Goal / Action Runtime | Server | Планирование и whitelist actions | Произвольный код модели |
| Navigation Adapter | Server | Путь и фактическое перемещение | Диалог |
| Agnor Motion Engine | Server authority + client presentation | Semantic tasks, pose pipeline | API и память |
| Asset Pipeline / Content Packs | Loader + client assets | Импорт, capability profiles | Private config |
| Renderer / Audio / UI | Client | Отображение и ввод | Авторитетное состояние |
| Network Protocol | Shared contracts + handlers | Версии DTO, проверки, синхронизация | Бизнес-решения |
| Persistence / Migration | Server | Надёжное хранение | AI-контекст |

Физические Gradle-модули могут быть объединены, но логические интерфейсы и запреты зависимостей действуют с самого начала.

## 3. Жизненный цикл мода и персонажа

### 3.1 Startup / shutdown

1. Fabric entrypoints регистрируют common/server/client части; client classes не загружаются на dedicated server.
2. Core валидирует конфигурацию, schema и безопасные defaults.
3. Server lifecycle открывает persistence, проверяет schema version, выполняет миграции транзакционно и проверяет целостность.
4. Content Pack manifests проходят проверку ID, version, dependencies, paths, size, checksum, formats и conflicts.
5. Provider adapters создаются лениво; secrets читаются только из server-side secret storage.
6. Scheduler и bounded queues создаются до загрузки мира, но не исполняют задачи до готовности world scope.
7. При загрузке мира восстанавливаются NPC и только те pending tasks, для которых определена безопасная recovery policy.
8. При остановке запрещается intake новых задач, выполняется cancellation/checkpoint, завершаются транзакции, flush persistence и закрываются executors.

Ошибка optional subsystem (например, TTS) переводит только её в disabled/degraded state. Повреждённое обязательное хранилище нельзя молча заменить пустой базой.

### 3.2 Создание NPC

Запрос UI/API → server identity и permission validation → world/limit/definition validation → Registry проверяет уникальность UUID → Runtime Factory создаёт независимые state containers, relationship namespace, memory scope и scheduler scope → persistence сохраняет начальное состояние атомарно → сервер публикует разрешённый spawn snapshot → клиенты загружают локальный asset либо placeholder → NPC становится Active после готовности обязательных серверных компонентов.

Client renderer readiness не является условием server activation. Повтор с тем же idempotency key не создаёт второго NPC.

### 3.3 Удаление, выгрузка, восстановление

Удаление entity не равно удалению памяти. Chunk/entity unload сохраняет долговременное состояние. При unload отменяются ephemeral tasks; durable tasks завершаются или сохраняют checkpoint. При reload восстанавливаются state и версии контента; просроченные задачи переоцениваются, а не исполняются вслепую. Поздний callback после world unload обязан проверить generation token и не писать в закрытый scope.

## 4. Character Runtime, state и Goal Scheduler

Character Definition immutable: личность, правила, базовые параметры, ссылки на ресурсы. Runtime State mutable: режим, эмоции, потребности, активность и текущая цель. Relationship State хранится отдельно для каждой пары NPC ↔ Player UUID. Presentation Intent содержит expression, gaze, gesture и desired motion. Task State содержит текущие цели и операции. Memory References — ссылки на сервисы, не копия всех хранилищ.

Событие поступает через Observer или валидированную команду. Runtime проверяет применимость, обновляет bounded gameplay state, создаёт durable event при необходимости, публикует сигнал Scheduler/Goal Scheduler и обновляет presentation intent только контролируемыми командами. Не каждое событие мира вызывает LLM: шумные события фильтруются, coalesce-ятся; значимые события сохраняются.

Goal содержит тип, источник, приоритет, expiry, зависимости, preconditions, cancellation policy и completion predicate. Scheduler выбирает цель с учётом safety constraints, user commands, стоимости прерывания, автономных целей и доступных capabilities. LLM может предложить цель, но не назначить ей привилегии. Неудачная цель не должна повторяться бесконечно.

## 5. Полный диалоговый pipeline

### 5.1 Вход

Игрок отправляет текст/голос через UI. Клиент передаёт payload и адресата/сессию; сервер самостоятельно определяет sender UUID, permissions, дистанцию, состояние NPC и лимиты.

### 5.2 Оркестрация

1. Validate sender, NPC, world, permission, payload size, rate limit, session и input type.
2. Проверить request/message ID на replay; повтор не создаёт новый ответ.
3. Сохранить inbound message как pending/accepted с уникальным ID.
4. Context Builder собирает разрешённый контекст.
5. Orchestrator выбирает Main Dialogue preset; настройки гномиков независимы.
6. Provider Gateway выполняет запрос вне server thread с deadline, cancellation и token/size bounds.
7. Output Validator проверяет schema, размер, safety policy и отделяет player-facing text от action/emotion proposals.
8. Response commit сохраняет ответ, request ID и provider/model metadata без secrets.
9. Только после commit публикуются события для Memorizer, Summarizer, Thinker/Goal Scheduler и Bot при наличии соответствующего intent.
10. Сервер доставляет текст только разрешённому игроку; другим клиентам доступно только публичное presentation/game state.
11. При проблеме доставки запись остаётся доступной для reconciliation; повторная доставка не вызывает новый inference.

Ответ модели не является одновременно фактом, командой мира и воспоминанием. Эти типы проходят разные валидаторы.

### 5.3 Streaming и timeout

Streaming chunks — временное состояние UI; они не запускают Memorizer/Summarizer. Durable message появляется только после финализации. Interrupted partial response маркируется как partial и не считается полным ответом.

Повтор inference не является безопасным сетевым retry: первый запрос мог завершиться и породить другой текст/расход. Сохраняется стадия операции. При uncertain external result выполняется reconciliation, а не слепой второй prompt.

## 6. Context Builder и приватность

Context Builder — единственная точка сборки контекста; он не хранит данные и не вызывает LLM.

Источники:
1. Character Definition и system constraints.
2. Server policy и настройки активного диалога.
3. Lorebook entries после scope/activation filters.
4. Conversation History конкретного NPC + player/conversation scope.
5. Relationship State текущего Player UUID.
6. Diary retrieval из разрешённой области.
7. Action Memory, относящаяся к данному NPC/player/world.
8. Structured Thoughts; hypotheses явно помечены.
9. Daily Summary того же scope/period.
10. Ограниченный актуальный world context, если разрешён.

Prompt assembler формирует стабильные секции и token budgets. System constraints и текущее сообщение не вытесняются обычными воспоминаниями. Дублирующие references могут схлопываться, но записи не удаляются. Конфликты сохраняют provenance; embedding similarity не является доказательством истины. Snapshot фиксирует версии прочитанных данных.

User messages, lorebook entries, external descriptions и retrieved memories считаются недоверенными данными, а не system instructions. Никакая инструкция из памяти не отменяет permissions, system policy или action whitelist.

## 7. Шесть хранилищ и persistence

### 7.1 Общий контракт

Storage API обеспечивает typed operations, scope enforcement, schema validation, transactions, pagination, retention и migrations. Бизнес-сервисы не строят SQL/JSON напрямую. Durable записи имеют ID, scope, timestamps, provenance, schema version и status. SQLite/JSON и vector index остаются предварительными вариантами до прототипа.

### 7.2 Conversation History

Inbound message записывается pending до inference; response записывается finalized/interrupted отдельно. Read path выбирает последние N сообщений по NPC + player/conversation scope и возвращает их в хронологическом порядке. Default N=10. Open: определить, считается ли сообщением отдельная реплика или пара user/assistant; правило должно быть единым в UI и Summarizer. История не равна Diary.

### 7.3 Lorebook

Entry: stable ID, scope, title/content, activation mode, triggers, priority, role, constraints, enabled state, timestamps, optional embedding metadata. Activation: Always → exact/keyword matching → semantic retrieval при доступном embedding → deduplication → priority/relevance sorting → context budget. Если semantic-only entries недоступны из-за embedding failure, UI/diagnostics показывают деградацию; fallback задаётся явно.

### 7.4 Diary

Хранит только долговременные воспоминания после Judge. Entry включает content, type, confidence, importance, provenance, source references, validity interval, scope, relation links и merge/supersedes lineage. Merge не теряет происхождение; split создаёт отдельные утверждения; reject не добавляет запись в активный контекст.

### 7.5 Action Memory

Observer создаёт typed events: actor, action, target, outcome, location/dimension, world time, event time, visibility/scope, provenance. Шумные события coalesce; важные durable. Retrieval формирует компактные воспоминания; retention настраивается.

### 7.6 Thoughts

Structured Thinker outputs: state, emotion, desire, goal proposal, plan, uncertainty, evidence references, expiry, provenance. Каждое поле маркируется observed/reported/inferred/desired/planned. Это не hidden chain-of-thought и не raw prompt/completion. Просроченные планы не исполняются автоматически.

### 7.7 Daily Summary

Summary содержит scope, period, structured facts/text, input watermark, source IDs, previous version, generation status и checksum. Default threshold — каждые 10 сообщений, configurable. Open: Minecraft day или calendar/server-local day.

### 7.8 Транзакции и миграции

Многошаговые изменения выполняются транзакционно. Если backend не может обеспечить атомарность, используется journal/outbox + recovery. Migration проверяет source schema, создаёт backup/snapshot где возможно, выполняет versioned steps, валидирует итог и фиксирует версию только после успеха. При ошибке rollback или read-only recovery; silent reset запрещён.

## 8. Gnome Scheduler — общая модель

Task включает Task ID, type, owner scope, trigger event, priority, created/deadline, idempotency key, payload schema version, attempts, cancellation token, correlation ID и checkpoint/result.

Scheduler проверяет scope/payload/limits, coalesces дубликаты по policy, ставит в bounded priority queue, выделяет per-gnome/per-provider worker slot и передаёт immutable input snapshot. Worker не изменяет Minecraft world. Completion handler на server side повторно проверяет scope, snapshot version, cancellation и preconditions, применяет разрешённые изменения, публикует downstream events и фиксирует status/metrics.

Срочные gameplay/user tasks не должны ждать Summarizer. Нужны fairness и защита от starvation. Retry только для transient failures; backoff+jitter, max attempts/deadline. Validation/permission/unsupported capability errors не повторяются без изменения входа. Исчерпанные задачи переходят в failed/dead-letter state. Никакого unbounded queue. При shutdown intake прекращается, durable tasks checkpoint-ятся, uncertain external calls проходят reconciliation.

## 9. Пять гномиков — подробные контракты

### 9.1 Memorizer

**Назначение:** извлечь потенциально значимые воспоминания, не решая их окончательное сохранение.

**Разрешённые источники строго ограничены:** Conversation History и Thoughts в переданном NPC/player scope. Не читает Diary целиком, Action Memory, чужие player scopes, произвольные world files, secrets или hidden chain-of-thought.

**Триггеры:** финализированные сообщения, eligible ThoughtChanged events, batch trigger. Не запускается на каждом streaming token.

**Алгоритм:**
1. Scheduler создаёт задачу с точным scope и watermark.
2. Input assembler читает только History и Thoughts, формирует диапазон новых данных.
3. Каждый фрагмент сохраняет provenance: кто сообщил/наблюдал, когда и в каком контексте.
4. Отсеиваются временные реплики, инструкции, шутки и неподходящие кандидаты по policy.
5. Запрос идёт через отдельный Memorizer provider preset в Provider Gateway.
6. Structured output валидируется по schema, size, scope, evidence references, confidence и importance.
7. Кандидат получает epistemic type: fact, player-reported, observed, inference, desire, intention или plan.
8. Кандидаты сохраняются как pending и передаются Judge; прямой approved Diary write запрещён.
9. Watermark продвигается атомарно с сохранением candidate batch/очереди Judge.
10. Повтор диапазона не создаёт duplicate candidate IDs.

**Выход:** candidate batch, provenance, evidence refs, scope, confidence, importance, idempotency key.

**Отказы:** timeout → bounded retry; malformed output → reject/ограниченный repair path; missing source → cancelled/empty по типу; cross-scope references → security reject. Основной диалог не блокируется.

**Проверки:** невозможно получить Diary/Action Memory/чужую историю; inference/desire не превращается в fact; restart/retry не теряет и не дублирует candidates.

### 9.2 Judge

**Назначение:** принять решение по pending candidate относительно релевантных Diary records.

**Вход:** candidate, scoped Diary retrieval, provenance/evidence и optional embedding similarity.

**Алгоритм:**
1. Проверить candidate schema/scope/evidence.
2. Найти потенциальные дубликаты и противоречия в той же области, не выгружая весь Diary.
3. Embedding provider выбирается отдельным preset; проверить model/index version compatibility.
4. Similarity threshold только выбирает записи для сравнения, не принимает решение.
5. Judge preset анализирует смысл, субъект, время, контекст, epistemic type и contradictions.
6. Решение: ACCEPT_NEW, MERGE, SPLIT, REJECT или DEFER/NEEDS_REVIEW.
7. Validator проверяет target refs, scope, допустимость операции и epistemic consistency.
8. Persistence транзакционно создаёт/обновляет записи и lineage либо отклоняет/откладывает.
9. Candidate status и audit trail сохраняются; повтор не применяет side effect дважды.

Merge не теряет несовпадающие детали и источники. Разные субъекты/времена не схлопываются только из-за похожих слов. При сомнении — separate records или defer.

**Отказы:** embedding unavailable → explicit lexical/metadata fallback, если допустимо, иначе defer; Judge unavailable → pending сохраняется; transaction failure → no partial merge.

### 9.3 Thinker

**Назначение:** оценивать текущее состояние NPC и предлагать структурированные намерения/цели. Не исполняет действия.

**Триггеры:** целевой интервал около 30 секунд; значимые события (диалог завершён, изменение отношений, цель завершена/провалена, важное событие мира, изменение состояния). Частые события debounce/coalesce. Event trigger может перенести scheduled run, но не создаёт параллельный шторм.

**Контекст и источники:**
1. Character Definition — личность и ограничения.
2. Runtime State — режим, эмоции, потребности, активная цель.
3. Relationship State релевантного Player UUID; другие игроки не подмешиваются без причины.
4. Recent Conversation History в допустимом scope.
5. Diary retrieval — утверждённые релевантные воспоминания.
6. Action Memory — значимые недавние события.
7. Daily Summary и предыдущие structured Thoughts.
8. World Observer snapshot: позиция, окружение, доступность, опасности; timestamps/freshness.
9. Active tasks и rig/action capabilities.

Thinker не читает secrets, чужие private conversations, произвольные файлы или полный server log. Контекст snapshot-ится с version/time.

**Алгоритм:**
1. Scheduler coalesces triggers в одну задачу на NPC/scope.
2. Context assembler собирает данные, помечая stale/missing observations.
3. Policy layer исключает приватные/неподходящие сведения и добавляет hard constraints.
4. Provider Gateway использует независимый Thinker preset; не наследует настройки main dialog или других гномиков.
5. LLM возвращает ограниченную schema: assessment, emotion, desires, goal proposals, plan steps, confidence/uncertainty, evidence refs, validity/expiry.
6. Validator проверяет schema, ranges, allowed goal types, evidence, contradictions, freshness и scope.
7. State reconciler сравнивает версии: no-op без изменений; закрывает устаревшие планы; не затирает приоритетную активную задачу без правила.
8. Сохраняет structured Thoughts с provenance/expiry.
9. Goal Scheduler получает только валидированные proposals и самостоятельно проверяет preconditions/priority.
10. Presentation Intent может отдельно обновить эмоцию/выражение; не запускает физическое действие напрямую.

**Связи с другими гномиками:** Memorizer получает ThoughtChanged event, но Thinker не вызывает его напрямую. Judge работает только с candidates от Memorizer, а не с каждой мыслью. Bot получает validated goals/actions, не сырой completion. Summarizer использует только явно разрешённые structured events. Thinker может работать при временно недоступном Diary, помечая контекст неполным.

**Надёжность:** одна активная задача на NPC/scope; periodic runs не накапливаются; stale result отбрасывается по state version; timeout не останавливает NPC; invalid plan не исполняется.

### 9.4 Bot

**Назначение:** преобразовать выбранную цель в безопасные typed actions и semantic motion intents.

**Вход:** validated Goal, Runtime State, action whitelist, world observations, capabilities, permissions и текущий navigation/motion status.

**Алгоритм:**
1. Goal Scheduler выбирает цель и запрашивает action plan.
2. Bot формирует typed steps: observe, navigate, interact, speak, gesture, wait, cancel.
3. Action Validator проверяет type/parameters/target/distance/world state/NPC mode/permissions/cooldown/capability.
4. Planner строит dependency graph и preconditions; невозможные шаги отклоняются с reason code.
5. Gameplay mutation выполняется через server Action Executor; долгие вычисления не выполняются на server thread.
6. Navigation делегируется Navigation Adapter; gesture/motion — Motion Engine.
7. Action lifecycle: proposed → validated → queued → running → succeeded/failed/cancelled/expired.
8. Результат сверяется с фактическим world state, а не только с API response.
9. Outcome event записывается в Action Memory и возвращается Goal Scheduler/Thinker.
10. После failure план ограниченно пересчитывается; uncertain side effect не повторяется до reconciliation.

LLM не может передать Java class, command string, arbitrary NBT, file path, URL или raw game command. Только зарегистрированные typed schemas.

### 9.5 Summarizer

**Назначение:** обновлять компактную сводку на основе предыдущей версии и нового диапазона.

**Триггер:** default каждые 10 сообщений, configurable; дополнительный event trigger — только по policy. Message-count semantics и day boundary должны быть согласованы.

**Вход:** previous summary/version, новые finalized messages/events после watermark, scope, period и отдельный Summarizer preset.

**Алгоритм:**
1. Сформировать стабильный input range по sequence/watermark.
2. Проверить отсутствие уже завершённой задачи для диапазона.
3. Прочитать previous summary и новые записи по времени.
4. Вызвать Provider Gateway.
5. Проверить schema, scope, size, source refs и фактическую обоснованность содержания.
6. Сохранить новую версию summary/source IDs.
7. Продвинуть watermark атомарно с commit.
8. Сохранить старую версию по retention policy.
9. Опубликовать SummaryUpdated без циклического повторного trigger.

Idempotency key = scope + input range/watermark + policy version. Restart не теряет диапазон, повтор не удваивает текст, новые сообщения не пропускаются.

## 10. Provider Gateway и API

Ни один AI component не вызывает SDK/HTTP напрямую. Все обращения идут через Provider Gateway и typed capabilities.

Независимые presets: Main Dialogue, Memorizer, Judge, Thinker, Bot (если LLM используется), Summarizer, Embedding, TTS. Reuse одного profile ID допустим только как явная настройка.

Adapter lifecycle:
1. Config Resolver читает preset.
2. Secret Resolver извлекает credential только из server-side secret store/env/config.
3. Adapter Factory создаёт adapter и проверяет capabilities.
4. Preflight проверяет конфигурацию без отправки приватного контекста, если поддерживается.
5. Request Builder формирует provider-neutral request.
6. Adapter преобразует его в provider-specific transport.
7. Transport выполняет запрос с connect/read/overall deadlines, cancellation, size limits и TLS validation.
8. Response Normalizer приводит результат к neutral schema.
9. Validator проверяет контракт назначения.
10. Metrics фиксируют latency/usage/error class без secrets и полного приватного prompt.

Capabilities: chat, streaming, structured output, tool calling (не unrestricted tools), embeddings, TTS voices/formats, max context и rate limits. Совместимость не предполагается по одному URL/названию. Missing capability приводит к явному fallback или disabled state.

Secrets не входят в Content Packs, DTO, crash reports, diagnostic bundles или client sync. UI показывает masked status. Логи маскируют headers/query params/error payloads. Ошибка TTS не ломает chat; embedding failure не ломает базовый диалог; один гномик не блокирует остальных.

Классы ошибок: configuration, authentication, authorization, rate limit, timeout, network, unavailable, unsupported capability, malformed response, schema violation, cancellation. Для каждого назначения независимая health/circuit-breaker policy.

## 11. World Event Observer

Observer подписывается на Fabric/server events и преобразует callbacks в typed events; raw callbacks не вызывают AI напрямую.

Pipeline: получить событие → проверить активность мира/участников → нормализовать actor/target/location/action/outcome/time → relevance/privacy/dedup filters → coalesce шумных событий → durable write для важных событий → publish consumers (Runtime, Thinker, Action Memory, Goal Scheduler).

Каждый тип события помечается durable или best-effort. Observer не знает prompt/provider. Действия NPC не должны создавать бесконечный feedback loop, запускающий то же действие.

## 12. Goal, Action Executor и Navigation

Каждый action type регистрируется с stable version, input schema/bounds, required permissions/capabilities, server preconditions, idempotency/retry classification, expected transition, timeout/cancellation, observable completion и audit event.

Validator выполняется непосредственно перед исполнением, даже если plan проверялся ранее: мир мог измениться.

Navigation получает только validated destination. Проверяет dimension, loaded chunks, reachable bounds и path; отслеживает progress, stuck, hazards и invalidation; передаёт locomotion intent Motion Engine; завершается по completion/cancel/target moved/path failure/safety preemption; возвращает фактическую позицию и reason code. Построенный path не означает успешное достижение цели.

Для side-effect actions хранится execution record. После crash uncertain action сверяется с миром прежде чем решать retry.

## 13. Agnor Motion Engine

### 13.1 Входы и pipeline

Входы: semantic tasks, navigation velocity/direction, emotion/expression intent, interaction targets, rig capability profile, clips, current authoritative pose и collision observations.

Pipeline:
1. Intent Interpreter превращает validated goals/actions в semantic tasks: look_at, turn_towards, reach, grasp, walk_to, wave, sit, stand, maintain_balance.
2. Task Planner управляет task graph, priority, dependencies, masks, preemption, timeout и completion.
3. Navigation/Locomotion рассчитывает gait intent по path/velocity.
4. Whole Body Coordinator разрешает конфликты каналов и сохраняет баланс.
5. IK/Constraints: Two-Bone IK, FABRIK/CCD, pole vectors, joint limits, foot placement/IK, gaze.
6. Pose Layer Mixer смешивает base animation, locomotion, gesture, additive reaction, face/viseme и IK corrections.
7. Secondary Physics выполняет spring/damper, Verlet/XPBD или проверенный solver для hair/clothing/accessories с proxies/clamps.
8. Pose Publisher публикует versioned snapshots, state changes и completion events.
9. Client interpolation сглаживает snapshots, не пересчитывая authoritative gameplay motion.

### 13.2 Task arbitration

Task имеет priority, target, start/deadline, body channels, blend weight, preconditions, completion и interruptibility. Safety/balance invariants имеют высший приоритет; locomotion не перезаписывается случайным gesture; gaze может сочетаться с ходьбой; interaction IK приоритетнее idle в занятых каналах; face emotion и lip-sync смешиваются по отдельным каналам. Прерванная задача освобождает locks/weights и получает terminal reason.

### 13.3 Rig capability и physics

Для каждого ассета формируется capability report: semantic bone mapping, hierarchy, skinning, clips, morph/viseme channels, spring chains, collision proxies, sockets, scale/orientation и unsupported features. Fallback описан явно; missing bone не вызывает crash.

Secondary physics ограничена художественными chains/proxies, с stable timestep, clamps, damping, collision bounds, reset on teleport/unload, pause/resume и performance budget. NaN/invalid state сбрасывает только повреждённую chain в rest pose.

Всё, что влияет на authoritative gameplay pose/root movement, рассчитывается на сервере. Client smoothing локален и не authoritative. Возможная оптимизация compact pose controls допустима только при сохранении серверной истины и подтверждённом benchmark.

### 13.4 Network snapshots

DTO включает NPC/world identity, sequence, server timestamp/tick, protocol version, model/content version, root transform, motion task state и минимальный pose/control payload. Не отправлять полный skeleton каждый tick без измерений.

Клиент проверяет version/identity/sequence, отбрасывает stale snapshots, интерполирует, безопасно обрабатывает packet loss и сбрасывает несовместимый pose cache при смене модели. Клиентская поза никогда не отправляется как authoritative.

## 14. Asset Importer и Content Packs

Сначала ищется готовая модель с проверяемыми provenance/rights. Blender используется для проверки mesh/materials, адаптации rig, retargeting, skin weights, facial channels, sockets и экспорта; моделирование с нуля не является обязательным default. Source asset не перезаписывается.

Importer: проверить format/size/archive/path limits → import mesh/materials/skeleton/clips/extensions → normalize axes/units/scale/origin → semantic rig mapping и weights → обнаружить morphs/visemes/springs/collision proxies/sockets → проверить textures/shaders → сформировать capability report PASS/WARN/UNSUPPORTED → reference pose/deformation checks → export runtime asset + rig descriptor + provenance/license → validate Content Pack.

Manifest: pack ID/version, supported API version, dependencies, asset IDs, hashes, formats, capability metadata, provenance/license и paths. Validator проверяет path traversal, absolute paths, zip bombs, size/file count, file signatures, checksums, duplicate IDs, dependency cycles, unsupported schema, malformed metadata и conflicts. Activation атомарная: validate → stage → resolve → activate; при ошибке предыдущая valid version остаётся.

Server синхронизирует только ассеты с разрешённым redistribution и явным admin opt-in. Unknown/restricted assets не передаются клиентам; клиент использует local copy или placeholder. Отсутствие модели не ломает NPC.

Временные restricted models допускаются в локальной dev-среде/dev JAR/local Content Pack на риск пользователя. Не коммитить файлы в public repo, не включать в public release, не передавать игрокам/синхронизацией. Замена всего временного набора — перед финальным полным релизом после Phase 11, не перед Phase 10. Register содержит source page, direct URL, author, local path, SHA-256, rights status, reason и replacement plan.

## 15. Network protocol и handlers

Каждый packet имеет protocol version, message type, bounded payload, request ID/sequence и инициирующую сторону. DTO не содержит secrets, prompts, private memory или произвольных serialized Java objects.

Incoming handler: sender identity берётся из server context, не payload → packet size/schema/version/rate validation → world/NPC/permission/distance checks → normalize в internal command → enqueue server executor → ack/rejection с request ID/reason → результат отдельным state update.

Server выбирает recipients по tracking/permission/visibility. Private dialogue/UI state идёт только адресному игроку; public presentation/game state — tracking clients. Optional fields имеют default/unknown behavior; semantic change требует protocol versioning. Размеры и частота ограничены.

## 16. UI и пользовательские операции

UI areas: Dialogue, Character status/relationships, owner control center, permissions, provider presets, Memory viewer/editor, Lorebook editor, Content Pack/model manager, diagnostics.

Каждая команда: локальная UX validation → typed request + request ID → pending UI state → server ack/rejection → отображение server response/snapshot. При timeout показывается uncertain/pending и запрашивается status; side-effect нельзя слепо повторять.

Server повторно проверяет все права независимо от UI. Owner имеет полный набор; остальные получают явные permission scopes. Permission changes атомарны и audit logged.

## 17. Voice / TTS

Dialogue response сначала сохраняется как текст. Затем отдельная TTS task выбирает voice profile, проверяет capability/format/limits, отправляет минимальный text payload через Provider Gateway, проверяет audio size/duration, применяет recipient policy и доставляет playback metadata/audio. Viseme timeline подаётся в Face Controller как presentation input. TTS failure не отменяет текст. При отсутствии viseme timing используется явно обозначенный fallback или нет lip-sync.

## 18. Permissions и privacy

Единый Permission Service проверяет server-side identity, world, NPC ownership, role и scope для каждой команды. Различать owner/admin controls, dialogue, inspect public presentation, read private memory, edit lorebook/personality, configure provider/secrets, create/delete NPC, manage packs и allow asset sync. Default deny для private memory/config/secrets. UUID — идентификатор, nick — display. UI permission check не заменяет server check.

## 19. Observability

Каждая сквозная операция получает correlation ID, background work — Task ID. Диагностика фиксирует trigger, components, context/state/schema/provider versions, queue wait, provider latency, validation/persistence time, final status, retry/cancel/fallback и изменённое состояние.

Логи не содержат secrets, полных private conversations по умолчанию, raw prompts или пользовательских dumps. Debug content разрешается только локальным opt-in с предупреждением/redaction.

Metrics: queue depth/age, task latency, provider latency/errors/rate limits/tokens/cost, storage latency/failures, stale result count, action outcomes, navigation stuck, motion solver budget, snapshot bandwidth/drops, asset errors, client frame time.

GitHub Actions: обычный CI использует check status, logs, test output и job summary. Artifacts загружаются только для ручной проверки человеком; минимальный набор, короткий retention, без secrets/private data/restricted temporary assets.

## 20. Общие отказные сценарии

| Отказ | Обязательное поведение |
|---|---|
| LLM timeout | Не блокировать server tick; сохранить статус; не повторять uncertain request вслепую |
| Provider 429 | Учитывать Retry-After, ограничивать очередь, degraded state |
| Invalid output | Reject; не писать в память и не исполнять |
| Embedding unavailable | Явный fallback или disabled semantic feature |
| TTS failure | Текст остаётся доступен |
| DB write failure | Не подтверждать durable success; rollback/recovery |
| Crash during workflow | Transaction/outbox/watermark/idempotency recovery |
| Client disconnect | Продолжить или отменить по task policy; durable result не теряется |
| Server shutdown | Stop intake, cancel/flush/checkpoint |
| NPC unload | Сохранить durable state, отменить ephemeral tasks |
| Missing client model | Placeholder + diagnostic |
| Unsupported rig | Capability fallback |
| Packet replay | Deduplicate request ID, повторно проверить state |
| Malicious payload | Reject, rate limit, security event |
| Late callback after unload | Проверить world generation, не писать в закрытый scope |
| Motion NaN | Reset affected chain/pose |
| Corrupt Content Pack | Не активировать; оставить previous valid version |
| Migration failure | Rollback/read-only recovery; не делать silent reset |

## 21. Сквозные сценарии

### 21.1 Игрок пишет NPC

Client UI → server packet → identity/permission validation → Dialogue Orchestrator → inbound durable message → Context Builder → Main Dialogue preset → Provider Gateway → output validation → response commit → private delivery → gnome/event scheduling → optional TTS → UI display.

Проверки: scope isolation, duplicate packet, timeout, provider failure, disconnect, restart между persist и delivery, отсутствие утечки другому игроку.

### 21.2 NPC запоминает факт

Finalized message/eligible Thought → Scheduler → Memorizer (только History + Thoughts) → candidate/provenance → Judge retrieval → optional embedding → Judge decision → transactional Diary update → audit/status → Context Builder retrieval.

Проверки: candidate не обходит Judge; provenance сохранён; merge/split/reject; concurrent candidate; retry/restart; scope isolation.

### 21.3 Thinker обновляет намерение

Periodic/event trigger → coalescing → context snapshot → Thinker preset → structured assessment → validator → freshness/version reconciliation → Thoughts persistence → Goal Scheduler → proposal validation. Bot не запускается до принятия цели.

Проверки: stale response, burst events, timeout, conflicting goal, missing memory, restart, desire vs fact.

### 21.4 NPC идёт к игроку и машет

Validated goal → Goal Scheduler → Bot typed plan → Action Validator → Navigation → path progress → Motion Engine walk_to/gait → arrival predicate → Motion Engine wave → completion event → Action Memory → Thinker/Goal update → server pose snapshots → client interpolation.

Проверки: препятствие, moving target, stuck, cancel, gesture/locomotion conflict, second client, server authority, rig без нужной кости.

### 21.5 Новый Content Pack

User selects pack → server permission → manifest/size/path/hash/dependency validation → staging → asset/capability validation → atomic activation → model registry version → client availability → safe model snapshot.

Проверки: zip traversal, decompression bomb, checksum mismatch, duplicate ID, unsupported version, missing client model, rollback.

### 21.6 Voice

Finalized dialogue → TTS task → provider capability → audio validation → recipient policy → playback/audio delivery → client playback → viseme/expression timeline. TTS failure не меняет transcript.

### 21.7 Multiplayer privacy

Player A asks NPC → server authenticates A → Context Builder scope A → response delivered only A. Player B получает только public presentation state. Проверить forged UUID payload и одинаковые nicknames при разных UUID.

## 22. Конкурентность и согласованность

Server thread выполняет bounded work: checks, small state transitions, scheduling, result application и world mutations. File IO, DB heavy queries, embedding, LLM, TTS, import и expensive solver work — вне server thread или в budgeted subsystem. Async result проверяет world/NPC generation и state version.

Для одного NPC transitions сериализуются через actor/mailbox или эквивалентный single-writer механизм. Разные NPC могут обрабатываться параллельно. Memory operations с watermark/merge сериализуются или используют optimistic concurrency. Client time не определяет порядок. Snapshots имеют monotonic sequence. Выбор actor/mailbox vs locks — Open, но single-writer semantics обязательны.

Не держать DB locks во время API call; не ждать worker синхронно на server thread; очереди bounded. При перегрузке coalesce low-value tasks, reject/rate-limit новые запросы или degrade features. Не нарушать permissions и memory integrity ради throughput.

## 23. Performance и масштабирование

Измерять server tick time при разном числе NPC; queue wait/worker/provider throughput; persistence latency/lock contention; retrieval/embedding latency; solver time; pose snapshot bandwidth/recipients; client draw calls, skinning/morph cost, texture memory/FPS; TTS bandwidth/latency.

Профилировать один NPC, несколько NPC, crowded server, dedicated/integrated server и client без asset. При превышении budget снижается частота необязательных Thinker/secondary motion updates, но не permissions, memory integrity или authoritative gameplay.

## 24. Configuration и presets

Разделить global server policy, world settings, per-NPC definition/runtime options, per-gnome presets, main dialogue, embedding, TTS и client presentation. Определить precedence/inheritance. Новые задачи используют новую версию профиля; выполняющаяся задача сохраняет snapshot прежней версии. Invalid config не применяется частично. UI показывает validation/capabilities.

Export/import удаляет secrets; credentials привязываются на host. Изменение provider profile не меняет NPC memory.

## 25. Тестовая стратегия

Уровни:
1. Unit: schemas, validators, planners, state machines, dedupe, scope filters.
2. Contract: Provider adapters, Storage API, Network DTO, Content Pack manifest, Rig capability schema.
3. Integration: DB transactions/migrations, scheduler/provider failure, gnome pipeline, action execution.
4. Game integration: Fabric 1.21.1 integrated + dedicated server.
5. Multiplayer: минимум два клиента с разными UUID/permissions/private context.
6. End-to-end: user action → UI → packet → server state → runtime → persistence/provider → result → display.
7. Fault injection: timeout, rate limit, disconnect, crash/restart, corrupt data, stale callbacks.
8. Load/performance: NPC concurrency, queue saturation, renderer/motion/network budgets.
9. Asset compatibility: complete rig, partial rig, no morphs, missing textures, unsupported format.

Для каждой функции обязательны happy path, invalid input, missing dependency, timeout, duplicate/replay, concurrency, cancellation, restart/recovery, wrong scope/permission, stale version, observable error/recovery и end-to-end result.

Compile/CI alone не даёт Verified. Multiplayer features требуют dedicated server + two clients; persistence — restart/migration; AI — malformed output/timeout/isolation; renderer — acceptance scenarios ADR-0006/0007 на реальной модели.

## 26. Открытые решения — не заполнять догадками

1. Fabric 1.21.1 project structure, entrypoints и lifecycle APIs.
2. Persistence backend, schema и vector index.
3. Actor/mailbox vs иной single-writer mechanism.
4. Provider adapter baseline, structured output compatibility и streaming semantics.
5. Message count policy для history/summarizer.
6. Daily Summary day boundary.
7. Retention для Thoughts, Action Memory, pending candidates и audit.
8. Conflict policy для world facts, NPC beliefs и player-specific facts.
9. Permission matrix и owner/admin transfer.
10. Renderer library/custom renderer, skinning/morph runtime.
11. Pose replication и authoritative solver budget.
12. Navigation implementation и stuck/repath criteria.
13. Rig descriptor schema и importer formats.
14. Content Pack sync protocol при разрешённых правах.
15. Voice transport/storage/viseme support.
16. NPC count, queue sizes, timeouts, retry budgets/resource limits.
17. Calendar/game-day semantics/timezone.
18. Backup/restore/world migration.

Каждый вопрос закрывается prototype, measurement, compatibility test или явным accepted trade-off.

## 27. Дальнейшая детализация и Definition of Blueprint Complete

Blueprint — единый верхний документ. Отдельная спецификация создаётся только когда алгоритм/контракт слишком велик, с явной ссылкой из этого документа. Стабильные внешние контракты выносятся в versioned schemas. Каждая функция связывается с roadmap phase, ADR, тестами и отчётом. Изменение поведения обновляет blueprint и связанные документы в одной логической задаче.

Blueprint можно считать готовым к соответствующей реализации, когда:
- у каждой подсистемы заполнена карточка компонента;
- у каждого пользовательского сценария есть end-to-end flow;
- каждый межкомпонентный вызов имеет contract, scope, threading и error semantics;
- каждое хранилище имеет owner, schema, lifecycle и migration;
- каждая async operation имеет timeout, cancellation, retry, dedupe и stale-result policy;
- каждая world command имеет permissions, preconditions, idempotency и completion;
- client/server boundary и privacy scopes проверяемы;
- renderer/model capability fallback определён;
- Open решения закрыты evidence или вынесены блокерами нужной фазы;
- тесты покрывают errors, restart, concurrency и multiplayer.

**Текущий статус 0.1:** зафиксированы карта подсистем, основные алгоритмы, связи и общие отказные правила. Это проектный baseline, не окончательная спецификация реализации. Open решения раздела 26 и измеряемые параметры должны быть закрыты до соответствующих этапов.