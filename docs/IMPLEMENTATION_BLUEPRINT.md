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
| 1. Каркас | Lifecycle, common/server/client, config/catalogs, protocol/versioning, permissions, settings UI, safe logging | 1–3, 15–16, 18–20, 24 | **Частично.** Границы и network security есть; нужно конкретизировать config/data roots, config validation/apply, minimal UI command lifecycle и logging policy |
| 2. Character Runtime | NPC UUID/definition, create/delete/load, runtime state, relationships per player UUID, world/NPC/player saves, two-player isolation | 3–4, 7, 18, 21, 25 | **Частично.** Основные объекты есть; нужно определить lifecycle state machine, ownership, deletion semantics и согласование world save/load |
| 3. 3D и visual control | Import готовой модели, retarget/repair, animation state machine, crossfade/masks/additive, Motion Engine, IK, gait, facial control, secondary physics, pose sync | 13–14, 21.4, 23, 25 | **Частично.** Pipeline есть; требуется подробная спецификация animation graph/layer precedence, конкретных solver tests, gait measurements и связи viseme contract с Фазой 9 |
| 4. Content Packs | Manifest, scan/enable/disable, assets/definitions/prompts, provenance/license, capability report/fallback, priority overrides, checksum/version, safe client policy | 14–15, 19–20 | **Частично.** Validation/sync есть; не были выделены package activation lifecycle, override precedence, enable/disable/rollback и hot-reload boundary |
| 5. Provider Gateway/dialogue | LLM adapters, independent presets, local/cloud endpoints, timeouts/retry/streaming, last N messages, system prompts/roles, host secrets, dialogue UI | 5–6, 10, 15–16, 24 | **Частично.** API flow есть; нужно явно задать staged durable History: базовые последние N сообщений появляются в Фазе 5, затем расширяются в Фазе 6 без второго конкурирующего хранилища |
| 6. Шесть хранилищ | Все 6 stores, SQLite/JSON choice, migrations/backups, world/NPC/player isolation, indexes/retention | 7, 19–20, 22, 25 | **Частично.** Scope/transactions/migrations описаны; надо добавить backup/restore/retention lifecycle и переход от Phase 2/5 data без потери данных |
| 7. Гномики и scheduler | Memorizer, Embedding, Judge, Thinker, Summarizer, queues/retries/metrics, manual run/debug UI | 8–10, 16, 19–20, 25 | **Частично.** Контракты пяти гномиков есть; нужно определить, что Bot в Фазе 7 получает только контракт/скелет, а полноценные world actions появляются в Фазе 8; добавить ручное управление/диагностику |
| 8. Автономность | Observer, Goal Scheduler, navigation/follow, whitelist interactions, reactions, simultaneous motion, interruption and safety | 4, 11–13, 21.3–21.4, 25 | **В основном покрыто.** До реализации закрыть action state machine, nav/stuck policy, idempotency side effects и motion conflict acceptance scenarios |
| 9. Голос | TTS adapters/presets, per-NPC voice, audio queue/interruption/cache, client playback, mouth sync, graceful degradation | 10, 13, 15–17, 21.6 | **Частично.** Основной TTS path есть; отсутствовали детальные queue/cache/interruption/playback sync contracts |
| 10. Полировка и первый релиз | Multiplayer regression, load, long sessions/restart, errors/migrations, API cost/performance settings, install/pack docs, compatibility matrix | 19–25 | **Частично.** Test taxonomy есть; нужно формализовать release matrix, soak tests, packaging/update/rollback и user documentation gate |
| 11. Мир и финальная версия | World transfer, character packs, addon integrations/Create, expanded voice/interactions, final replacement of temporary assets | 14, 21, 26, 27–28 | **Есть пробел.** Blueprint не содержал отдельного migration workflow авторского мира и полного optional addon API contract; они добавлены в разделы 27–28 |

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
| ADR-0004 Memory pipeline | 6–9, 10, 20, 22; Phase 5–7 | Шесть stores и гномики сопоставлены; schema/index/retention/day-boundary остаются Open до фаз внедрения |
| ADR-0005 Optional integrations | 27; Phase 1 design, Phase 11 addon | Ранее отсутствовавший отдельный контракт добавлен; реальная совместимость Create не заявляется |
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
5. Motion spike: semantic task contract, look-at/reach/turn, Two-Bone IK, сравнение FABRIK/CCD где применимо, whole-body priority approach, gait и spring-chain.
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

**Артефакты:** lifecycle diagram, config schema, каталог путей, packet contract, минимальная permission matrix, UI request state machine, logging/redaction rules.

**Design gate:** dedicated server не загружает client-only classes; ни один запрос UI не меняет state до серверной проверки; конфигурация переживает перезапуск и не применяется частично.

#### Фаза 2 — Character Runtime и первая NPC

**Проектируем:**
1. NPC identity: UUID, Character Definition ID/version, world association, ownership/creator и правила уникальности.
2. Runtime state machine: Constructing → Active → Suspended/Unloaded → Removing → Removed/Failed; допустимые переходы и восстановление.
3. Creation flow: permission → definition validation → resource checks → factory → initial persistence → spawn snapshot → idempotent acknowledgement.
4. Delete flow: удаление entity, сохранение/удаление памяти, подтверждение пользователя, каскадные зависимости и восстановление после сбоя.
5. Relationship State: ключ NPC UUID + Player UUID; ник только display; lifecycle игрока и правила удаления/retention.
6. World/NPC/player persistence: границы save data, atomic writes, load ordering, world isolation и backup strategy.
7. Concurrency: единственный владелец runtime state конкретного NPC; stale callback после unload не применяет старые изменения.

**Артефакты:** NPC lifecycle/state diagram, Character Definition schema, ownership rules, relationship schema, save/load/delete transaction map и два-player isolation scenarios.

**Design gate:** NPC и состояние восстанавливаются; профили двух игроков не смешиваются; удаление не приводит к случайному удалению общих данных; повтор команды создания/удаления не выполняет side effect дважды.

#### Фаза 3 — 3D, визуальное управление и Motion Engine

**Проектируем:**
1. Runtime asset contract и rig descriptor: skeleton hierarchy, semantic bone map, skin weights, clips, morphs, visemes, spring chains, proxies, sockets и fallback.
2. Animation graph: base state machine, transitions, crossfade, body-part masks, additive layers и правила interrupt/blend.
3. Whole Body Controller: ownership body channels, priority arbitration, preemption, constraints и безопасное освобождение каналов.
4. Solver interface: Two-Bone IK, foot IK, gaze distribution, pole vectors, joint limits; FABRIK/CCD только там, где подтверждено тестом.
5. Procedural gait: phase, stride, speed, pelvis/weight transfer, arm counter-swing, starts/stops/turns и transition to authored clips.
6. Facial pipeline: expression channels, eye/eyelid/brow/jaw, morph control и независимость от выбранного клипа.
7. Viseme contract: определить входную временную шкалу артикуляции уже в Фазе 3, но источник TTS/audio timing подключить в Фазе 9.
8. Secondary physics: solver step, substeps, chains, constraints, collision proxies, clamps, pause/unload/reset и performance budgets.
9. Pose replication: authoritative root/gameplay state, compact presentation payload, sequence, send rate, interpolation, missing snapshots и version mismatch.
10. Performance/quality test suite по ADR-0006/0007 на одной и нескольких NPC.

**Артефакты:** rig capability schema, animation/layer graph, solver contracts, motion-task catalogue, facial/viseme interface, pose DTO и renderer benchmark.

**Design gate:** модель не просто отображается — она проходит полный профиль idle, walk/run, start/stop, turn, look-at, reach, gesture, expressions, layer blending, secondary physics и two-client sync. Неизвестные capabilities дают fallback, а не crash.

#### Фаза 4 — Content Packs

**Проектируем:**
1. Каталог пакетов, manifest schema, package ID/version, supported Agnor API version и dependencies.
2. Scan → validate → stage → resolve → activate; отдельные enable/disable/remove и rollback transitions.
3. Priority/override resolution: порядок base pack, dependency pack, user pack и explicit override; детерминированное разрешение конфликтов ID/resources.
4. Package data model: character definitions, prompt templates, model/texture/animation resources, facial profiles и declarative behavior settings.
5. Разделение pack data и host-only settings: credentials, endpoint secrets, private memory и world save данные запрещены.
6. Security: path traversal, archive expansion limits, file count/size/type/signature, checksum, manifest schema, dependency cycles.
7. Hot reload boundary: какие типы ресурсов можно перезагрузить без restart; какие требуют NPC quiescence/recreation; как invalid update откатывается.
8. Client availability/sync policy: allowed redistribution + admin opt-in, local-only assets, trusted source/manual install/refusal при mismatch.
9. Provenance/license metadata и capability fallback на каждом asset.

**Артефакты:** manifest/schema, precedence rules, package state machine, validation threat cases, hot-reload matrix, multiplayer transfer policy.

**Design gate:** новый тестовый персонаж подключается без изменения JAR; corrupt/incompatible pack не активируется; previous valid pack остаётся; restricted asset не передаётся и не публикуется.

#### Фаза 5 — Provider Gateway и живой диалог

**Проектируем:**
1. Provider-neutral chat request/response и adapter lifecycle; capability negotiation, local/cloud endpoint, TLS, auth, errors.
2. Preset resolution: main dialogue profile независимо от gnome/embedding/TTS; snapshot version конфигурации на каждый request.
3. Secret resolver и host-only storage; masked UI, rotation, redaction, export/import без credentials.
4. Dialogue lifecycle: request validation, idempotency, inbound persistence, context assembly, provider call, structured response validation, response commit, delivery/reconciliation.
5. System prompts, message roles и разделение trusted policy/untrusted conversation/lorebook data.
6. Context limit: последние N сообщений, default 10, настройки размера и deterministic order.
7. Streaming state: partial chunks не durable; финальная фиксация; interrupted response; безопасное восстановление доставки.
8. Retry semantics: различать transport retry и повтор inference; uncertain provider acceptance требует reconciliation.
9. Dialogue UI request state machine и поведение при server/provider timeout/disconnect.

**Важное staging решение:** Фаза 5 реализует durable minimal Conversation History через общий Storage API — те же message IDs, scope и последовательность, которые будут использоваться далее. Фаза 6 расширяет этот же контракт до полной системы памяти. Не создавать временное второе хранилище истории и не переписывать диалог при добавлении шести stores.

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
8. Day/period abstraction для Daily Summary как open decision, который должен быть закрыт до реализации Summary в Phase 7.

**Артефакты:** ER/data model, schema versioning, migration scripts plan, backup/restore procedure, scope matrix, retention matrix и crash-recovery tests.

**Design gate:** все данные изолированы по world/NPC/player UUID; миграция и восстановление проверены; перезапуск не теряет сообщения/мысли/дневник; повреждение не приводит к silent reset.

#### Фаза 7 — гномики и Gnome Scheduler

**Проектируем и реализуем по зависимостям:**
1. Scheduler envelope, queue partitions, per-gnome/per-provider concurrency, priorities, fairness, dedupe, retry, cancellation, checkpoint и metrics.
2. Memorizer: только History + Thoughts; candidates с provenance и epistemic type; watermark и передача Judge.
3. Embedding adapter/profile: версии моделей/индекса, similarity retrieval, rebuild, fallback.
4. Judge: scoped candidate retrieval, duplicate/conflict logic, ACCEPT/MERGE/SPLIT/REJECT/DEFER и transactional lineage.
5. Thinker: target 30 seconds + event triggers, context snapshot, stale-result rejection, structured Thoughts и goal proposals.
6. Summarizer: каждые 10 сообщений; previous summary + new range; watermark, idempotency, day boundary и no data loss.
7. Независимые provider presets для каждого gnome; ошибка одного не блокирует другие.
8. Manual run/debug UI: permission check, dry-run/preview, task status, cancel/retry policy, sanitized diagnostics; ручной запуск не обходит validators.
9. **Bot в этой фазе:** фиксируется service contract, typed plan schema и связь с Goal Scheduler/Motion Engine; полный action execution не реализуется раньше Фазы 8, чтобы не смешивать memory pipeline с world mutation.

**Артефакты:** scheduler state machine, gnome input/output schemas, provider assignment matrix, task retry matrix, manual diagnostics UI flow и end-to-end memory tests.

**Design gate:** Memorizer не обходит Judge; повтор не создаёт duplicates; Thinker не вызывает actions напрямую; stale results отбрасываются; один provider failure изолирован.

#### Фаза 8 — автономное поведение и действия

**Проектируем:**
1. World Observer event catalogue: event type, actor/target, relevance, visibility, durability, dedupe/coalesce.
2. Goal Scheduler policy: priority, dependencies, preemption cost, expiry, success/failure and anti-loop rules.
3. Bot action planner и whitelist registry; schema, permission, range, cooldown, world preconditions и capability checks.
4. Action Executor lifecycle, idempotency class, uncertain outcome reconciliation и audit record.
5. Navigation: path, follow target, repath, stuck detection, obstacles, unloaded chunks, stop/cancel and arrival predicate.
6. Motion task bridge: walk_to, look_at, reach, wave etc.; coordination and interruption across simultaneous tasks.
7. Event feedback: действие NPC → observed outcome → Action Memory/Thinker; предотвращение бесконечного self-trigger loop.
8. Multiplayer visibility and permission policy for autonomous actions.

**Артефакты:** event catalogue, goal/action schemas, whitelist matrix, state transitions, navigation failure taxonomy и combined-motion acceptance suite.

**Design gate:** NPC самостоятельно выполняет разрешённые задачи; препятствие/отмена/изменение цели корректно обрабатывается; ни одна модель не исполняет произвольную команду; фактический outcome подтверждён миром.

#### Фаза 9 — голос

**Проектируем:**
1. TTS adapter contract, voice profile per NPC, locale/format/capability checks и отдельные provider presets.
2. Audio task lifecycle: queued → generating → validated → ready → playing → interrupted/completed/failed.
3. Очередь аудио, приоритет, interrupt/replace policy, cancellation и синхронизация речи нескольких NPC.
4. Cache key, cache scope, invalidation при смене текста/голоса/провайдера, size limits и storage/retention.
5. Audio transport/recipient policy: адресный или tracking broadcast, permissions, bandwidth, packet loss и fallback.
6. Playback metadata: start timestamp, duration, voice/model ID и viseme timeline.
7. Face/Motion bridge: использование viseme timeline, fallback без timing, отсутствие конфликта с expression/lip-sync layers.
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
6. Optional addon API: versioned contracts и compatibility policy для регистрации новых interaction/action/event/goal types; отдельный addon не модифицирует Core и отсутствует без последствий.
7. Create adapter: отдельная сборка; dependency/version detection; mapping Create blocks/events/actions; precondition/permission validation; отсутствие Create не меняет поведение Core. Совместимость не утверждать до теста конкретной Fabric build.
8. Expanded voice/interactions и final asset rights audit.
9. Только перед финальным full release после переноса мира заменить/удалить весь temporary restricted asset inventory; Phase 10 standalone companion release этим не блокируется.

**Артефакты:** migration inventory/mapping, dry-run and rollback report, addon API specification, Create compatibility matrix, final asset replacement audit.

**Design gate:** мир перенесён и проверен на копии; исходник сохранён; addon optional; все restricted temporary models заменены/удалены из final full distribution.

### 0.7 Ключевые зависимости между фазами

- Phase 0 выбирает доказанные technical constraints; Phase 1 превращает минимальный spike в поддерживаемую структуру мода.
- Phase 1 даёт lifecycle, config, network, permissions и Storage/Executor boundaries для всех последующих функций.
- Phase 2 создаёт единый Character Runtime и базовую persistence-модель.
- Phase 3 использует локальный test asset; formal Content Pack lifecycle появляется в Phase 4. Не делать Phase 3 зависимой от ещё не существующего pack manager.
- Phase 4 поставляет character definitions/prompts/assets; host secrets и private memory остаются вне пакетов.
- Phase 5 создаёт первую durable Conversation History через общий storage contract; Phase 6 расширяет её, а не заменяет.
- Phase 6 завершает storage contracts до запуска фоновых gnomes в Phase 7.
- Phase 7 фиксирует Bot API/plan schema; Phase 8 включает реальное выполнение действий.
- Phase 3 формирует facial/viseme interface; Phase 9 подключает к нему TTS timing.
- Phase 10 стабилизирует standalone companion; Phase 11 переносит authored world и добавляет optional integrations.

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
| GAP-14 | Не были явным образом связаны facial rig Фазы 3 и TTS Фазы 9 | Общий viseme timeline contract и staged provider integration | 3/9 |

Этот реестр не означает, что соответствующие функции реализованы. Исправление состоит в том, что проектирование теперь привязано к нужной фазе и имеет проверяемый выход.

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

## 27. Optional Integration / Addon API

### 27.1 Граница Core и addon

Agnor Core публикует versioned extension API, а addon является отдельным Fabric-модом с собственной зависимостью и версионной совместимостью. Core не импортирует классы Create или иных необязательных модов напрямую. Отсутствующий, отключённый или несовместимый addon не влияет на запуск и работу базового companion-мода.

### 27.2 Контракт расширения

API должен позволять addon:
- объявить собственный addon ID, версию и диапазон поддерживаемых Agnor API versions;
- зарегистрировать типизированные world events, target descriptors, action definitions и goal/interaction handlers;
- указать необходимые capabilities/dependencies;
- предоставить validator/precondition checks, execution adapter, completion/failure reason mapping и audit metadata;
- подписываться только на явно разрешённые события через lifecycle-managed subscriptions;
- безопасно unregister/dispose при остановке мира/сервера.

Addon не может подменять базовый Character Runtime, обходить Permission Service, напрямую читать private memory/credentials или исполнять неограниченный код из Content Pack. Любое действие addon проходит тот же серверный validation/execution lifecycle, что и встроенное действие.

### 27.3 Регистрация и отказ

При startup Core обнаруживает зарегистрированные addons, проверяет API version, dependency presence, duplicate IDs и declared capabilities. Несовместимый addon переводится в disabled state с reason code; Core продолжает работу. Регистрация должна быть атомарной: частично зарегистрированный набор actions/events не остаётся активным. События addon получают source/addon ID и provenance.

### 27.4 Создание и выпуск addon

До первого addon должны быть опубликованы:
- стабильные интерфейсы и versioning policy;
- lifecycle/threading contract;
- permissions/security contract;
- compatibility test harness;
- migration/deprecation policy.

Create adapter реализуется отдельно и проверяется только против конкретной доступной Fabric/Minecraft/Create matrix. Наличие API не доказывает совместимость Create. Это соответствует ADR-0005; до Phase 11 addon не является зависимостью standalone release.

## 28. Перенос авторского мира

### 28.1 Принцип

Перенос мира — отдельная data/content migration, а не импорт файлов в активный runtime. Исходный мир никогда не перезаписывается. Любая операция выполняется на копии и имеет inventory, mapping, dry-run, validation, approval и rollback.

### 28.2 Inventory и mapping

Перед преобразованием формируется manifest исходных сущностей и связей:
- персонажи, stable IDs, имена и варианты;
- локации, координаты, dimensions/regions и пространственные связи;
- Character Definitions, personality/lore/prompt references;
- отношения, события, timeline и допустимые memory records;
- модели, текстуры, анимации, звуки и provenance/license;
- связи персонажей с локациями/объектами/сюжетными элементами;
- внешние зависимости и элементы, которые невозможно перенести напрямую.

Каждый исходный тип получает одно из решений: direct mapping, transform, manual review, unsupported/omit. Потеря информации и неоднозначные соответствия не скрываются; они попадают в migration report.

### 28.3 Conversion workflow

1. Создать read-only source snapshot и checksum.
2. Построить inventory/manifest без изменения source.
3. Сопоставить source IDs с Agnor/Minecraft IDs; создать deterministic mapping file.
4. Выполнить dry-run и показать missing assets, collisions, unsupported fields, dimension/coordinate issues и потенциальные потери.
5. Подготовить staging world/content pack в отдельном каталоге.
6. Преобразовать data/definitions/assets с сохранением source provenance и references.
7. Проверить referential integrity, assets/capabilities, world load, NPC spawn, relationships и ключевые сценарии.
8. Получить явное approval на promotion staging result в target world.
9. Сохранить migration report, source/target version, mapping, checksums и rollback instructions.

Повторный запуск с тем же source snapshot и mapping должен быть детерминированным либо явно остановиться на конфликте. Частичный импорт не считается успешным завершением.

### 28.4 Проверки и release gate

Тестировать на отдельной копии мира:
- ключевые локации и dimension mapping;
- персонажей, уникальность ID и связи;
- характеры/lore/prompt references;
- assets, rig capability и замену временных моделей;
- missing dependency и duplicate collision;
- повторный запуск, rollback и восстановление после сбоя.

Личные данные, секреты и приватные дампы не включаются в публичные migration artifacts. Final asset rights audit проводится после переноса и до финальной полной публикации. Политика ADR-0008 сохраняется: Phase 10 не блокируется локальными временными моделями; cleanup обязателен перед финальным full release после Phase 11.

## 29. Дальнейшая детализация и Definition of Blueprint Complete

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