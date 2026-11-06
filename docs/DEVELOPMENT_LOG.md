P5.2 Verified 2026-11-06: [отчёт010](reports/2026-11-06-010-lore-context.md), [ADR0026](decisions/ADR-0026-lore-context-admission.md). Scoped ContextSourceRegistry/пять statuses/budget/source IDs,110tests PASS+3skipped. Real Ollama: A private COBALT-732/B pack PACK-AMBER, nonmatch и privacy PASS. Следующая P5.3; полная P5 не завершена.

P5.1 Verified 2026-11-06: [отчёт009](reports/2026-11-06-009-lorebook-crud.md), [ADR0025](decisions/ADR-0025-lorebook-storage-and-sources.md), [команды Lorebook](MEMORY.md). Schema4/CRUD/scopes/activation/pack-local overrides,107tests PASS+3skipped, dedicated2clients PASS. Следующая P5.2, вся P5 ещё не завершена.

**Актуально 2026-11-06: P4 Verified.** P4.1→P4.2→P4.3 завершены последовательно; [итог008](reports/2026-11-06-008-phase4-acceptance.md), [формат и команды](CONTENT_PACKS.md), [UI/sync007](reports/2026-11-06-007-pack-controls-sync.md), ADR0022–0024.102tests PASS/3opt-in skipped; dedicated2clients/restart/integrated/real Ollama PASS. Restricted payload0. Следующая P5 не начата. Нижние записи с прежними статусами — история своих срезов.

P4.2 Verified 2026-11-06: [отчёт006](reports/2026-11-06-006-pack-activation.md), [ADR-0023](decisions/ADR-0023-content-generations-and-presentation.md). Atomic Registry/last-known-good, same UUID/external Firefly, dedicated+restart+integrated PASS;97tests PASS/3skipped. Следующая P4.3, вся P4 ещё не завершена.

P4.1 Verified 2026-11-06: [отчёт005](reports/2026-11-06-005-pack-ingestion.md), strict directory/ZIP loader и manifest v1, ADR-0022; 91 tests PASS +3 opt-in skipped. P4.2 следующая, P4 ещё не завершена.

# Журнал разработки AGNOR

Журнал содержит хронологический указатель на отдельные отчёты. Полное описание каждой работы хранится в `docs/reports/`; архитектурные решения — в отдельных ADR. Новые записи добавляются сверху.

## Текущее состояние на 2026-11-06

**P3 Verified,2026-11-06:** P3.1–P3.5 последовательно завершены в документированных границах. [Итоговая приёмка и бюджеты](reports/2026-11-06-004-phase3-acceptance.md), [pose sync/dialogue/compat](reports/2026-11-06-003-pose-sync-dialogue.md), [визуальные ограничения](assets/FIREFLY_VISUAL_LIMITS.md). Dedicated2clients и integrated, реальный dialogue/retry, reconnect/mismatch/stale, selected Sodium/Iris/Complementary PASS;83tests PASS/3opt-in skipped. Локальный dev rig adapter до P4; full cloth/MToon/voice/navigation не заявлены. Следующая фаза P4 не начата.

[Miyabi: результаты поиска](assets/MIYABI_MODEL_SEARCH.md). Изменения рабочей копии без commit/push; старые записи ниже отражают состояние на свою дату.

## Записи

2026-11-06 — **Done P3.5 / Verified P3:** [pose sync/dialogue](reports/2026-11-06-003-pose-sync-dialogue.md), [итоговый бюджетный gate004](reports/2026-11-06-004-phase3-acceptance.md), ADR0021. Same-UUID host producer, bounded S2C keyframes и client interpolation/fallback, trusted public dialogue cues; dedicated2clients/integrated/Ollama/retry/reconnect/hash/stale PASS. Selected Sodium0.6.13/Iris1.8.8/Complementary r5.5.1 native shader PASS после dev dependency/remap fix. Контрольный multi479shared frames,0whole-NPC failures,15chain-local resets;5NPC host p95=7.812ms,payload116.79KiB/s/viewer. Build83PASS/3skipped; JAR/source boundaries audit PASS. Full cloth/MToon/voice/navigation не заявлены; P4 не начата. Failed attempts/evidence сохранены.

2026-11-06 — **Done P3.3(a–d)**: [host pose/face pipeline и recovery](reports/2026-11-06-001-pose-face-pipeline.md), ADR-0019. Immutable rig/TRS/clips/mixer, analytic two-bone IK, bounded semantic tasks/priority/expiry/cancel, flat foot/look/reach, независимые face lanes и gaze. Последовательно приняты controlled сцены, затем body/face одной Firefly. Исправлены blink bindings, target orientation, stale fixture guard; неудачные attempts сохранены. Blender original pose/активная сцена восстановлены, packed QA checkpoint сохранён локально. Build/57tests PASS,3skipped. P3.4 не начата.

2026-11-05 — **Done P3.2**: [модель и renderer adapter](reports/2026-11-05-014-npc-model-adapter.md). Миграция toolchain, отдельный entity type без второго persistence writer, семь lossless palette parts, локальные textures и restricted register. Dedicated/client/reload/restart/fallback подтверждены реальными кадрами и UUID. Ошибки image resource binding и overlay исправлены, неудачные кадры сохранены. P3.3–P3.5 ещё не реализованы.

2026-11-05 — **Done P3.1 adapter proof**: [отчёт 013](reports/2026-11-05-013-renderer-adapter-proof.md). GemRender GPU morph потребовал explicit GUI upload; после исправления host palette, independent copies, reload/reset подтверждены реальными кадрами. Dedicated GPU-free neutral metadata reader/evaluator PASS. Для production нужен toolchain/API upgrade в P3.2. Путь пользовательской коллекции GLB/VRM/ZIP сохранён в workspace docs.

2026-11-05 — **Done P3.1 bounded spike / renderer pending**: [GemRender/BlendLib/custom comparison и fixture](reports/2026-11-05-012-renderer-capability.md), ADR-0017. Оригинальный rig+morph+clip, headless Java API PASS; candidate client remap FAIL на exact Loom 1.7.4 против 1.16.3. Следующий adapter/backport/host packaging/client visual proof определён без реализации gait/physics. Обычная сборка PASS, тесты UP-TO-DATE; runtime renderer не объявлен проверенным.

2026-11-05 — **Done P2.4 / Verified P2**: [Local/cloud dialogue и отказы](reports/2026-11-05-011-phase2-complete.md), ADR-0016. OpenRouter adapter и server-only named file secret resolver, реальный Gateway/cloud gameplay, dedicated timeout (40 ticks), stale/cancel/disconnect. 38 tests/build PASS, 3 opt-in skipped. Проваленные dev fixture попытки сохранены с причинами и исправлениями; последние D3/I4 exits=0. Ключ не скопирован в проект; source/evidence audit 0 leaks. Остановлено перед P3.

2026-11-05 — **Done P2.3**: [Реальный command/UI dialogue и events](reports/2026-11-05-010-dialogue-path.md), ADR-0015. Dedicated два игрока/restart и integrated/restart подтвердили private replies, persisted context, request/MessageId dedupe и counts History=events. 36 tests/build PASS. D2 gameplay PASS, один wrapper завершён exit1 после диагностированного Gradle stdin deadlock; исправление и limitation в отчёте. Следующий P2.4.

2026-11-05 — **Done P2.2**: [History/Context Builder](reports/2026-11-05-009-history-context.md), ADR-0014. Schema v3, scoped canonical messages, atomic minimal event record, replay/restart/failure invariants, budgeted single context engine. 35 tests PASS и real historyLive после reopen — ответ Cedar47. Игровой ingress — P2.3.

2026-11-05 — **Done P2.1**: [Production Gateway и реальная текстовая Ollama](reports/2026-11-05-008-provider-gateway-verified.md). После восстановления среды default/test переведены на text-only tag; `test providerLive` PASS, реальный ответ через Gateway, 5468ms, reasoning отключён. Можно переходить к P2.2.

2026-11-05 — **Partial/Blocked P2.1**: [Gateway и missing runner](reports/2026-11-05-007-provider-gateway-blocked.md), ADR-0013. Server-owned Main Dialogue, env secrets, bounded queue/timeout/cancel/response limit, нулевые retries, circuit breaker, sanitized errors. Build и 30 controlled/regression tests PASS, opt-in live в ordinary suite skipped; отдельный реальный providerLive **FAIL** (UNAVAILABLE, HTTP 500). Ollama 0.40.14 видит модель, но установка не содержит `llama-server.exe`. Ни одна задача P2 не объявляется завершённой; переход к P2.2 остановлен.

2026-11-05 — **Done P1.4 / Verified P1**: [Управление и итоговая приёмка](reports/2026-11-05-006-phase1-management.md), ADR-0012. Команды spawn/select/delete/own relationship, trusted owner/admin checks, фиксированный 65-byte request/≤256-byte private snapshot, connection selection/rate bounds и spawn caps. 22 unit tests/build PASS; A1/A2/B1/B2 с двумя игроками и I1/I2 с local player — все PASS. Online auth/OS disk failure/release-JAR compatibility не заявляются проверенными. P2 не начиналась.

2026-11-05 — **Done P1.2/P1.3**: [Зависимость storage и NPC lifecycle](reports/2026-11-05-005-npc-lifecycle.md). До продолжения P1.3 проверены критерии minimal storage/config и исправлены отказные случаи metadata/restore. Добавлены schema v2 migration/backup, typed NPC/relationships, durable operation IDs/tombstone, async lifecycle и временная entity без NBT writer. 17 unit tests/build PASS; dedicated и integrated stages 1→restart→2 PASS. Начальная ошибка порядка подписчиков harness сохранена и исправлена. P1.4 не начата.

2026-11-05 — **Partial P1.3, storage fix Done**: [Connection settings и Definition Registry](reports/2026-11-05-004-storage-followup-definition-registry.md). FK settings исправлены для каждого connection, SQLITE_FULL/reopen и FK rejection проверены. Добавлен встроенный versioned dev definition; registry tests/build PASS. Entity lifecycle ещё отсутствует. В workspace указатель добавлен найденный desktop Ollama launcher; API не запускался.

2026-11-05 (Asia/Vladivostok, UTC+10) — **Done with limitations, P1.2**: [Минимальный Storage Facade и world config](reports/2026-11-05-003-storage-facade.md). SQLite driver упакован в remapped JAR; save root от server API; schema/WorldId, scoped transaction, same-version/same-world backup restore, config save/load. Gradle build PASS, 4 storage tests PASS. OS-level write failure и Minecraft restart не проверены.

2026-11-05 (Asia/Vladivostok, UTC+10) — **Done, P1.1**: [Backend и world-scoped storage contract](reports/2026-11-05-002-storage-contract.md). Одна контрольная build с rerun tasks: PASS, 3 tests; SQLite/JSON fixture: 7 групп проверок на каждый backend, включая rollback/process reopen/migration/two saves. Принят ADR-0010: SQLite на server save; Java adapter/Storage Facade и P1.2 не реализованы. Production исходники не менялись.

2026-11-05 (Asia/Vladivostok, UTC+10) — **Done, documentation audit**: [Аудит архитектуры и перепроектирование плана](reports/2026-11-05-001-architecture-replan.md). Новый маршрут P0–P10, task-by-task перенос, устранение преждевременных gates и циклов планирования, уточнение staged storage/outbox и server pose. Обновлены Blueprint/архитектура/ADR/skill/карта. Runtime тесты повторно не запускались. Исторические записи ниже сохранены, включая смену старого статуса фаз.


2026-11-04 — **Partial, Фаза 0 In progress**: [Client lifecycle и двухклиентский probe](reports/2026-11-04-004-phase-0-client-network.md). Integrated runtime/re-entry и dedicated two-client probe PASS; исправлен каталог remote-only клиента; добавлен отдельный dev-only harness. Renderer/motion/storage не закрыты; первоначальный native GLFW crash сохранён в диагностике.

2026-11-04 — **Partial, Фаза 0 In progress**: [Повторное выполнение проверок Фазы 0](reports/2026-11-04-003-phase-0-revalidation.md). Build и тесты PASS; dedicated Fabric classloading достиг EULA gate; client застрял на downloadAssets; renderer/motion/storage остаются Open.

2026-11-04 — **Partial, dedicated runtime PASS**: EULA принята по инструкции пользователя; сервер запущен до `Done`, каталог AGNOR создан. Продолжение результатов в отчёте 003.

2026-11-04 — **Partial, Фаза 1 In progress**: [Старт фазы 1: layout каталогов и статус фаз](reports/2026-11-04-002-phase-1-foundation.md). Подготовительный этап Фазы 0 отмечен завершённым согласно skill; неисполненные технические spikes сохранены как открытые.

2026-11-04 — **Partial, Фаза 0 In progress**: [Fabric bootstrap, Gradle Wrapper, data root и сетевой probe](reports/2026-11-04-001-fabric-bootstrap.md). Сборка PASS, три unit-теста PASS; runtime и технические ограничения ещё не подтверждены.

| 2026-11-02 | Аудит межфазных producer/consumer-зависимостей | Done (documentation dependency audit; runtime unverified) | [Отчёт 011](reports/2026-11-02-011-phase-dependency-audit.md) | Исправлены producer/consumer staging-контракты Character Definition/Storage/Context Builder/Action Memory; Phase 2 NPC Runtime отделён от Phase 3 renderer через placeholder/Presentation Adapter; Phase 5 сохраняет dialogue events в transactional outbox, Phase 7 replay-ит per-consumer deliveries; сообщения не дублируются в Action Memory; Content Pack prompts/behavior/voice остаются пассивными до появления consumers; Main Dialogue preset активен в Phase 5, остальные preset destinations включаются вместе с consumers в Phases 7/8/9; cognition и autonomy реализуются вертикальными этапами; canonical message count закреплён: inbound и finalized assistant records считаются отдельно, Summarizer запускается по watermark каждые 10 records, Thinker — по turn-finalized/важным событиям/periodic coalesced trigger |
| Дата | Работа | Статус | Отчёт | Основной результат |
|---|---|---|---|---|
| 2026-11-02 | Разделение Motion Engine и источников поведения персонажа | Done (documentation correction; runtime unverified) | [Отчёт 010](reports/2026-11-02-010-brain-motion-phase-correction.md) | Фаза 3 ограничена техническим исполнителем/test harness; dialogue reactions перенесены в Фазу 5, Thinker affect — в 7, автономная связка Brain-to-Motion — в 8, lip-sync — в 9 |
| 2026-11-02 | Сверка Blueprint с архитектурой и фазовый порядок проектирования | Done (documentation review; runtime unverified) | [Отчёт 009](reports/2026-11-02-009-blueprint-phase-review.md) | Добавлены двусторонние матрицы ROADMAP/ARCHITECTURE/ADR, фазовый маршрут 0–11, design gates и 14 устранённых пробелов документации |
| 2026-11-02 | Системная спецификация устройства и взаимосвязей | Partial (blueprint 0.1) | [Отчёт 008](reports/2026-11-02-008-implementation-blueprint.md) | Создан единый blueprint: функции, API, контекст, связи гномиков, память, runtime, Motion Engine, multiplayer и отказные сценарии; открытые решения отмечены отдельно |
| 2026-11-02 | Возврат универсальных требований и контроля | Done (docs only) | [Отчёт 007](reports/2026-11-02-007-restore-universal-requirements.md) | Восстановлены полные технические критерии, универсальные контракты, подробная отчётность и политика artifacts только для ручной проверки |
| 2026-11-02 | Промежуточная ревизия упрощений (заменена) | Superseded | [Отчёт 006](reports/2026-11-02-006-development-friction-review.md) | Первоначальные упрощения пересмотрены и скорректированы по итогам уточнения требований пользователя |
| 2026-11-02 | Временные модели и замена перед финальной полной версией | Done (requirements/docs only) | [Отчёт 005](reports/2026-11-02-005-temporary-restricted-assets.md) | Локальные временные модели не блокируют первый companion-релиз; замена требуется перед финальным релизом после переноса авторского мира |
| 2026-11-02 | Переиспользование готовых моделей | Done (requirements/docs only) | [Отчёт 004](reports/2026-11-02-004-external-character-assets.md) | Зафиксирован asset-first pipeline, Blender для адаптации, capability report и ограничения лицензий |
| 2026-11-02 | Процедурный Motion Engine | Done (architecture/docs only) | [Отчёт 003](reports/2026-11-02-003-procedural-motion-engine.md) | Зафиксированы semantic motion tasks, whole-body coordination, IK, процедурная походка, host-side physics и недельная цель прототипа |
| 2026-11-02 | Уточнение визуальной цели и физики персонажей | Done (requirements/docs only) | [Отчёт 002](reports/2026-11-02-002-character-visual-quality.md) | Зафиксирован целевой профиль уровня stylized action-RPG / ZZZ; renderer оставлен на сравнительный прототип |
| 2026-11-02 | Инициализация документации проекта | Done (docs only) | [Отчёт 001](reports/2026-11-02-001-documentation-baseline.md) | Созданы README, архитектура, roadmap, индекс, пять ADR и шаблоны |

## Как обновлять журнал

После завершения каждой логической задачи:
1. Создать отдельный файл в `docs/reports/` по [шаблону](templates/DEVELOPMENT_REPORT_TEMPLATE.md).
2. Внести сюда одну строку со ссылкой на отчёт.
3. Если принято или изменено архитектурное решение, создать/обновить ADR и [реестр](DECISIONS.md).
4. Обновить [план](ROADMAP.md) и [архитектуру](ARCHITECTURE.md), если их содержание изменилось.

Не используйте этот журнал вместо подробного отчёта. Не записывайте здесь предположения как выполненные действия.
