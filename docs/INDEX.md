P5.2 Verified 2026-11-06: [отчёт010](reports/2026-11-06-010-lore-context.md), [ADR0026](decisions/ADR-0026-lore-context-admission.md). Scoped ContextSourceRegistry/пять statuses/budget/source IDs,110tests PASS+3skipped. Real Ollama: A private COBALT-732/B pack PACK-AMBER, nonmatch и privacy PASS. Следующая P5.3; полная P5 не завершена.

P5.1 Verified 2026-11-06: [отчёт009](reports/2026-11-06-009-lorebook-crud.md), [ADR0025](decisions/ADR-0025-lorebook-storage-and-sources.md), [команды Lorebook](MEMORY.md). Schema4/CRUD/scopes/activation/pack-local overrides,107tests PASS+3skipped, dedicated2clients PASS. Следующая P5.2, вся P5 ещё не завершена.

**Актуально 2026-11-06: P4 Verified.** P4.1→P4.2→P4.3 завершены последовательно; [итог008](reports/2026-11-06-008-phase4-acceptance.md), [формат и команды](CONTENT_PACKS.md), [UI/sync007](reports/2026-11-06-007-pack-controls-sync.md), ADR0022–0024.102tests PASS/3opt-in skipped; dedicated2clients/restart/integrated/real Ollama PASS. Restricted payload0. Следующая P5 не начата. Нижние записи с прежними статусами — история своих срезов.

P4.2 Verified 2026-11-06: [отчёт006](reports/2026-11-06-006-pack-activation.md), [ADR-0023](decisions/ADR-0023-content-generations-and-presentation.md). Atomic Registry/last-known-good, same UUID/external Firefly, dedicated+restart+integrated PASS;97tests PASS/3skipped. Следующая P4.3, вся P4 ещё не завершена.

P4.1 Verified: [Content Packs v1](CONTENT_PACKS.md), [ADR-0022](decisions/ADR-0022-content-pack-schema-and-ingestion.md), [отчёт005](reports/2026-11-06-005-pack-ingestion.md). Следующая P4.2.

# Индекс документации AGNOR

**P3 Verified,2026-11-06:** P3.1–P3.5 последовательно завершены в документированных границах. [Итоговая приёмка и бюджеты](reports/2026-11-06-004-phase3-acceptance.md), [pose sync/dialogue/compat](reports/2026-11-06-003-pose-sync-dialogue.md), [визуальные ограничения](assets/FIREFLY_VISUAL_LIMITS.md). Dedicated2clients и integrated, реальный dialogue/retry, reconnect/mismatch/stale, selected Sodium/Iris/Complementary PASS;83tests PASS/3opt-in skipped. Локальный dev rig adapter до P4; full cloth/MToon/voice/navigation не заявлены. Следующая фаза P4 не начата.

Этот файл — индекс документации; фактическая структура — [PROJECT_MAP](PROJECT_MAP.md). Здесь указано: что читать, где фиксировать изменения и какие документы считать источником истины.

P3.1: [renderer capability evidence / отчёт 012](reports/2026-11-05-012-renderer-capability.md), [ADR-0017 и следующий adapter proof](decisions/ADR-0017-renderer-capability-spike.md), [команды fixture](../src/p3Check/README.md). Исследование завершено с отрицательным drop-in результатом, production renderer не выбран.

## Быстрая навигация

Текущая реализация технических проверок: [протокол Фазы 0](PHASE_0_SPIKES.md) — bootstrap evidence, probe contract, команды запуска и перенесённые проверки; критерии задаёт только ROADMAP.

| Нужно найти или изменить | Документ / каталог |
|---|---|
| Общее описание проекта и ссылки | [Корневой README](../README.md) |
| Правила разработки и ссылки на документацию | [AGNOR Development Skill](../skills/agnor-development/SKILL.md) |
| Компоненты, границы модулей, потоки данных | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Полная логика функций, contracts, producer/consumer-граф, runtime cycles и отказные сценарии; не порядок фаз | [IMPLEMENTATION_BLUEPRINT.md](IMPLEMENTATION_BLUEPRINT.md) |
| Этапы, зависимости, критерии завершения | [ROADMAP.md](ROADMAP.md) |
| Принятые технические решения | [DECISIONS.md](DECISIONS.md) и [decisions/](decisions/) |
| Backend первой NPC и world-scoped storage contract (P1.1) | [ADR-0010](decisions/ADR-0010-world-scoped-storage.md), [отчёт и evidence](reports/2026-11-05-002-storage-contract.md) |
| Первая сохраняемая NPC, UUID и lifecycle (P1.3) | [ADR-0011](decisions/ADR-0011-npc-lifecycle.md), [сценарии и evidence](reports/2026-11-05-005-npc-lifecycle.md) |
| Команды, права, private snapshots и завершение P1 | [ADR-0012](decisions/ADR-0012-npc-management-protocol.md), [итоговая приёмка](reports/2026-11-05-006-phase1-management.md), [команды пользователя](../README.md#минимальное-управление-npc) |
| Main Dialogue Gateway/preset, P2.1 Verified | [ADR-0013](decisions/ADR-0013-main-dialogue-gateway.md), [реальный text-only Gateway PASS](reports/2026-11-05-008-provider-gateway-verified.md) |
| History/единый Context Builder и private gameplay dialogue | [ADR-0014](decisions/ADR-0014-conversation-history-context.md), [ADR-0015](decisions/ADR-0015-dialogue-protocol.md), отчёты [009](reports/2026-11-05-009-history-context.md)/[010](reports/2026-11-05-010-dialogue-path.md) |
| Завершение P2: local/cloud, host secrets и отказы | [ADR-0016](decisions/ADR-0016-cloud-mode-failure-policy.md), [итог 011](reports/2026-11-05-011-phase2-complete.md), [настройка/команды](../README.md#настройка-и-диалог) |
| Требования к качеству моделей и физике | [ADR-0006](decisions/ADR-0006-character-visual-quality-and-physics.md) |
| Процедурное движение, IK и Motion Engine | [ADR-0007](decisions/ADR-0007-procedural-motion-engine.md) |
| Внешние готовые 3D-модели и их импорт | [ADR-0008](decisions/ADR-0008-reuse-external-character-assets.md) |
| Временные модели, которые нужно заменить до релиза | [Asset Replacement Register](assets/ASSET_REPLACEMENT_REGISTER.md) |
| Отчёт о конкретной выполненной задаче | [reports/](reports/) |
| Хронология всех работ | [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md) |
| Шаблон отчёта о работе | [DEVELOPMENT_REPORT_TEMPLATE.md](templates/DEVELOPMENT_REPORT_TEMPLATE.md) |
| Шаблон ADR | [DECISION_TEMPLATE.md](templates/DECISION_TEMPLATE.md) |
| Текущие требования и границы MVP | [ARCHITECTURE.md](ARCHITECTURE.md), разделы «Цели» и «Вне первой версии» |
| Изменение порядка этапов | [ROADMAP.md](ROADMAP.md) |

## Источники истины

| Информация | Единственный ответственный источник |
|---|---|
| Функциональная цель и границы | ARCHITECTURE: цели/возможности; принятые требования ADR-0004/0006/0007/0008 |
| Архитектурные границы и ownership | ARCHITECTURE + принятые ADR; изменение решения получает новый ADR |
| Детальные алгоритмы, lifecycle, I/O | IMPLEMENTATION_BLUEPRINT, технические разделы 1–29 |
| Порядок задач и критерии завершения | ROADMAP P0–P10; другие документы только ссылаются на него |
| Фактический прогресс | DEVELOPMENT_LOG → отчёт → конкретный код/runtime evidence |
| Фактическая структура/наличие компонентов | [PROJECT_MAP](PROJECT_MAP.md), сверяется с filesystem |
| Инструкции Codex | [Проектный skill](../skills/agnor-development/SKILL.md) |
| Основания перестановки и аудит старых задач | [Аудит](reports/2026-11-05-001-architecture-replan.md), [PLAN_MIGRATION](PLAN_MIGRATION.md) |

Отчёты до 2026-11-05 и [архив](archive/) сохраняют историческую нумерацию 0–11. Их статусы/«следующий шаг» описывают тот момент, **не являются действующими инструкциями**. Прежняя широкая фаза 0 не выполнена; новая P0 закрыта в ином явно утверждённом объёме. Документы цели не доказывают реализацию.

## Куда писать

### 1. Изменение поведения или реализация функции
Для каждой законченной логической задачи создавайте отдельный файл:

`docs/reports/YYYY-MM-DD-NNN-short-title.md`

В отчёте указывайте исходное требование, фактически изменённые файлы, сделанные действия, команды и проверки, результат, известные ограничения и то, что осталось НЕ ПРОВЕРЕНО. После создания отчёта добавляйте одну строку в [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md).

### 2. Новое архитектурное решение или изменение существующего
Создавайте отдельный ADR:

`docs/decisions/ADR-NNNN-short-title.md`

Не переписывайте старое решение так, будто оно всегда было другим. Если решение заменено, измените его статус на Superseded, укажите заменяющий ADR и создайте новый документ. Обновите [DECISIONS.md](DECISIONS.md).

### 3. Изменение архитектуры
Обновляйте [ARCHITECTURE.md](ARCHITECTURE.md) вместе с ADR, если изменились границы компонентов, интерфейсы, потоки данных или модель хранения. Описание должно соответствовать фактическому коду; неподтверждённые элементы помечайте как Proposed или Not implemented.

### 4. Изменение плана
Обновляйте [ROADMAP.md](ROADMAP.md), когда изменился порядок этапов, зависимости или критерии готовности. В отчёте указывайте причину изменения.

### 5. Добавление новых типов документации
Сначала добавьте ссылку и правило хранения в этот индекс. Не создавайте параллельные журналы с пересекающейся ответственностью.

## Правила статусов

- **Accepted** — решение согласовано и является текущим направлением.
- **Proposed** — вариант рассматривается, но ещё не утверждён.
- **Implemented** — реализовано в коде; это не означает автоматически, что функция полностью проверена.
- **Verified** — проверено указанными тестами или воспроизводимым пользовательским сценарием.
- **Blocked** — работа остановлена из-за указанной зависимости или проблемы.
- **Superseded** — решение заменено более новым ADR.

Для функций используйте отдельные статусы: Planned, In progress, Implemented, Verified, Blocked. Не ставьте Verified только на основании успешной компиляции.

## Идентификаторы и порядок записей

- ADR: возрастающий номер `ADR-0001`, `ADR-0002` и далее.
- Отчёты: дата UTC и порядковый номер за день, например `2026-11-02-001-content-packs.md`.
- Журнал: новые записи добавляются сверху, ссылки ведут на отдельные отчёты.
- Даты в отчётах записывайте в ISO 8601 с часовым поясом или явно указывайте UTC.

## Обязательный минимум для каждого отчёта

1. Цель и исходное требование.
2. Состояние репозитория до работы: ветка и HEAD, если доступны.
3. Что изменено — точные пути файлов.
4. Принятые решения и ссылки на ADR.
5. Какие проверки выполнены и их результаты.
6. Что не удалось проверить.
7. Оставшиеся риски и следующий конкретный шаг.

## Ограничения безопасности

Не коммитьте API-ключи, токены, пароли, реальные персональные данные игроков, приватные диалоги и дампы пользовательских миров. Примеры конфигурации должны содержать только фиктивные значения. Секреты хоста не должны попадать в Content Packs или синхронизироваться клиентам.

## GitHub Actions и артефакты

Квота на GitHub Actions artifacts ограничена. **Не создавать и не загружать артефакты на каждый запуск CI.** Артефакт формируется только тогда, когда требуется ручная проверка человеком: например, нужно скачать собранный JAR, открыть скриншоты/видео, изучить диагностический пакет или воспроизвести результат теста.

Для обычных автоматических проверок использовать статус check, вывод тестов, job logs и job summary. Если ручная проверка необходима, прикладывать только минимальный набор файлов, который нужен проверяющему; не включать секреты, пользовательские данные и временные модели. Где доступно, задавать минимальный необходимый срок хранения.
