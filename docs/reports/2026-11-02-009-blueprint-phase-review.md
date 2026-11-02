# Отчёт 009: Сверка Blueprint и фазовый порядок проектирования

- **Дата:** 2026-11-02
- **Статус:** Partial (design review completed for current documentation baseline)
- **Ветка:** main
- **HEAD до работы:** aa8104d4a7c73153af76e8ff4b0f734410309963
- **HEAD после основных изменений Blueprint/ROADMAP/INDEX:** c395beb249848e9323c5bc6631c84aca091dd2ff
- **Связанные ADR:** ADR-0001…ADR-0008; решения не переутверждались
- **Связанные этапы:** Фазы 0–11

## 1. Цель и исходное требование

Проверить полноту IMPLEMENTATION_BLUEPRINT.md по отношению к ARCHITECTURE.md, ROADMAP.md и ADR-0001…ADR-0008; выявить пропуски, неоднозначности и конфликты последовательности; расположить проектирование в порядке фактической реализации фаз 0–11.

## 2. Состояние до изменений

- Ветка main, HEAD aa8104d4a7c73153af76e8ff4b0f734410309963.
- Найдена одна ветка main; открытых PR нет.
- Основные источники сверки: ARCHITECTURE.md (13 разделов), ROADMAP.md (фазы 0–11), Blueprint 0.1 и все восемь ADR.
- Blueprint уже описывал компоненты по архитектурным темам, но не предоставлял основного маршрута проектирования по фазам ROADMAP.

## 3. Выполненные действия

1. Сверены требования каждой фазы ROADMAP с разделами Blueprint.
2. Сверены все 13 тематических разделов ARCHITECTURE и ADR-0001…ADR-0008; затем добавлены отдельные обратные таблицы прослеживаемости для всех 13 разделов и восьми ADR.
3. В Blueprint добавлены:
   - матрица соответствия ROADMAP → Blueprint;
   - пофазный порядок проектирования для фаз 0–11;
   - design artifacts и design gates каждой фазы;
   - межфазные зависимости и границы staged implementation;
   - реестр 14 обнаруженных пробелов GAP-01…GAP-14 с фазами их закрытия.
4. Добавлена спецификация optional addon/extension API в соответствии с ADR-0005.
5. Добавлен отдельный безопасный workflow переноса авторского мира: inventory, mapping, dry-run, staging, validation, approval, rollback.
6. Зафиксированы важные последовательности:
   - Phase 3 использует локальный test asset; формальный Content Pack manager появляется в Phase 4.
   - Phase 5 создаёт durable Conversation History через общий Storage API; Phase 6 расширяет то же хранилище, не создавая второе.
   - Phase 7 фиксирует Bot service/plan contract; полноценное world action execution начинается в Phase 8.
   - Phase 3 определяет facial/viseme interface; TTS timing подключается в Phase 9.
7. ROADMAP получила прямую ссылку на фазовый маршрут Blueprint. Критерии выхода ROADMAP не изменялись и не смягчались.
8. INDEX уточнён: Blueprint теперь обозначен как основной документ фазовой последовательности проектирования.

## 4. Обнаруженные пробелы и исправления

| ID | Проблема до сверки | Исправление / привязка |
|---|---|---|
| GAP-01 | Для технических spikes Phase 0 отсутствовала единая измерительная процедура | Spike artifacts и design gate Phase 0 |
| GAP-02 | Config/data root и lifecycle были описаны слишком общо | Config/catalogue design Phase 0–1 |
| GAP-03 | NPC create/delete/load/save lifecycle был недостаточно формализован | State machine, ownership и save/load flow Phase 2 |
| GAP-04 | Animation graph, solver validation и viseme handoff не были разложены по фазам | Animation/layer/viseme contracts Phase 3/9 |
| GAP-05 | Не были выделены pack activation, enable/disable, overrides, hot reload и rollback | Package lifecycle и precedence Phase 4 |
| GAP-06 | Был риск создать временное хранилище истории в Phase 5 и второе в Phase 6 | Единая эволюционирующая Conversation History через Storage API |
| GAP-07 | Реализация Bot не была отделена от memory gnomes | Bot contract Phase 7; action execution Phase 8 |
| GAP-08 | Не был определён manual run/debug workflow для гномиков | Permissioned dry-run/status/cancel/diagnostics Phase 7 |
| GAP-09 | Backup/restore/retention имели недостаточно конкретный lifecycle | Data recovery and retention design Phase 6; release drills Phase 10 |
| GAP-10 | TTS описывался без полного audio queue/cache/interruption lifecycle | Audio state machine and playback contracts Phase 9 |
| GAP-11 | Release compatibility, soak testing и distribution audit были обозначены общо | Release matrix/soak/rollback/security audit Phase 10 |
| GAP-12 | Отдельный optional addon API не был полностью специфицирован | Extension API section; interface design before addon development |
| GAP-13 | Перенос авторского мира не имел безопасного migration workflow | Inventory/mapping/dry-run/staging/rollback Phase 11 |
| GAP-14 | Не был явно разделён facial rig contract и фактическое подключение TTS | Viseme timeline contract Phase 3, TTS source Phase 9 |

## 5. Изменённые файлы

| Путь | Изменение | Причина |
|---|---|---|
| docs/IMPLEMENTATION_BLUEPRINT.md | Расширен до фазово-упорядоченной спецификации; добавлены crosswalk, design gates, gap register, addon API и world migration | Превратить архитектурный каталог в рабочий порядок проектирования |
| docs/ROADMAP.md | Добавлена связь с Blueprint и обязательность соответствующих design artifacts | Связать реализацию и проектирование без изменения критериев фаз |
| docs/INDEX.md | Уточнено назначение Blueprint | Сделать фазовый маршрут обнаруживаемым |
| docs/reports/2026-11-02-009-blueprint-phase-review.md | Этот отчёт | Зафиксировать сверку и результаты |
| docs/DEVELOPMENT_LOG.md | Добавлена запись 009 | Сохранить хронологию |

## 6. Проверки и результаты

| Проверка | Результат | Что подтверждено |
|---|---|---|
| Branch search | PASS | Единственная найденная ветка — main |
| Open PR search | PASS | Открытых PR нет |
| Fetch ROADMAP | PASS | Все фазы 0–11 и исходные критерии прочитаны |
| Fetch ARCHITECTURE | PASS | Все 13 разделов сопоставлены |
| Fetch ADR-0001…ADR-0008 | PASS | Все восемь принятых решений сопоставлены |
| Blueprint structure | PASS | Содержит фазовый маршрут, crosswalk, GAP-01…GAP-14, addon и world migration sections |
| ROADMAP criteria | PASS | Исходные критерии фаз сохранены без смягчения |
| Source/runtime behavior | НЕ ПРОВЕРЕНО | Изменялась только документация; кодовая реализация не проверялась |

## 7. Решения и важные уточнения

- Порядок фаз ROADMAP оставлен без изменений.
- Новые решения о конкретной базе, renderer, solver, API provider, сетевой частоте или compatibility matrix не выдумывались.
- Open-вопросы должны закрываться в фазе, указанной в фазовом плане.
- Появление раздела в Blueprint не означает реализацию или проверку функции.

## 8. Ограничения и оставшаяся работа

Сверка завершена для текущего документального baseline, но Blueprint ещё не является доказательством реализуемости. Технические решения Phase 0 требуют фактических spikes. Некоторые конкретные schema/API details остаются Open до проверки Fabric 1.21.1 и выбранных библиотек.

Следующий этап — выполнять design gates и прототипы в порядке фаз; не начинать последующую реализацию при незакрытых обязательных зависимостях предыдущей фазы.

## 9. Итог

Blueprint теперь имеет основной фазовый маршрут 0–11, матрицу соответствия с ROADMAP, перечень исправленных недоопределённостей и расширенную спецификацию addon/world migration. ARCHITECTURE, ROADMAP и ADR сохранены как источники требований и решений, без подмены их содержания.

