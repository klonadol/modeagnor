# Журнал разработки AGNOR

Журнал содержит хронологический указатель на отдельные отчёты. Полное описание каждой работы хранится в `docs/reports/`; архитектурные решения — в отдельных ADR. Новые записи добавляются сверху.

## Текущее состояние на 2026-11-05

P0 **Verified в новом scope bootstrap**; контрольная сборка и три существующих теста повторно прошли 2026-11-05. P1 **In progress**: P1.1 завершена как ограниченное исследование SQLite/JSON и world-scoped contract (ADR-0010); первая незавершённая задача — **P1.2**, её реализация не начата. P2–P10 **Not started**. Production storage/NPC отсутствуют; старый широкий renderer/motion gate не выполнен. Код/Wrapper остаются локальными незакоммиченными изменениями на main/5d06367.

## Записи

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
