# Историческая фазовая часть Blueprint

Не нормативный маршрут. Сохранена без сокращения для трассировки прежних design checklists; технический каталог 1–29 остаётся в действующем [Blueprint](../IMPLEMENTATION_BLUEPRINT.md).

### 0.4 Порядок чтения и проектирования

Основной маршрут теперь совпадает с фазами ROADMAP 0–11. Проектирование каждой следующей фазы начинается после проверки выходных артефактов предыдущей, но общие контракты (идентификаторы, scope, server authority, версии DTO, ошибки, observability) определяются заранее и не дублируются.

В документе есть два слоя:
- **Часть A — фазовый маршрут ниже:** в каком порядке проектировать и реализовывать функции; какие решения нужны до начала фазы; какой результат проектирования должен существовать.
- **Часть B — технический каталог, разделы 1–29:** подробные алгоритмы и общие контракты компонентов. Ссылки в каждой фазе ведут к этим разделам.

Правило: нельзя начинать реализацию фазы только потому, что для неё есть строка в ROADMAP. Сначала закрывается её design gate: компоненты, contracts, scopes, state transitions, thread ownership, error/recovery behavior и тестовые сценарии описаны. Неизвестные технические значения остаются Open до прототипа/измерения.

### 0.5 Сверка ROADMAP с текущим Blueprint

Состояние отражает документальную сверку, а не наличие работающего кода.

| Фаза ROADMAP | Основные требования ROADMAP | Разделы Blueprint | Результат сверки |
|---|---|---|---|
| 0. Технические проверки | Fabric/client/server, каталоги, базовый packet, renderer, IK, gait, skinning/morph/blending/physics, host pose sync, лицензии и модельные тесты | 1–3, 10, 13–15, 20, 22–25 | **Частично.** Компоненты и критерии есть; нужен единый протокол spike, таблица измерений и решение по storage/catalog path |
| 1. Каркас | Lifecycle, common/server/client, config/catalogs, protocol/versioning, permissions, settings UI, safe logging, единый Storage Facade foundation | 1–3, 7, 15–16, 18–20, 24 | **Частично.** Единый persistence/storage foundation используется далее NPC state, dialogue history и memory stores; не создавать параллельные ad-hoc storage paths |
| 2. Character Runtime | NPC UUID, базовый Character Definition Registry, create/delete/load, runtime state, relationships per player UUID, world/NPC/player saves, two-player isolation; server runtime independent of renderer | 3–4, 7, 18, 21, 25 | **Частично.** Базовая схема/Registry создаются в Phase 2; NPC server state работает с placeholder, а Phase 3 подключает Presentation Adapter к тому же NPC UUID; Phase 4 добавляет Pack Definition Source к тому же Registry |
| 3. 3D и visual control | Import готовой модели, rig/renderer, Character Presentation Adapter, animation layers, Motion Engine foundation, IK, gait primitives, facial rig capability, secondary physics, pose sync | 13–14, 21.4, 23, 25 | **Частично.** Фаза 3 подключает renderer к NPC UUID уже созданным в Phase 2 и создаёт технический исполнительный слой/test harness, но не полноценное поведение. Dialogue reactions подключаются в Фазе 5, Thinker affect в Фазе 7, autonomous world motion в Фазе 8, lip-sync в Фазе 9 |
| 4. Content Packs | Manifest/schema version, scan/enable/disable, assets/definitions/prompts, provenance/license, capability report/fallback, priority overrides, checksum/version, safe client policy | 14–15, 19–20 | **Частично.** Content Pack schema/version отделена от executable Addon API; prompts/behavior/voice descriptors регистрируются пассивно и включаются только при готовности consumer Phase 5/7/8/9; package lifecycle, overrides, rollback и hot reload описаны отдельно |
| 5. Provider Gateway/dialogue | Provider Gateway and Preset Registry, active Main Dialogue preset, local/cloud endpoints, timeouts/retry/streaming, durable dialogue history/outbox, system prompts/roles, host secrets, initial Context Builder sources and presentation reactions | 5–6, 10, 13.5, 15–16, 24 | **Частично.** Only Main Dialogue is active in Phase 5; gnome/Embedding, Bot and TTS destinations activate with their consumers in Phases 7/8/9. Context Builder sources expand in Phase 6; no second builder/history store |
| 6. Шесть хранилищ | Все 6 stores, единый storage API/backend, migrations/backups, world/NPC/player isolation, indexes/retention, canonical History message count и Summary watermark semantics | 7, 19–20, 22, 25 | **Частично.** Action Memory не считается рабочим без Event Feed producer Phase 7; Thoughts — без Thinker writer Phase 7. Единица подсчёта Summary определена до её реализации |
| 7. Когнитивные гномики и scheduler | Event Feed, transactional History outbox consumers, Memorizer, Judge, Thinker, Summarizer, per-consumer trigger policies, Affective Mapper, Goal Proposal Inbox, queues/retries/metrics; Bot только contract | 8–10, 13.5, 16, 19–20, 22, 25 | **Совместная интеграция обязательна.** Четыре когнитивных гномика, two event streams и Gnome Task Scheduler принимаются как один цикл; Summarizer triggers only at 10 committed History records, Thinker on turn-finalized/events/periodic timer |
| 8. Автономность | Full World Observer, Goal Scheduler consuming proposals, Bot (fifth gnome), Action Registry/Validator/Executor, Navigation, Action Memory outcome events, Motion Engine bridge | 4, 8, 11–13, 13.5, 21.3–21.4, 22, 25 | **Единый вертикальный этап.** Goal Scheduler, Bot, validator/executor, navigation and full observer form one feedback loop and must be integrated/tested together |
| 9. Голос | TTS adapters/presets, per-NPC voice, audio queue/interruption/cache, client playback, real viseme-driven lip-sync integrated with Face Controller, graceful degradation | 10, 13, 13.5, 15–17, 21.6 | **Совместная интеграция.** TTS generation, audio playback clock and viseme timeline must be implemented and accepted as one end-to-end feature |
| 10. Полировка и первый релиз | Multiplayer regression, load, long sessions/restart, errors/migrations, API cost/performance settings, install/pack docs, compatibility matrix | 19–25 | **Частично.** Test taxonomy есть; нужно формализовать release matrix, soak tests, packaging/update/rollback и user documentation gate |
| 11. Мир и финальная версия | World transfer, character packs, addon integrations/Create, expanded voice/interactions, final replacement of temporary assets | 14, 21, 26–28 | **Проектирование добавлено.** Migration workflow и addon API contract описаны; addon зависит от API freeze Phase 10, реализация остаётся задачей Phase 11 |

### 0.5.1 Обратная сверка ARCHITECTURE и ADR

#### ARCHITECTURE.md → Blueprint

| Источник ARCHITECTURE | Соответствующие разделы Blueprint | Итог |
|---|---|---|
| 1. Цели и границы | 0.5–0.8, 1–2, 27–28 | Сохранено; границы standalone release и Phase 11 отражены |
| 2. Архитектурные слои | 2 | Сохранено |
| 3. Хост-авторитетная модель | 1, 3, 15, 18, 20, 22 | Сохранено; runtime verification остаётся обязательной |
| 4. Character Runtime | 3–4, 12, 21 | Сохранено; точная state machine и persistence gates назначены Phase 2 |
| 5. Система памяти | 6–9 | Сохранено; шесть stores и ограничения Memorizer/Judge/Thinker/Summarizer описаны |
| 6. Gnome Task Scheduler | 8–9, 19–20, 22 | Сохранено; fairness, concurrency, retry и recovery заданы; numeric budgets остаются Open |
| 7. Provider Gateway | 5, 10, 17, 24 | Сохранено; provider-specific schemas/capabilities требуют Phase 0/5/7/9 validation |
| 8. 3D, Motion Engine и физика | 13–14, 21.4, 23, 25 | Сохранено; renderer/solver/pose budgets остаются предметом Phase 0/3 spikes |
| 9. Content Packs | 14–15, 20 | Расширено package state machine, enable/disable, precedence, hot reload и rollback для Phase 4 |
| 10. Persistence и изоляция | 3, 7, 20, 22, 25 | Расширено backup/restore/retention и staged migration path; backend/schema остаются Open до Phase 0/6 |
| 11. Безопасность и права | 1, 6, 15–16, 18, 20 | Сохранено; точная permission matrix закрывается в Phase 1 и проверяется в каждой фазе |
| 12. Совместимость и дополнительные моды | 27 | Добавлен отсутствовавший optional extension/addon API contract |
| 13. Открытые технические вопросы | 0.6–0.8, 26–28 | Каждый вопрос привязан к фазе closure; неразрешённые решения не выданы за готовые |

#### ADR-0001…ADR-0008 → Blueprint

| ADR | Разделы Blueprint | Статус соответствия |
|---|---|---|
| ADR-0001 Platform | 1–3, 15, 22, 25; Phase 0–1 | Требования отражены; Fabric 1.21.1 build/runtime proof остаётся Phase 0 gate |
| ADR-0002 Host-authoritative runtime | 1, 3, 15, 18, 20–22 | Требование отражено сквозными инвариантами; требуется multiplayer verification |
| ADR-0003 Content Packs | 14–15, 20; Phase 0/4 | Ограничения и package lifecycle описаны; path/sync/hot reload остаются техническими проверками |
| ADR-0004 Memory pipeline | 6–10, 20, 22; Phase 5–8 | Шесть stores, Event Feed producer, когнитивный цикл Phase 7 и Bot/Action expansion Phase 8 сопоставлены; schema/index/retention/day-boundary остаются Open до фаз внедрения |
| ADR-0005 Optional integrations | 27; internal modularity Phase 1, Action/Goal/Event API design Phase 8, freeze Phase 10, addon Phase 11 | Публичный API не фиксируется до стабилизации базовых contracts; реальная совместимость Create не заявляется |
| ADR-0006 Character visual quality | 13–14, 23, 25; Phase 0/3/10 | Acceptance profile и capability fallbacks сохранены; конкретный renderer и benchmark не утверждены без spike |
| ADR-0007 Procedural Motion Engine | 12–13, 21.4, 23, 25; Phase 0/3/8 | Semantic tasks/pipeline отражены; solver strategy, timestep, collisions и snapshot rate остаются Open до измерений |
| ADR-0008 External character assets | 14; Phase 0/3/4/11 | Asset-first, Blender adaptation, capability report, provenance и restricted-asset release gate отражены |

Матрицы фиксируют прослеживаемость требований. Они не означают, что каждый пункт реализован, а статус «сохранено» относится только к качеству проектной документации.

### 0.6 Порядок проектирования по фазам 0–11

Для каждой фазы ниже перечислены не только работы ROADMAP, но и то, что должно быть спроектировано **до реализации**. Это основной последовательный план проектирования.

#### Фаза 0 — базовые технические доказательства

**Сначала проектируем:**
1. Минимальный Fabric 1.21.1 / Java 21 bootstrap, разделение dedicated/integrated server и client-only classes.
2. Протокол простого server→client и client→server state request: version, identity, validation, sequence и error response.
3. Data root resolver: client game directory, dedicated server directory, permissions, нестандартный game directory и отсутствие доступа к каталогу.
4. Renderer spike: один готовый rigged anime asset и один static/partial-rig asset; перечень обязательных runtime controls — skinning, bone pose read/write, morph weights, animation blending, procedural overrides, secondary chains.
5. Motion spike: semantic task contract и renderer/solver feasibility: look-at/reach/turn, Two-Bone IK, сравнение FABRIK/CCD где применимо, whole-body priority approach, gait и spring-chain. Все задачи подаются через scripted/synthetic test driver; это проверяет исполнитель, а не AI-поведение персонажа.
6. Host-authoritative pose test: один server simulation, два клиента, sequence/interpolation, latency/loss behavior.
7. Storage spike: сравнение SQLite/JSON для server save lifecycle, transactions, backup/migration, concurrent reads/writes и дальнейших шести stores. До выбора нельзя фиксировать окончательную схему.
8. Asset/legal record: проверка лицензии и provenance тестовых файлов; при отсутствии пригодного файла — локальный temporary restricted asset по ADR-0008 и запись в register.
9. Compatibility spike: Fabric API version, candidate renderer dependencies, Sodium/Iris interaction и лицензии библиотек.

**Артефакты выхода проектирования:** spike matrix с версией зависимостей, тестовым asset/capability report, renderer/solver measurements, network proof, data-root/storage decision и списком ограничений. Недельный спринт из ROADMAP — цель демонстратора, не критерий снижения полноты.

**Design gate:** не выбирать renderer, solver, storage engine или сетевую частоту без воспроизводимого теста. Подтверждённый результат фиксируется в ADR/отчёте.

#### Фаза 1 — модовый каркас и security boundary

**Проектируем:**
1. Bootstrap/lifecycle: common, server, client entrypoints; startup, world load/unload, server stop, dedicated-server classloading.
2. Configuration system: global/server/world/NPC/client scopes, defaults, schema versions, validation, atomic apply, reload policy.
3. Data root/catalog layout: packs, presets, cache, logs и server/world data; отделение общих ресурсов от данных конкретного мира.
4. Network protocol: DTO versioning, request IDs, bounded payloads, sender identity, acknowledgements, rejection reason codes, rate limits.
5. Permission service: начальная роль owner/admin/player, default deny, scope checks и будущая возможность расширения без обхода server validation.
6. Settings UI: pending → server response → persisted result; ошибки и permission denial отображаются по серверному ответу.
7. Safe logging/diagnostics: correlation IDs, redaction, уровни логирования, opt-in для чувствительной диагностики.
8. Threading contract: server thread только для bounded state/world changes; background executors и возврат результата через server scheduler.
9. Storage Facade foundation: единый интерфейс транзакций, schema versioning, scope enforcement, world-scoped repositories, migrations и durable outbox/task records. Backend выбирается в Phase 0; этот же сервис расширяется в фазах 2, 5, 6 и 7, без отдельной базы на каждую функцию.

**Артефакты:** lifecycle diagram, config schema, каталог путей, packet contract, минимальная permission matrix, UI request state machine, logging/redaction rules.

**Design gate:** dedicated server не загружает client-only classes; ни один запрос UI не меняет state до серверной проверки; конфигурация переживает перезапуск и не применяется частично.

#### Фаза 2 — Character Runtime и первая NPC

**Проектируем:**
1. NPC identity: UUID, Character Definition ID/version, world association, ownership/creator и правила уникальности.
2. Runtime state machine: Constructing → Active → Suspended/Unloaded → Removing → Removed/Failed; допустимые переходы и восстановление.
3. Creation flow: permission → Character Definition schema/ownership validation → server-side Runtime Factory → initial persistence → spawn snapshot → idempotent acknowledgement. Не требовать наличия renderer, client asset или полного rig capability для создания/активации server NPC.
4. Delete flow: удаление entity, сохранение/удаление памяти, подтверждение пользователя, каскадные зависимости и восстановление после сбоя.
5. Relationship State: ключ NPC UUID + Player UUID; ник только display; lifecycle игрока и правила удаления/retention.
6. World/NPC/player persistence: границы save data, atomic writes, load ordering, world isolation и backup strategy.
7. Concurrency: единственный владелец runtime state конкретного NPC; stale callback после unload не применяет старые изменения.
8. Базовый Character Definition schema + Registry: встроенный/dev definition source для первой NPC, стабильный ID/version и typed references на prompts/assets. Внешние Content Packs позже подключаются как дополнительный source к тому же Registry; не создавать отдельную pack-only schema.
9. Разделить server definition readiness и client presentation readiness: Phase 2 сохраняет model/asset references и их expected IDs/version, но renderer не является prerequisite для NPC runtime. До Phase 3 клиент отображает технический placeholder/diagnostic presentation state `PRESENTATION_NOT_READY`; server-side identity, behavior state и persistence продолжают работать.

**Артефакты:** NPC lifecycle/state diagram, Character Definition schema, ownership rules, relationship schema, save/load/delete transaction map и два-player isolation scenarios.

**Design gate:** NPC и её server state создаются/восстанавливаются независимо от renderer; два клиента видят один и тот же временный placeholder, пока Phase 3 renderer не подключён; профили двух игроков не смешиваются; удаление не затрагивает чужие данные; повтор create/delete не повторяет side effect. Полноценная модель не входит в критерий Phase 2.

#### Фаза 3 — 3D, визуальное управление и Motion Engine

**Проектируем:**
1. Runtime asset contract и rig descriptor: skeleton hierarchy, semantic bone map, skin weights, clips, morphs, visemes, spring chains, proxies, sockets и fallback.
2. Animation graph: base state machine, transitions, crossfade, body-part masks, additive layers и правила interrupt/blend.
3. Whole Body Controller: ownership body channels, priority arbitration, preemption, constraints и безопасное освобождение каналов.
4. Solver interface: Two-Bone IK, foot IK, gaze distribution, pole vectors, joint limits; FABRIK/CCD только там, где подтверждено тестом.
5. Procedural gait: phase, stride, speed, pelvis/weight transfer, arm counter-swing, starts/stops/turns и transition to authored clips. В Фазе 3 он получает только synthetic velocity/root-motion inputs из test harness; игровой pathfinding, follow target и фактическое автономное перемещение через мир появятся при интеграции Navigation в Фазе 8.
6. Face/rig capability: expression channels, eye/eyelid/brow/jaw, morph control и независимость от выбранного клипа. Проверяются контроллеры и тестовые позы; смысловое управление эмоциями ещё не подключается.
7. Задать технический интерфейс facial channels/viseme weights и таймлайна, чтобы renderer его поддерживал. Не реализовывать работающий lip-sync: источником viseme timing станет TTS/audio pipeline Фазы 9.
8. Secondary physics: solver step, substeps, chains, constraints, collision proxies, clamps, pause/unload/reset и performance budgets.
9. Pose replication: authoritative root/gameplay state, compact presentation payload, sequence, send rate, interpolation, missing snapshots и version mismatch.
10. Character Presentation Adapter: привязать renderer/rig/pose controller к уже существующим NPC UUID и server presentation snapshots; не создавать новую entity identity/lifecycle и не требовать renderer readiness для server NPC activation.
11. Performance/quality test suite по ADR-0006/0007 на одной и нескольких NPC.

**Артефакты:** rig capability schema, animation/layer graph, solver contracts, motion-task catalogue, Face Controller API, facial/viseme channel schema, Character Presentation Adapter contract, pose DTO, scripted test harness и renderer benchmark. Это технический исполнитель без интегрированного AI.

**Design gate:** через scripted test harness модель проходит idle, walk/run, start/stop, turn, look-at, reach, gesture, тестовые expressions, layer blending, secondary physics и two-client sync. Это подтверждает controllability визуального слоя, но НЕ подтверждает живое автономное поведение, осмысленную эмоциональную мимику или lip-sync. Они проверяются после подключения источников в Фазах 5, 7, 8 и 9. Неизвестные capabilities дают fallback, а не crash.

#### Фаза 4 — Content Packs

**Проектируем:**
1. Каталог пакетов, declarative Content Pack manifest/schema version, package ID/version, supported Agnor/Content Pack compatibility range и dependencies. Не смешивать эту версию с executable Addon API.
2. Scan → validate → stage → resolve → activate; отдельные enable/disable/remove и rollback transitions.
3. Priority/override resolution: порядок base pack, dependency pack, user pack и explicit override; детерминированное разрешение конфликтов ID/resources.
4. Package data model: character definitions, prompt templates, model/texture/animation resources, facial profiles, voice descriptors и declarative behavior settings. Все эти элементы — пассивные декларативные данные; loader не исполняет prompts, goals, actions или scripts.
5. Разделение pack data и host-only settings: credentials, endpoint secrets, private memory и world save данные запрещены.
6. Security: path traversal, archive expansion limits, file count/size/type/signature, checksum, manifest schema, dependency cycles.
7. Hot reload boundary: какие типы ресурсов можно перезагрузить без restart; какие требуют NPC quiescence/recreation; как invalid update откатывается.
8. Client availability/sync policy: allowed redistribution + admin opt-in, local-only assets, trusted source/manual install/refusal при mismatch.
9. Provenance/license metadata и capability fallback на каждом asset.
10. Consumer readiness/activation: model/assets/definitions могут активироваться после успешной pack validation; prompt templates лишь регистрируются как data source и начинают использоваться Main Dialogue только в Phase 5, gnome prompts/policies — в Phase 7, Bot planning data — в Phase 8, voice descriptors/TTS assets — в Phase 9. Неактивный consumer даёт явный NOT_YET_ENABLED, но не блокирует загрузку независимых ресурсов пакета.
11. Definition Source Adapter: validated pack definitions регистрируются через Phase 2 Character Definition Registry и его schema/version; pack loader не создаёт параллельную систему персонажей.

**Артефакты:** manifest/schema, precedence rules, package state machine, validation threat cases, hot-reload matrix, multiplayer transfer policy.

**Design gate:** новый тестовый персонаж подключается без изменения JAR; corrupt/incompatible pack не активируется; previous valid pack остаётся; restricted asset не передаётся и не публикуется.

#### Фаза 5 — Provider Gateway и живой диалог

**Проектируем:**
1. Provider-neutral chat request/response и adapter lifecycle; capability negotiation, local/cloud endpoint, TLS, auth, errors.
2. Provider Gateway foundation и Preset Registry: в этой фазе активен только Main Dialogue profile и соответствующий chat capability; preset slots могут иметь назначения для gnomes/embedding/Bot/TTS, но остаются NOT_YET_ENABLED до появления consumers (cognitive gnomes + Embedding Phase 7; Bot Phase 8; TTS Phase 9). Каждый request фиксирует snapshot version настроек, а отсутствие будущего preset не блокирует основной диалог.
3. Secret resolver и host-only storage; masked UI, rotation, redaction, export/import без credentials.
4. Dialogue lifecycle: request validation, idempotency, inbound persistence, context assembly, provider call, structured response validation, response commit, delivery/reconciliation.
5. System prompts, message roles и разделение trusted policy/untrusted conversation/lorebook data.
6. Context limit: последние N сообщений, default 10, настройки размера и deterministic order.
7. Streaming state: partial chunks не durable; финальная фиксация; interrupted response; безопасное восстановление доставки.
8. Retry semantics: различать transport retry и повтор inference; uncertain provider acceptance требует reconciliation.
9. Dialogue UI request state machine и поведение при server/provider timeout/disconnect.
10. Transactional downstream event outbox: каждая durable Conversation History write (accepted inbound message и finalized assistant response; streaming partial не включается) атомарно создаёт typed `HistoryMessageCommitted` event с message ID, direction/status, scope, conversation ID, sequence и correlation ID. После finalized assistant response создаётся `ConversationTurnFinalized` marker для Thinker trigger. Delivery state ведётся отдельно для каждой пары `event_id + consumer_id`: PENDING/CLAIMED/ENQUEUED/COMPLETED/FAILED, lease/attempt metadata, idempotency key, linked durable task ID и consumer watermark. В Phase 5 gnome consumers ещё disabled; records остаются pending. Phase 7 workers регистрируют consumers и claim/replay records независимо друг от друга; завершение Memorizer не подтверждает событие за Summarizer или Thinker. Consumer может пометить delivery ENQUEUED только атомарно с созданием/обновлением соответствующей durable Gnome Task или aggregation watermark; ack без сохранённой задачи запрещён. LLM worker status отслеживается отдельно от доставки event. Сигнал не отправляется в несуществующую очередь, а event не теряется между History commit и consumer activation.
11. Единственный Context Builder создаётся здесь с доступными источниками: Character Definition/system policy, Relationship/Runtime snapshot и finalized Conversation History. Будущие memory stores пока имеют статус NOT_YET_ENABLED, а не имитируют пустой успешный результат.
12. Context Source Registry: в Phase 6 новые memory stores подключаются адаптерами к тому же Context Builder без переписывания dialogue pipeline или второго сборщика контекста.
13. Context Builder поддерживает versioned Context Profiles, а не один одинаковый prompt для всех consumers: Phase 5 вводит dialogue profile; Phase 7 — thinker profile. Каждый profile задаёт строгий source allowlist, scope, freshness, priority и token budget. Gnome Input Assemblers используют тот же source registry, но только с фиксированными allowlists (Memorizer — только History + Thoughts; Judge — candidate + scoped Diary retrieval).

**Важное staging решение:** Фаза 5 реализует durable minimal Conversation History через общий Storage API — те же message IDs, scope и последовательность, которые будут использоваться далее. Фаза 6 расширяет этот же контракт до полной системы памяти. Не создавать временное второе хранилище истории и не переписывать диалог при добавлении шести stores.

**Связь с визуальным поведением:** после получения и фиксации диалогового события Dialogue Presentation Adapter передаёт Character Runtime ограниченные intents: turn/look_at на говорящего, listening/thinking/speaking state и, при наличии валидной поддерживаемой emotion/speech-act metadata, semantic expression request. Adapter не задаёт кости или произвольные morph weights. При отсутствии metadata используется нейтральная реакция. Это первая реальная реакция на собеседника, но ещё не автономная личность.

**Артефакты:** provider adapter contracts, capability matrix, preset schema, secret lifecycle, dialogue sequence/state machine, conversation message schema и failure cases.

**Design gate:** полный путь UI → server → provider → persisted result → addressed client; timeout не блокирует server thread; приватный контекст не виден другому игроку.

#### Фаза 6 — шесть хранилищ и persistence

**Проектируем:**
1. Финальную storage schema и миграционный путь от Phase 2 runtime saves и Phase 5 Conversation History.
2. Шесть store contracts, scope matrix, keys, indexes, pagination, retention и backup/restore.
3. Provenance/epistemic types: observed, reported, inferred, desired, planned; conflict и supersession lineage.
4. Transaction/outbox semantics для нескольких записей и watermark updates.
5. Backup schedule, consistency snapshot, restore to separate test world, corruption detection и recovery mode.
6. Retention: срок/условие для History, Action Memory, Thoughts, pending candidates, Diary и Summary; ничего не удалять без явной policy.
7. Embedding index lifecycle: provider/model/version, rebuild/reindex, stale vector handling и fallback. Сам adapter подключается в Phase 7, но storage interface планируется заранее.
8. Зафиксировать canonical message count для Conversation History/Summarizer: одна durable message record — одно сообщение; accepted inbound player message и finalized assistant reply считаются отдельно. Streaming partial, system prompt entries, transport retries и uncommitted interrupted partials не считаются. Default summary threshold — 10 records, диапазон определяется по message sequence/watermark.
9. Day/period abstraction для Daily Summary как open decision, который должен быть закрыт до реализации Summary в Phase 7.
10. В Phase 6 доступны exact/keyword/Always Lorebook activations; semantic-only retrieval явно остаётся NOT_YET_ENABLED до Embedding adapter Phase 7. Нельзя вернуть пустой semantic result и скрыть, что capability ещё не подключена.
11. Все stores создаются через Storage Facade, уже используемый Phase 2 и Phase 5. History переиспользует существующие message IDs/scope/sequence; отдельных БД или временных форматов не создавать.
12. Action Memory storage schema/API готовится здесь, но event-feed writer ещё не подключён; функциональность хранилища принимается только после producer Phase 7.
13. Thoughts schema/API готовится здесь, но runtime writer/reader cycle появляется только в совместной интеграции Phase 7.

**Артефакты:** ER/data model, schema versioning, migration scripts plan, backup/restore procedure, scope matrix, retention matrix и crash-recovery tests.

**Design gate:** все данные изолированы по world/NPC/player UUID; миграция и восстановление проверены; перезапуск не теряет сообщения/мысли/дневник; повреждение не приводит к silent reset.

#### Фаза 7 — когнитивные гномики и Gnome Task Scheduler

**Обязательный объём совместной реализации:**
1. Поднять **World Event Feed Foundation** — ограниченный server-side producer наблюдаемых world/NPC events: NPC lifecycle hooks, вход/выход и приближение игрока, наблюдаемые взаимодействия рядом с NPC и существенные изменения NPC. При активации он строит baseline snapshot текущего NPC/world state; события до включения feed не реконструируются задним числом и не выдумываются. После activation новые события записываются как typed world-event records в Action Memory. Содержимое диалоговых сообщений в Action Memory не копируется: оно остаётся в Conversation History и передаётся по отдельному dialogue outbox. Это не полный World Observer Фазы 8 и не исполняет actions.
2. Подключить replay Phase 5 transactional Conversation History outbox к Gnome Task Scheduler: accepted inbound/finalized assistant message events создают отдельные deliveries для Memorizer, Summarizer и Thinker. Для каждой пары event ID + consumer ID используется независимая delivery record, claim/lease, idempotency и watermark. Завершение одного consumer не подтверждает событие за других. Consumer ack атомарен с durable task/aggregation update; LLM execution status хранится отдельно.
   - **Memorizer:** объединяет contiguous unprocessed finalized History ranges в scoped task (History + Thoughts only), не создаёт LLM task на каждый streaming chunk или transport retry.
   - **Summarizer:** учитывает каждый committed History message record с уникальным message ID; запускает summary task только при достижении настроенного threshold (default 10 records), используя previous summary и точный range после watermark. User message и finalized NPC response считаются отдельными records; system prompts, streaming partials и retry attempts не считаются.
   - **Thinker:** получает trigger на `ConversationTurnFinalized` (после финального ответа NPC), значимые world/NPC events и собственный periodic schedule (~30s); burst triggers coalesce-ятся в одну pending task на NPC/scope. Не запускается на каждый message chunk.
   - **Judge:** не получает raw message event; получает только validated candidate delivery от Memorizer.
3. Реализовать Gnome Task Scheduler envelope, bounded queue partitions, per-gnome/per-provider concurrency, priorities, fairness, dedupe, retry, cancellation, checkpoint и metrics. Это планировщик фоновых worker tasks, не Goal Scheduler игровых целей.
4. Совместно подключить Memorizer и Thinker: Memorizer читает History + Thoughts, Thinker создаёт/обновляет Thoughts. Их contracts, watermark, ThoughtChanged trigger и stale-result reconciliation проверяются вместе; Memorizer нельзя принимать как законченную функцию до появления Thinker writer.
5. Подключить Judge к candidate pipeline и Diary transactionally. Thinker читает утверждённый Diary; поэтому цепочка Memorizer → Judge → Diary → Thinker принимается только как единая интеграция.
6. Подключить Summarizer к finalized Conversation History и доступным Event Feed/Action Memory events; Thinker получает предыдущую Summary через thinker Context Profile. Каноническая единица порога — одна durable History message record: accepted inbound от игрока и finalized assistant reply считаются отдельно; streaming partial/system prompt/retry не считаются. Default threshold 10 records, trigger создаётся только при переходе watermark через порог. SummaryUpdated не запускает бесконечный trigger loop.
7. Подключить независимые provider presets для активируемых когнитивных гномиков (Memorizer, Judge, Thinker, Summarizer) и Embedding adapter/profile (model/index version, reindex, fallback). Bot-specific preset/capability вводится вместе с runtime Bot в Phase 8.
8. Подключить Affective/Presentation Mapper: validated Thinker outputs (emotion/intensity/attention/desire) переводятся в разрешённые face/body intents с smoothing, hysteresis, expiry и rig-capability fallback.
9. Разделить cognitive goal proposals и presentation state: Thinker может сохранять желания/цели независимо от эмоций и не выдаёт world actions напрямую.
10. Ввести durable **Goal Proposal Inbox/Outbox** через Storage Facade: Thinker сохраняет proposals с ID, scope, evidence, priority hint, expiry и status=PENDING; запись атомарно связана с Thought/evidence references. Это outbox/task record, а не седьмое хранилище памяти. В Phase 7 proposals только валидируются и сохраняются; активировать игровые действия до Goal Scheduler Phase 8 запрещено. Phase 8 atomically claims proposals, проверяет актуальность и отмечает accepted/expired/rejected, не исполняя один proposal повторно.
11. Реализовать manual run/debug UI с permission check, dry-run/preview, task status, cancel/retry policy и sanitized diagnostics; ручной запуск не обходит validators.
12. **Bot в Phase 7 — только contract/schema design.** Пятый гномик не объявляется реализованным и не запускается до совместной Фазы 8 с Goal Scheduler/Action Executor/Navigation.

**Почему это одна интеграция:** Memorizer читает Thoughts, создаваемые Thinker; Judge формирует Diary, читаемый Thinker; Summarizer обновляет Summary, читаемую Thinker; World Event Feed наполняет Action Memory, читаемую Thinker и Summarizer; отдельный History outbox доставляет message events Memorizer/Summarizer/Thinker. Эти producer-потоки не сливаются: Dialogue History не дублируется в Action Memory. Приёмка относится к общему циклу четырёх когнитивных гномиков + Scheduler + World Event Feed + History outbox consumers, а не к разрозненным unit-ready сервисам.

**Артефакты:** scheduler state machine, world-event schema/adapters, per-consumer outbox delivery schema, gnome input/output schemas, provider assignment matrix, task retry matrix, goal proposal inbox contract, affect mapping, manual diagnostics UI flow и end-to-end memory/Thinker tests.

**Design gate:** проверяются оба независимых входных потока: World Event Feed → Action Memory → Thinker/Summarizer и Conversation History outbox → отдельные Memorizer/Summarizer/Thinker deliveries. Затем проверяется cognitive cycle Thoughts → Memorizer → Judge → Diary → Thinker и Summary ↔ Context Builder. Реплики не дублируются в Action Memory; один consumer не подтверждает event за другого; повторы не создают duplicates; watermark не теряет данные; stale results отбрасываются; private scopes сохраняются; proposals остаются pending и не вызывают world actions; один provider failure изолирован.

#### Фаза 8 — автономность: Bot, Goal Scheduler, World Observer и Action Runtime

**Обязательная совместная вертикальная реализация:**
1. Расширить Phase 7 Event Feed до полного World Observer event catalogue: typed actors/targets/outcomes/location/time/visibility, event importance, durability, dedupe/coalesce и server observation policy.
2. Реализовать отдельный Goal Scheduler и подключить его к durable Goal Proposal Inbox из Phase 7: проверить expiry/evidence/preconditions/priority, выбрать активную цель, управлять dependencies, preemption, retries и anti-loop.
3. Реализовать **Bot как пятого гномика**: преобразовать выбранную Goal в typed action plan; это runtime реализация schema, подготовленной в Phase 7. Подключить отдельный Bot provider preset и capability validation, если планирование использует LLM; deterministic plan executor не должен вызывать модель на каждый tick. Bot inference dispatch использует расширенный Gnome Task Scheduler, а не создаёт собственную очередь фоновых задач.
4. Совместно реализовать Action Registry, Action Validator и Action Executor: whitelist schemas, permission/range/cooldown/world preconditions, idempotency, cancellation, uncertain outcome reconciliation и audit.
5. Совместно реализовать Navigation/Follow: path, repath, stuck detection, obstacles, unloaded chunks, moving targets, stop/cancel, arrival predicate и фактический movement result.
6. Соединить Navigation velocity/root movement с Motion Engine gait Phase 3; подключить gesture/reach/look-at и multi-task arbitration к реальным action targets, а не synthetic inputs.
7. Подключить action outcomes и расширенные World Observer events к Action Memory и Thinker; обновлять состояние/цели после успеха, отказа или изменения мира.
8. Реализовать event/action feedback loop с correlation IDs, deduplication self-generated events и ограничением повторных действий.
9. Проверить multiplayer visibility/permissions и server authority для каждого действия.

**Почему это одна вертикальная группа:** Goal Scheduler выбирает цель для Bot; Bot требует Validator/Executor; Navigation и Motion Engine исполняют план; Observer фиксирует фактический outcome; Action Memory/Thinker обновляют состояние и создают следующие proposals. Нельзя выпускать эти компоненты как независимые готовые функции: обязательный результат — работающий замкнутый цикл с реальным изменением и проверкой world state.

**Артефакты:** event catalogue, goal/action schemas, whitelist matrix, Goal Proposal consumer, Bot runtime, executor state machine, navigation failure taxonomy, brain-to-motion orchestration graph, feedback-loop policy и combined-motion acceptance suite.

**Design gate:** Thinker proposal → Goal Scheduler → Bot → Action Validator/Executor → Navigation/Motion Engine → фактическое изменение/движение → World Observer → Action Memory/Thinker. Проверены препятствия, moving targets, permissions, отмена, stale proposal, duplicate events, side-effect retry, несколько клиентов и комбинации gaze/gesture/locomotion/emotion.

#### Фаза 9 — голос

**Проектируем:**
1. TTS adapter contract, voice profile per NPC, locale/format/capability checks и отдельные provider presets.
2. Audio task lifecycle: queued → generating → validated → ready → playing → interrupted/completed/failed.
3. Очередь аудио, приоритет, interrupt/replace policy, cancellation и синхронизация речи нескольких NPC.
4. Cache key, cache scope, invalidation при смене текста/голоса/провайдера, size limits и storage/retention.
5. Audio transport/recipient policy: адресный или tracking broadcast, permissions, bandwidth, packet loss и fallback.
6. Playback metadata: start timestamp, duration, voice/model ID и viseme timeline.
7. Face/Motion bridge: использование реального TTS/audio viseme timeline, fallback без timing и отдельное смешивание артикуляции с эмоциональной мимикой.
8. Failure isolation: TTS недоступен — текст уже сохранён и виден; очередь не блокирует chat.

**Артефакты:** audio state machine, cache/retention policy, network DTO, playback sync tests и viseme mapping.

**Design gate:** подключённые игроки слышат согласованное воспроизведение в выбранной policy; interrupted/overlapping audio корректно отменяется; без TTS диалог полностью работает.

#### Фаза 10 — полировка и первый самостоятельный релиз

**Проектируем:**
1. Release/compatibility matrix: Minecraft/Fabric/API, renderer, Sodium/Iris, server OS/runtime, supported provider capabilities и Content Pack versions.
2. Multiplayer regression suite: разные UUID, permissions, concurrent dialogues, memory isolation, NPC tracking и disconnect/reconnect.
3. Soak/long-session tests: queue saturation, provider rate limits, DB growth, cache cleanup, memory retention, world save/restart и migration.
4. Load profiles: NPC/player counts, server tick budget, renderer GPU/CPU, network bandwidth, API token/cost ceilings.
5. Recovery drills: backup restore, corrupted pack rollback, provider outage, server crash, interrupted tasks.
6. User-facing install/update/uninstall instructions, pack authoring guide, configuration/security docs и known limitations.
7. Public distribution audit: JAR contents, dependencies/licenses, Content Packs, secrets scan, no restricted temporary asset, reproducible release evidence.
8. CI evidence policy: checks/logs/summary by default; artifacts only for necessary human inspection, minimal content and short retention.

**Артефакты:** release checklist, compatibility matrix, regression/soak reports, install docs, known limitations, distribution audit and rollback plan.

**Design gate:** standalone companion работает без мира Агнор и Create; все critical user journeys проходят; долговременные сессии и восстановление проверены; публичный пакет не содержит restricted files/secrets.

#### Фаза 11 — перенос авторского мира и финальная полная версия

**Проектируем:**
1. World migration inventory: source world structures, character definitions, IDs, locations/dimensions, relationships, lore, assets, links and dependencies.
2. Mapping plan: как source entities/data становятся Minecraft world resources/NPC definitions/Content Pack entries; unresolved/lossy mappings помечаются, не угадываются.
3. Migration tool/process: read-only source inspection → manifest → dry run → staged conversion → validation → explicit approval → target import.
4. Referential integrity: stable IDs, duplicate resolution, missing assets, orphan records, coordinates/dimension mapping, rollback and repeatability.
5. World acceptance suite: ключевые локации, персонажи, отношения, история/лore и playable routes; источник никогда не перезаписывается.
6. Optional addon API: после стабилизации Action/Goal/Event contracts в Phase 8 и release hardening в Phase 10 заморозить versioned contracts для регистрации новых interaction/action/event/goal types. В Phase 1 допускается только внутренняя модульная граница, не преждевременная фиксация публичного API. Отдельный addon не модифицирует Core и отсутствует без последствий.
7. Create adapter: отдельная сборка; dependency/version detection; mapping Create blocks/events/actions; precondition/permission validation; отсутствие Create не меняет поведение Core. Совместимость не утверждать до теста конкретной Fabric build.
8. Expanded voice/interactions и final asset rights audit.
9. Только перед финальным full release после переноса мира заменить/удалить весь temporary restricted asset inventory; Phase 10 standalone companion release этим не блокируется.

**Артефакты:** migration inventory/mapping, dry-run and rollback report, addon API specification, Create compatibility matrix, final asset replacement audit.

**Design gate:** мир перенесён и проверен на копии; исходник сохранён; addon optional; все restricted temporary models заменены/удалены из final full distribution.

### 0.7 Ключевые зависимости между фазами

- Phase 0 выбирает доказанные runtime constraints (renderer, storage, server/network); Phase 1 строит единую поддерживаемую основу.
- Phase 1 создаёт lifecycle, config, network, permissions и общий Storage Facade; все последующие данные используют его.
- Phase 2 создаёт NPC Runtime, Character Definition Registry и runtime persistence. Phase 4 добавляет pack source к тому же Registry.
- Phase 3 строит renderer/Motion/Face execution layer и проверяет его только scripted harness; synthetic gait не требует Navigation.
- Phase 4 подключает external Content Pack loader к готовым Character Definition и runtime asset contracts.
- Phase 5 создаёт единственный Context Builder, durable Conversation History и transactional outbox для downstream message events. Gnome consumers ещё disabled; Phase 7 replay/consumes pending events. Context Builder начинает только с имеющихся sources; Phase 6 расширяет source registry.
- Phase 6 добавляет все шесть memory stores в общий Storage Facade; наличие store ещё не означает готовность его producer/consumer.
- Phase 7 совместно реализует Event Feed foundation, Scheduler и четыре когнитивных гномика. Event Feed наполняет Action Memory; Thinker/Thoughts и Memorizer работают как взаимозависимая пара; Judge/Diary и Summarizer/Summary включаются в тот же acceptance cycle.
- Phase 7 Thinker сохраняет goal proposals в durable inbox; они не выполняются без Goal Scheduler.
- Phase 8 одновременно реализует full World Observer, Goal Scheduler, пятого гномика Bot, Action Validator/Executor, Navigation и Motion Engine bridge. Это один замкнутый вертикальный пользовательский сценарий.
- Phase 3 задаёт Face Controller API; Phase 5 подключает реакции на диалог; Phase 7 — Thinker affect; Phase 8 — поведение при автономных действиях; Phase 9 — реальный TTS lip-sync.
- Phase 11 addon implementation допускается только после Action/Goal/Event API design in Phase 8 и freeze/compatibility gate Phase 10.

### 0.8 Реестр пробелов, выявленных сверкой

| ID | Выявленный пробел/неоднозначность | Исправление в Blueprint | Фаза закрытия |
|---|---|---|---|
| GAP-01 | Phase 0 spikes не имели общего измерительного протокола | Phase 0 artifacts и design gate | 0 |
| GAP-02 | Data root/config lifecycle был описан слишком общо | Config/catalogue design, path validation and apply lifecycle | 0–1 |
| GAP-03 | NPC create/delete/load/save state machine была неполной | Полный lifecycle и ownership design | 2 |
| GAP-04 | Animation graph, solver tests и viseme boundary не были разложены по этапам | Animation/layer contract и viseme handoff | 3, 9 |
| GAP-05 | Pack enable/disable, precedence, hot reload и rollback не выделялись | Package activation state machine и override policy | 4 |
| GAP-06 | Phase 5 history / Phase 6 full memory создавали риск двух хранилищ | Единственная эволюционирующая Conversation History через общий Storage API | 5–6 |
| GAP-07 | Bot смешивался с gnome pipeline до готовности Action Executor | Bot contract в Phase 7, полноценное исполнение в Phase 8 | 7–8 |
| GAP-08 | Manual gnome run/debug отсутствовал в функциональном контракте | Permissioned dry-run, status, cancel, sanitized diagnostics | 7 |
| GAP-09 | Backup/restore, retention и recovery были недостаточно конкретны | Storage backup/restore/retention design gate | 6, 10 |
| GAP-10 | Audio queue/cache/interruption/playback sync были описаны только общим TTS path | Audio lifecycle и cache/network contracts | 9 |
| GAP-11 | Long-session/release compatibility and distribution audit были слишком общими | Release matrix, soak tests, rollback and public audit | 10 |
| GAP-12 | Optional addon API не имел отдельного контракта в Blueprint | Новый раздел 27: extension/addon API | 1/8 design; 11 implementation |
| GAP-13 | Перенос авторского мира не имел безопасного migration workflow | Новый раздел 28: inventory, mapping, dry-run, validation, rollback | 11 |
| GAP-14 | Не были разделены техническая возможность мимики и её смысловые источники | Face Controller/test harness Фазы 3; dialogue reactions Фазы 5; Thinker affect Фазы 7; autonomous intents Фазы 8; TTS lip-sync Фазы 9 | 3/5/7/8/9 |
| GAP-15 | Phase 2 Character Definition и Phase 4 Pack definitions могли стать двумя схемами | Одна Character Definition schema/Registry в Phase 2; Pack Loader — source adapter Phase 4 | 2/4 |
| GAP-16 | Phase 5 Context Builder ссылался на шесть хранилищ, которых ещё нет | Minimal source set Phase 5; расширяемый Source Registry в Phase 6 | 5–6 |
| GAP-17 | Thinker Phase 7 потреблял Action Memory до появления Event Observer Phase 8 | Basic Event Feed producer вводится совместно с gnomes в Phase 7; full Observer/outcomes расширяются в Phase 8 | 7–8 |
| GAP-18 | Memorizer/Thinker/Judge/Summarizer имеют взаимные producer/consumer зависимости | Четыре когнитивных гномика + Scheduler + Event Feed принимаются как единая Phase 7 интеграция | 7 |
| GAP-19 | Thinker goal proposals не имели потребителя до Phase 8 | Durable Goal Proposal Inbox с PENDING/TTL в Phase 7; Goal Scheduler активирует их в Phase 8 | 7–8 |
| GAP-20 | Bot, Goal Scheduler, Action Executor, Navigation и Observer образуют цикл, но были описаны как отдельные задачи | Совместная вертикальная реализация Bot + Goal/Action/Navigation/Observer/Motion bridge в Phase 8 | 8 |
| GAP-21 | Публичный Addon API планировался до стабилизации Action/Goal/Event contracts | Внутренняя модульность Phase 1; API design Phase 8; freeze Phase 10; addon Phase 11 | 8–11 |
| GAP-22 | Character Runtime Phase 2 мог неявно требовать renderer, который продуктово подключается только в Phase 3 | Server NPC lifecycle/UUID/persistence не зависят от renderer; Phase 2 client placeholder, Phase 3 Character Presentation Adapter к тому же UUID | 2–3 |
| GAP-23 | Dialogue Phase 5 публиковал gnome events до активации consumers Phase 7 | Transactional durable outbox в Phase 5; Phase 7/8 claim/replay с idempotency и watermark | 5–8 |
| GAP-24 | Phase 2 создавал memory/task scopes до появления stores и schedulers | Только stable ownership scope keys в Phase 2; repositories Phase 6, workers Phase 7/8 | 2/6/7/8 |
| GAP-25 | Один общий outbox acknowledgement мог позволить первому gnome consumer подтвердить message event за остальные consumers | Независимая delivery record на пару event_id + consumer_id, с claim/lease, attempt, idempotency и собственным watermark; History outbox отделён от Action Memory world events | 5/7 |
| GAP-26 | Content Packs могли требовать prompt/behavior/voice consumers раньше соответствующих Provider/AI/TTS phases | Pack resources остаются пассивными data; prompts/behavior/voice settings разрешаются только при активации consumer в Phase 5/7/8/9 | 4–9 |
| GAP-27 | Provider Gateway Phase 5 мог требовать профили для ещё не созданных gnomes/Bot/TTS | Main Dialogue — единственное активное назначение Phase 5; остальные назначения активируются вместе с consumers в Phases 7/8/9 | 5–9 |
| GAP-28 | Phase 7 Event Feed мог неявно обещать реконструкцию world events до своей активации, хотя producer ещё не существовал | При activation формируется baseline текущего состояния; только последующие observed events пишутся в Action Memory, ретроспективные события не выдумываются | 7 |
| GAP-29 | Summarizer default каждые 10 сообщений не определял, считается ли сообщение репликой или парой; trigger мог расходиться между History/Outbox/UI | Одна durable History record = одно сообщение: inbound player и finalized assistant считаются отдельно; streaming/system/retry не считаются; threshold по sequence/watermark | 6–7 |

Этот реестр не означает, что соответствующие функции реализованы. Исправление состоит в том, что проектирование теперь привязано к нужной фазе и имеет проверяемый выход.


### 0.10 Результат аудита межфазных зависимостей

| Функция / контракт | Требуемый producer | Потребитель | Где готовится / где включается | Исправление зависимости |
|---|---|---|---|---|
| Character Definition | Built-in/dev definition source | Character Registry, NPC Factory, Pack Loader | Schema/Registry — Phase 2; external pack source — Phase 4 | Одна schema/Registry; packs не создают второй тип персонажа |
| NPC Runtime ↔ Renderer | Server-owned NPC UUID/state | Client Presentation Adapter/renderer | NPC state — Phase 2 with placeholder; adapter — Phase 3 | Server NPC activation/persistence never waits for client renderer/assets; the adapter binds to the same UUID without creating a second entity lifecycle |
| Storage / Persistence | Storage backend + Storage Facade | NPC State, Dialogue History, six memory stores | Backend spike — Phase 0; Facade — Phase 1; staged data — Phases 2/5/6 | Единый backend/API и последовательные migrations |
| Context Builder | Character Definition, Runtime/Relationship state, History; позже memory stores | Dialogue, Thinker | Minimal source set — Phase 5; source adapters — Phase 6/7 | Один Context Builder с расширяемым source registry; будущие источники помечаются NOT_YET_ENABLED, не выдают ложный пустой результат |
| Dialogue → gnome jobs | Accepted/finalized History message transactions | Memorizer, Summarizer, Thinker | Event outbox — Phase 5; per-consumer delivery records/replay — Phase 7 | Commit+event are atomic; each `event_id + consumer_id` is independently claimed/acknowledged; raw dialogue is not copied into Action Memory |
| World Event Feed → Action Memory | NPC lifecycle hooks + server-observed player/world events | Thinker, Summarizer, Context Builder | Basic producer — Phase 7; full World Observer/outcomes — Phase 8 | Typed world/action events only; message contents remain in Conversation History |
| Provider preset activation | Provider profile config + provider adapter capability | Dialogue / gnomes / Bot / Embedding / TTS | Main Dialogue active — Phase 5; cognitive gnomes + Embedding — Phase 7; Bot — Phase 8; TTS — Phase 9 | Gateway/registry common foundation can exist earlier; each destination becomes active only with its consumer, and missing future presets do not block Main Dialogue |
| Pack prompt/behavior data | Declarative pack metadata and validated Character Definitions | Dialogue / gnomes / Bot / TTS consumers | Registered as passive data — Phase 4; consumer activation — Phase 5/7/8/9 | Pack loading does not execute content; inactive consumer status is NOT_YET_ENABLED |
| Dialogue presentation | Finalized dialogue events + optional validated emotion/speech-act metadata | Face Controller / Motion Engine | Face API — Phase 3; dialogue adapter — Phase 5 | Не активировать reaction до Phase 5; без metadata — neutral fallback |
| Action Memory | Server-observed event feed | Thinker, Summarizer, Context Builder | Storage contract — Phase 6; basic producer — Phase 7; full event/outcome producers — Phase 8 | Producer вводится не позже consumer; Phase 7/8 расширяют один feed/store |
| Thoughts ↔ Memorizer | Thinker пишет Thoughts; Memorizer читает Thoughts | Memory pipeline / Judge / Context | Schema — Phase 6; writer+reader — jointly Phase 7 | Взаимозависимые worker contracts и acceptance в одной фазе |
| Diary ↔ Judge ↔ Thinker | Memorizer candidates → Judge-approved Diary; Thinker reads Diary | Context Builder / Thinker | Stores — Phase 6; all writers/readers — jointly Phase 7 | Judge и Thinker не объявляются независимо complete |
| Daily Summary ↔ Thinker | Finalized messages + event feed; Summarizer writes summary; Thinker reads it | Thinker / Context Builder | Messages — Phase 5; store — Phase 6; event/summary integration — Phase 7 | Summarizer and Thinker acceptance in same Phase 7 cycle |
| History message count → Summary trigger | Durable History message sequence | Summarizer threshold/watermark | Canonical unit chosen in Phase 6; threshold consumer active Phase 7 | One inbound user record and one finalized assistant record each count as one; streaming/system/retry records do not; default trigger every 10 committed records |
| Goal proposals | Thinker validated proposal | Goal Scheduler | Proposal inbox/outbox via Storage Facade — Phase 7; consumer/activation — Phase 8 | Atomic durable pending records bridge phases; not a seventh memory store; no world side effect until Phase 8 |
| Bot / Action Runtime | Goal Scheduler, action registry/validator, executor, navigation, Motion Engine | Bot plans and execution | Schema — Phase 7; full implementation — jointly Phase 8 | Bot is fifth gnome; implement simultaneously with Goal/Action/Navigation stack |
| World Observer | Runtime/server events and actual action outcomes | Action Memory, Thinker, Goal Scheduler | Basic Event Feed — Phase 7; full Observer — Phase 8 | Minimal producer is available before Thinker; full observer expands in Phase 8 |
| Navigation ↔ gait | Path/target/velocity and locomotion outcomes | Motion Engine locomotion | Synthetic root/velocity test — Phase 3; real world navigation bridge — Phase 8 | Gait tested earlier; actual path-following only in joint Phase 8 |
| TTS ↔ face ↔ audio | Finalized dialogue, TTS, playback clock, viseme timing | Face Controller and client audio | Face API — Phase 3; provider foundation — Phase 5; end-to-end — Phase 9 | Audio generation, playback synchronization and lip-sync accepted together |
| Gnome Task Scheduler vs Goal Scheduler | Gnome Task Scheduler управляет background worker jobs; Goal Scheduler управляет игровыми целями и Action lifecycle | Gnomes / Bot / Action Runtime | Gnome Task Scheduler — Phase 7; Goal Scheduler — Phase 8 | Разные bounded services; Goal proposals передаются через durable inbox, не через shared in-memory queue |
| Optional addon API | Stable Action/Goal/Event extension points | External addon modules | Internal modularity — Phase 1; semantic API design — Phase 8; freeze — Phase 10; addon use — Phase 11 | Do not freeze public extension API before real Action/Goal/Event contracts exist |

### 0.11 Правило контрактов, реализации и активации

Для любой функции отдельно фиксируются три состояния:

1. **Contract ready:** описаны интерфейс, schema, ownership, scope, threading, errors and compatibility. Это позволяет проектировать зависимые компоненты, но не выдаёт заглушку за рабочую функцию.
2. **Implementation ready:** producer и consumer существуют, но могут ещё не быть соединены в end-to-end workflow.
3. **Runtime integrated / Verified:** вся цепочка соединена, включена, передаёт/сохраняет состояние и прошла user-path/failure tests.

Если обязательный producer/consumer появляется только в более поздней фазе, ранняя функция может оставить durable pending item/outbox с TTL, scope и idempotency (например, Goal Proposal Inbox Phase 7), работать явно в reduced mode с состоянием NOT_YET_ENABLED (например, Context Builder Phase 5) или оставаться disabled до совместной фазы. Нельзя silently подменять отсутствующий источник пустым успешным ответом, имитировать AI/мир тестовыми данными в production или заявлять функцию законченной.

Если два компонента имеют взаимный runtime цикл, они проектируются заранее, но принимаются только как совместная вертикальная реализация в одной фазе. В Agnor это обязательно для:
- четырёх когнитивных гномиков + Scheduler + Event Feed в Phase 7;
- Bot + Goal Scheduler + full World Observer + Action Validator/Executor + Navigation + Motion bridge в Phase 8;
- TTS generation + audio playback timing + viseme/lip-sync в Phase 9.

Unit/contract tests отдельных компонентов полезны, но не заменяют совместный end-to-end acceptance.

### 0.12 Принципиальная граница: исполнитель движения не является мозгом персонажа

Agnor Motion Engine отвечает за то, как выполнить уже принятое намерение. Он не определяет самостоятельно, что персонаж хочет, почему он это хочет и какую эмоцию должен испытывать. Источники намерений подключаются поэтапно:

- До диалога и гномиков (Фазы 0–4) Motion Engine и Face Controller проверяются только технически: scripted/synthetic test harness подаёт заранее заданные задачи и состояния. Это не поведение живого NPC и не релизная автономность.
- После диалога (Фаза 5) появляются первые реакции на реального собеседника: повернуться, смотреть, слушать, говорить и показывать ограниченную валидированную conversational expression.
- После гномиков (Фаза 7) Thinker предоставляет структурированное внутреннее состояние, эмоции и внимание, а Presentation Mapper превращает их в выражение лица и микродвижения. Это даёт непрерывное выразительное состояние, но ещё не полную самостоятельную навигацию.
- После Event Observer, Bot, Goal Scheduler и Navigation (Фаза 8) намерения связываются с реальными событиями и действиями мира. Здесь появляется полноценное автономное движение: подойти, следовать, остановиться, повернуться, взаимодействовать, прерываться и выбирать новое поведение.
- После TTS (Фаза 9) подключается синхронизация губ по реальному audio/viseme timeline.

Нельзя объявлять живое поведение, эмоциональную мимику или lip-sync готовыми после Фазы 3. Фаза 3 создаёт исполнительную базу и тестирует её управляемость. Поведенческая интеграция приходит только вместе с источниками решений соответствующих фаз.
