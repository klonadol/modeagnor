# Отчёт 011: Аудит producer/consumer-зависимостей и совместных этапов

- **Дата:** 2026-11-02
- **Статус:** Done (documentation dependency audit; runtime behavior not implemented/verified)
- **Ветка:** main
- **HEAD до аудита:** c40f8503b42b324da9c8fd1aa8b79db3d904cb45; перед созданием отчёта — 9968ec18f8ea6dd5dae99f700033f6709730f7e5
- **Связанные документы:** IMPLEMENTATION_BLUEPRINT.md, ROADMAP.md, ARCHITECTURE.md, ADR-0003, ADR-0004, ADR-0005
- **Связанные фазы:** 0–11, особое внимание 1–9

## 1. Цель

Проверить, нет ли в последовательности проекта функций, которые реализуются раньше обязательного producer, consumer или исполнительного компонента. Если функция может существовать в ограниченном режиме, зафиксировать безопасный staging contract. Если компоненты образуют взаимный runtime цикл, назначить их совместную реализацию и совместную приёмку в одной фазе.

Проверка проводилась по ROADMAP, всем фазовым разделам IMPLEMENTATION_BLUEPRINT, общей ARCHITECTURE и связанным ADR.

## 2. Основной вывод

Обнаружено несколько дополнительных dependency gaps. Они исправлены в документации:

1. Character Definition мог получить один формат в Phase 2 и второй — в Content Packs Phase 4.
2. Context Builder Phase 5 ссылался на шесть хранилищ, которые появляются только в Phase 6.
3. Thinker Phase 7 использовал Action Memory до появления источника событий.
4. Memorizer, Thinker, Judge и Summarizer имеют циклические зависимости и не должны приниматься как четыре несвязанные функции.
5. Thinker создавал goal proposals, но Goal Scheduler-потребитель появлялся только в Phase 8.
6. Bot зависел от Goal Scheduler, Action Validator/Executor и Navigation, но его место было описано как часть Phase 7.
7. Был риск смешать Gnome Task Scheduler и Goal Scheduler, хотя они управляют разными типами задач.
8. Semantic Lorebook retrieval зависел от Embedding adapter Phase 7, а само хранилище появлялось в Phase 6.
9. Публичный Addon API мог фиксироваться раньше, чем сформированы реальные Action/Goal/Event contracts.
10. Версия Content Pack могла быть ошибочно понята как версия executable Addon API.
11. Первая NPC создавалась в Phase 2 до продуктового renderer Phase 3, но не было явной гарантии, что server Character Runtime независим от визуального слоя.

## 3. Принятые исправления по зависимостям

### 3.1 Единый Character Definition Registry

- Phase 2 создаёт базовую Character Definition schema и Registry со встроенным/dev source.
- Phase 4 добавляет внешний Content Pack Definition Source к тому же Registry.
- Pack Loader не создаёт второй формат персонажей и не меняет NPC identity/lifecycle contract.

### 3.2 Единый Storage Facade

- Phase 0 выбирает backend на основании технического spike.
- Phase 1 создаёт общий Storage Facade, schema versioning, scope enforcement, transactions, migrations и durable outbox/task capability.
- Phase 2 сохраняет runtime NPC state через этот Facade.
- Phase 5 сохраняет Conversation History через тот же Facade.
- Phase 6 добавляет шесть memory stores без второй базы и без переноса/дублирования истории.
- Phase 7 использует общий Facade для task/outbox и goal proposal handoff.

### 3.3 Один Context Builder с Context Profiles

- Phase 5 реализует один Context Builder с initial sources: Character Definition/system policy, Runtime/Relationship snapshot и Conversation History.
- Phase 6 добавляет memory stores через Context Source Registry.
- Phase 5 использует dialogue profile; Phase 7 добавляет thinker profile.
- Каждый profile задаёт собственные source allowlists, scope, freshness, priority и token budget.
- Gnome Input Assemblers используют тот же source registry, но сохраняют строгие allowlists. Memorizer не получает доступ к дополнительной памяти только потому, что она появилась в Context Builder.
- Отсутствующие источники имеют статус NOT_YET_ENABLED, который не подменяется успешным пустым retrieval.

### 3.4 Action Memory и Thinker

- Phase 6 создаёт Action Memory schema/API, но не объявляет store функциональным без producer.
- Phase 7 одновременно с когнитивными гномиками вводит World Event Feed Foundation — ограниченный server-side producer релевантных событий NPC/игрока.
- Этот feed начинает наполнять Action Memory и предоставляет Thinker/Summarizer наблюдаемые данные.
- Phase 8 расширяет его до полного World Observer и добавляет фактические outcomes автономных действий.
- Сохраняется один Event Feed/Action Memory contract, который расширяется, а не переписывается.

### 3.5 Когнитивные гномики — совместная интеграция Phase 7

В Phase 7 совместно реализуются:
- World Event Feed Foundation;
- Gnome Task Scheduler;
- Memorizer;
- Judge;
- Thinker;
- Summarizer;
- Affective Presentation Mapper.

Причина совместной приёмки:
- Thinker пишет Thoughts, которые читает Memorizer;
- Memorizer создаёт candidates, которые обрабатывает Judge;
- Judge пишет Diary, который читает Thinker;
- Summarizer обновляет Summary, которую использует Thinker;
- Event Feed пишет Action Memory, которую читают Thinker и Summarizer.

Unit/contract tests компонентов остаются отдельными, но ни один из четырёх когнитивных гномиков не считается функционально завершённым до общей проверки pipeline.

### 3.6 Goal Proposal Inbox — мост между Phase 7 и Phase 8

- Thinker сохраняет валидированные goal proposals в durable Goal Proposal Inbox/Outbox через Storage Facade.
- Каждая запись имеет ID, scope, evidence references, priority hint, expiry и PENDING status.
- Inbox — outbox/task record, связанный с Thoughts, а не седьмое хранилище памяти.
- До Phase 8 proposal не запускает world action и не меняет мир.
- Phase 8 атомарно принимает/отклоняет/просрочивает proposal и предотвращает повторное исполнение.

### 3.7 Bot и автономный runtime — совместная интеграция Phase 8

- Phase 7 фиксирует только Bot typed plan/action-intent contract.
- Bot как пятый гномик реализуется в Phase 8 вместе с Goal Scheduler, полным World Observer, Action Registry/Validator/Executor, Navigation и Motion Engine bridge.
- Причина: Goal Scheduler выбирает цель; Bot планирует; Validator/Executor проверяют и выполняют; Navigation/Motion перемещают; Observer фиксирует исход; Action Memory/Thinker обновляют состояние. Это замкнутый цикл, и независимая приёмка его частей создала бы формально готовые, но неработающие функции.
- Bot provider preset подключается вместе с Bot, если он использует LLM; deterministic execution не вызывает модель на каждом tick.

### 3.8 Два разных планировщика

- **Gnome Task Scheduler (Phase 7):** очереди фоновых AI worker jobs, provider concurrency, retry, cancellation, fairness и checkpoint.
- **Goal Scheduler (Phase 8):** игровые цели NPC, dependencies, priority, preemption, lifecycle действий и anti-loop.
- Они не являются одним сервисом и не используют общую незащищённую in-memory queue. Между ними передаются durable goal proposals.

### 3.9 Lorebook и Embedding

- Phase 6 включает Always/exact/keyword Lorebook activation.
- Semantic-only retrieval остаётся явно NOT_YET_ENABLED до Embedding adapter Phase 7.
- Отсутствие embedding не выдаётся за корректную пустую семантическую выдачу.

### 3.10 Версии Content Pack и Addon API

- Phase 4 использует собственную declarative Content Pack schema/API compatibility version.
- Эта версия не является executable Addon API version.
- Внутренние модульные границы допустимы с Phase 1.
- Публичная семантика Action/Goal/Event API определяется после рабочего runtime Phase 8 и замораживается после compatibility hardening Phase 10.
- Addon implementation остаётся в Phase 11.

### 3.11 Server Character Runtime до renderer

- Phase 2 создаёт NPC UUID, Character Definition, Runtime/Relationship state и persistence на сервере; наличие renderer, rig или локального model file не является prerequisite для server activation.
- Если визуальный слой ещё не готов, server spawn snapshot обозначает presentation status `PRESENTATION_NOT_READY`, а клиенты показывают placeholder.
- Phase 3 добавляет Character Presentation Adapter, который подключает renderer/rig/pose controller к уже существующему NPC UUID и server presentation snapshots. Он не создаёт вторую entity identity/lifecycle и не требует переписывать Character Runtime.
- Для Phase 2 это явное ограничение: NPC logical/runtime lifecycle проверяется с placeholder; полноценная модель и визуальное управление принимаются в Phase 3.

## 4. Группы обязательной совместной реализации

| Фаза | Компоненты | Почему нельзя принимать независимо |
|---|---|---|
| 7 | Event Feed Foundation + Gnome Task Scheduler + Memorizer + Judge + Thinker + Summarizer + Affective Mapper | Взаимные чтение/запись Thoughts, Diary, Summary и Action Memory образуют единый cognition/memory cycle |
| 8 | Full World Observer + Goal Scheduler + Bot + Action Registry/Validator/Executor + Navigation + Motion Engine bridge | Замкнутый цикл от намерения до действия и фактического результата |
| 9 | TTS generation + audio task/queue + playback clock/transport + viseme timeline + Face Controller | Lip-sync нельзя подтвердить без синхронного end-to-end audio playback |
| 2 → 3 | Server Character Runtime + client placeholder (Phase 2), затем Character Presentation Adapter + renderer binding (Phase 3) | NPC server identity/persistence должна работать до renderer; adapter привязывает визуальный слой к тому же UUID, а не создаёт второй NPC |

## 5. Правило готовности

В Blueprint отдельно введены:
- **Contract ready** — интерфейсы и ограничения описаны;
- **Implementation ready** — producer и consumer существуют;
- **Runtime integrated / Verified** — вся цепочка соединена и прошла user-path/failure tests.

Ранний компонент может работать в reduced mode с явным NOT_YET_ENABLED или сохранять durable pending record. Он не может скрывать отсутствующую зависимость, выдавать заглушку за рабочую функцию или менять мир без готового consumer.

## 6. Изменённые файлы

| Путь | Изменение |
|---|---|
| docs/IMPLEMENTATION_BLUEPRINT.md | Добавлена dependency matrix 0.10, правило готовности 0.11, новые GAP-15…GAP-22; GAP-22 фиксирует независимость Phase 2 NPC Runtime от Phase 3 renderer, unified registry/storage/context staging, co-implementation Phase 7/8/9, goal outbox, scheduler distinction, embedding and API version staging |
| docs/ROADMAP.md | Фазы 1/2/3/4/5/6/7/8/11 уточнены по producer/consumer dependency; четыре cognitive gnomes в Phase 7; Bot/full autonomy в Phase 8 |
| docs/ARCHITECTURE.md | Добавлены Character Definition Registry staging, Context Profiles, Event Feed dependency, distinction of schedulers и общий dependency policy |
| docs/decisions/ADR-0003-content-packs.md | Разделены Content Pack schema version и executable Addon API version |
| docs/decisions/ADR-0004-memory-pipeline.md | Зафиксированы совместная активация memory/gnome cycle, Event Feed, Goal Proposal Outbox и scheduler distinction |
| docs/decisions/ADR-0005-optional-integrations.md | Публичный Addon API стабилизируется после Phase 8 и freeze Phase 10; addons в Phase 11 |
| docs/INDEX.md | Назначение Blueprint уточнено как карта producer/consumer и co-implementation dependencies |
| docs/reports/2026-11-02-011-phase-dependency-audit.md | Этот отчёт |
| docs/DEVELOPMENT_LOG.md | Запись отчёта 011 |

## 7. Проверки

| Проверка | Результат | Подтверждение |
|---|---|---|
| Branch search | PASS | Только main |
| Open PR search | PASS | Открытых PR не обнаружено |
| Fetch current ROADMAP/Blueprint/Architecture/ADRs | PASS | Изучены изменённые документы и текущие контракты |
| Dependency mapping | PASS (documentation) | Для основных producer/consumer chains определены фазы и стадии активации |
| Joint implementation groups | PASS (documentation) | Явно назначены совместные Phase 7/8/9 vertical integrations |
| Cross-document consistency | PASS (documentation) | Финально перечитаны Blueprint, Roadmap, Architecture, ADR-0003/0004/0005, Index, report и journal; Phase 2 placeholder ↔ Phase 3 adapter также согласованы |
| Runtime implementation/tests | НЕ ПРОВЕРЕНО | Изменения только документационные; код проекта не реализован |

## 8. Оставшиеся технические ограничения

Порядок зависимостей документально уточнён, но это не означает, что технические гипотезы подтверждены. Выбор storage backend, точные schema, Provider capabilities, Embedding index, Event Feed coverage, Action/Goal semantics, Navigation mechanics, renderer и performance budgets всё ещё требуют spikes/implementation tests в соответствующих фазах.

## 9. Итог

Последовательность теперь учитывает producer/consumer dependencies, а не только желаемый порядок функций. У функций, которые могут существовать в ограниченном режиме, определён честный staging contract. Взаимозависимые группы получают одну совместную end-to-end приёмку. Фазы ROADMAP не переставлены произвольно; исправлены scope каждой фазы и границы того, что считается реализованным.
