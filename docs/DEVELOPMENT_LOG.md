# Журнал разработки AGNOR

Журнал содержит хронологический указатель на отдельные отчёты. Полное описание каждой работы хранится в `docs/reports/`; архитектурные решения — в отдельных ADR. Новые записи добавляются сверху.

## Записи

| 2026-11-02 | Аудит межфазных producer/consumer-зависимостей | Done (documentation dependency audit; runtime unverified) | [Отчёт 011](reports/2026-11-02-011-phase-dependency-audit.md) | Исправлены producer/consumer staging-контракты Character Definition/Storage/Context Builder/Action Memory; Phase 2 NPC Runtime отделён от Phase 3 renderer через placeholder/Presentation Adapter; Phase 5 сохраняет dialogue events в transactional outbox, Phase 7 replay-ит per-consumer deliveries; сообщения не дублируются в Action Memory; Phase 7 cognition и Phase 8 Bot/Goal/Action/Navigation объединены в вертикальные этапы; разделены два Scheduler и версии Pack/Add-on API |
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

## Текущий статус проекта

- **Стадия:** архитектурное проектирование / подготовка к техническим проверкам.
- **Реализация:** не начиналась в этом репозитории на момент создания базовой документации.
- **Сборка и тесты:** отсутствуют; проверить пока нечего.
- **Ближайшая задача:** Фаза 0 — минимальный Fabric 1.21.1 / Java 21 проект; проверить импорт готовой rigged-модели и static GLB, semantic tasks, IK, базовую походку, spring-chain волосы и host-authoritative pose sync.
- **Текущая ветка:** `main`.

## Как обновлять журнал

После завершения каждой логической задачи:
1. Создать отдельный файл в `docs/reports/` по [шаблону](templates/DEVELOPMENT_REPORT_TEMPLATE.md).
2. Внести сюда одну строку со ссылкой на отчёт.
3. Если принято или изменено архитектурное решение, создать/обновить ADR и [реестр](DECISIONS.md).
4. Обновить [план](ROADMAP.md) и [архитектуру](ARCHITECTURE.md), если их содержание изменилось.

Не используйте этот журнал вместо подробного отчёта. Не записывайте здесь предположения как выполненные действия.