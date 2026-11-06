# Аудит архитектуры и перепроектирование плана ModeAgnor

- Дата: 2026-11-05, Asia/Vladivostok (UTC+10).
- Статус: выполнен аудит документации и плана; новая игровая функциональность не реализована.
- Репозиторий: `C:/Users/User/Documents/Codex/AGNOR/modeagnor`; branch `main`; HEAD `5d06367178325fcb6b41c1075af1ae0c5cc91613`, без изменения ветки/HEAD/PR.
- Основание: прямой запрос пользователя пересмотреть систему планирования, сохранив конечную цель интеллектуального 3D-NPC.

## 1. Метод и фактическая база

Изучены проектный skill, README/INDEX/ARCHITECTURE/ROADMAP, Blueprint (включая полный технический каталог 1–29), ADR-0001–0008, Asset Replacement Register, DEVELOPMENT_LOG, шаблоны и отчёты 2026-11-02/04, ссылки между ними и workspace map/environment. Проверены дерево исходников, build/source sets, scripts, wrapper, Git status/HEAD/log и сохранённые результаты прошлых запусков. Применимого AGENTS.md в репозитории и предках не найдено; AGENTS другого проекта `work/tunnel-plugin` не применяется.

Исходное рабочее дерево уже было dirty: README, DEVELOPMENT_LOG, INDEX, ROADMAP изменены; source/build/wrapper/scripts/skill/PHASE_0_SPIKES и четыре отчёта 2026-11-04 не отслеживались Git. Эти изменения не сброшены. До правок сделаны локальные копии документов и хеши исходников/сборки в `build/audit-2026-11-05/`. Для трассировки старого порядка сохранены документационные [архив ROADMAP](../archive/ROADMAP_2026-11-04.md) и [старая фазовая часть Blueprint](../archive/BLUEPRINT_PHASE_PLAN_2026-11-04.md).

Последние коммиты 5d06367, 5cd3404, 0d0325b, a14cffd, 27118e8 меняли документацию count/dependency audit, а не реализовывали runtime. Наличие этих коммитов не доказывает наличие памяти/агентов. Полная фактическая структура — [PROJECT_MAP](../PROJECT_MAP.md).

### Реализовано и подтверждено

- Java 21/Fabric 1.21.1 bootstrap; Loom split main/client, Wrapper 8.8; main/server classloading без клиентских классов.
- DataRoots создаёт общие каталоги gameDir/mods/agnor. Agnor вызывает его на SERVER_STARTING, AgnorClient — при client init. Это не world-scoped сохранение.
- ProbeProtocol v1: fixed request 12 bytes, response 20; context sender, version/negative sequence validation, response rate limit, cleanup при disconnect/stop. Нет gameplay side effects.
- Отдельный dev-only ProbeHarness исключён из normal JAR; два loopback offline UUID, ошибки/burst/replay, restart marker.
- Существующий `build/test-results/test/TEST-dev.agnor.DataRootsTest.xml`: tests=3, failures=0, errors=0. Они проверяют custom gameDir/repeat, разные roots и file collision; не ACL или NPC persistence.
- [Отчёт 004](2026-11-04-004-phase-0-client-network.md) и `build/phase0-evidence/` подтверждают integrated start/re-entry и два dedicated client прохода с exit=0, включая restart. Маркер — подтверждение сохранности файла, не база данных.

Character Runtime, NPC entity, settings/permissions, Storage Facade, LLM providers/dialogue, renderer/rig/IK/physics, packs loader, memory stores/gnomes/schedulers, actions/navigation и TTS отсутствуют. Каталоги packs/presets/server не считаются реализацией этих подсистем. Нет CI workflows. Source code не содержит альтернативного готового игрового слоя, который пришлось бы сохранять/рефакторить.

## 2. Подтверждённые проблемы планирования

Старые местоположения сохранены в архиве; таблица указывает причину и исправление первоисточника.

| Проблема / где | Почему мешает | Исправление |
|---|---|---|
| Старый ROADMAP фаза 0: rigged/static models, IK/whole-body/gait/hair/host pose/two clients, затем те же задачи фазы 3 | Gate требует значительной реализации будущей подсистемы до первого NPC. Test harness убирает зависимость от LLM, но не от importer/rig/solver/network pose | P0 только bootstrap; bounded capability risk P3.1; реальные motion slices P3.2–P3.5 |
| Exit старой фазы 0: «подтверждённые технические ограничения» без конечного списка, недельный план ADR-0007 | Не различает выбор API и готовый демонстратор; создаёт расширяемый preparatory gate | Конечные P0.1–P0.3 и отдельные A/B/C проверки, без недельного обязательства |
| Старые Blueprint 0.4–0.12 и ROADMAP — два плана фаз | Дублирование design gates и нумерации, рассогласование после изменений | Порядок только ROADMAP; в Blueprint граф/контракты/staging; старые checklists сохранены в архиве |
| Старые фазы 1–2 повторяют lifecycle/source sets уже созданного bootstrap; duplicate ownership bullet фазы 2 | Повторная работа не создаёт новой функции, скрывает фактическую готовность | P1 объединяет минимальный storage/runtime/операции; bootstrap зачтён только в его реальном scope |
| Storage backend был gate фазы 0, полный Facade/queues раньше первого writer | Нет конкретного persistence сценария для выбора; заранее проектируются future stores | P1.1 NPC+relationship transaction; таблицы создаются с writers, index позже |
| Старый диалог фазы 5 требует visual reaction из фазы 3 | Не позволяет проверить основной LLM/latency/privacy риск без готовой сложной графики | P2 реальный диалог/semantic intent; P3.5 подключает визуальную реакцию |
| Старые phase6 stores без writers ActionMemory/Thoughts/Summary | Пустые repositories дают ложное ощущение прогресса и преждевременные миграции | History P2, Lorebook P5, остальные с writers P6 |
| ADR-0004/ARCHITECTURE: ни один cognitive worker не завершён до всего цикла | Runtime feedback смешан с implementation dependency; Thinker уже по собственному контракту допускает неполный контекст | P6.1–P6.5 независимые срезы, полный loop остаётся итоговым gate |
| Bot+navigation+Observer должны появиться одновременно | Validator/executor невозможно проверить отдельно по прежнему правилу, хотя ручная цель — полноценный вход | P7.1 manual action, P7.2 path/motion, P7.3 Bot, P7.4 полный цикл |
| Blueprint §9.3 отправлял цель прямо Goal Scheduler, отсутствующему до следующей фазы | Потеря предложения или ложное исполнение | Атомарный Thoughts+GoalInbox; PENDING/expiry до consumer |
| Blueprint §26 держал count и bootstrap Open при уже существующем решении/evidence | Принуждение повторять уже завершённый дизайн/проверку | Canonical unit закрыта документально; runtime count tests в P2/P6. Bootstrap закрыт только по evidence |
| Outbox в старом roadmap требовал deliveries будущих consumers; recent-N мог удалить source до replay | Ненужная ранняя инфраструктура и недоопределённая потеря данных | Event log сразу; cursors/leases при activation, protected range, deletion outcome; ADR-0009 |
| Skill фиксировал старый широкий P0 и содержал некорректную URL-ссылку Fabric Networking | Агент возвращался к неподходящим gates; ссылка мешала переходу к networking documentation | Сохранены правильные `../../docs`, исправлена Fabric URL; добавлены A/B/C, bounded just-in-time design и source-of-truth |
| Workspace map/README утверждали «нет исходников/Wrapper» и «не клонировано» | Планирование начиналось с неверной исходной точки | PROJECT_MAP и workspace указатель отражают локальный dirty bootstrap |
| ADR-0006 «локальный Motion Controller», client animation layers vs host solver | Возможна ошибочная клиентская simulation и dedicated crash | Явно разделены host solving и client interpolation/skinning; библиотека проверяется headless в P3.1 |
| Универсальные build/two-client/API/restart проверки каждой фазы | Проверяется несуществующий functionality либо повторяется неизменённое evidence | Выбор по изменённому поведению; полный набор только после его реализации |

### Почему остановилась старая фаза 0

Подтверждённая последовательность: сначала build/unit PASS; dedicated достиг EULA gate; client ожидал asset download; после разрешённого запуска и исправления remote-only каталога integrated и dedicated two-client/restart прошли. Затем статус всё ещё In progress именно потому, что оставались renderer/motion/assets/storage по старому gate. Отчёт 002 преждевременно объявлял начало фазы 1, отчёт 003 вернул фазу 0; skill затем закрепил широкий gate. Это конкретная несовместимость границы этапа с реальным объёмом, а не вывод по времени или расходу модели.

Отдельно от плана были реальные implementation/environment проблемы: remote-only client не создавал каталоги (исправлен ДО этого аудита); первоначальный harness имел неверный unregister argument (исправлен ДО аудита); один запуск упал в native GLFW (причина неизвестна). Они не объясняют требование готового 3D в bootstrap. Повторные успешные проходы не доказывают устранения native причины.

Разбор восьми причин: (1) порядок — 3D до NPC; (2) избыточный exit — full motion demo; (3) преждевременная интеграция — pose/двое клиентов до pose pipeline; (4) скрытые основы — rig descriptor/importer/solver; (5) лишняя инфраструктура — stores/workers до writers; (6) неопределённость — общий gate ограничений; (7) смешение — исследование API, implementation и визуальная приёмка под одной фазой; (8) code/environment defects — перечислены отдельно и не маскируются планом.

## 3. Проверка всех прежних фаз и критериев

| Старая фаза | Оценка прежнего выхода и новая граница |
|---|---|
| 0 | Bootstrap доказан; renderer/motion/storage не доказаны. Новый P0 закрывается существующим evidence, остальные требования перенесены, не вычеркнуты |
| 1 | Config/protocol/facade без пользовательского результата; объединено с первой NPC, будущие workers исключены |
| 2 | Корректна независимость renderer, сохранена; добавлена явная world-scoped storage prerequisite P1.1 |
| 3 | Полезный, но крупный слой; повтор P0 устранён, разбит на пять результатов; реальный dialogue adapter доступен уже здесь |
| 4 | В основном порядок обоснован; внешний Registry source после dev source, passive metadata и addon version separation сохранены |
| 5 | LLM unnecessarily late; ранний диалог отделён от физической реакции без потери финальной связи |
| 6 | Требование всех шести stores до writers отклонено; каждый сохраняемый вид имеет producer/read path в P2/P5/P6 |
| 7 | Финальный feedback acceptance полезен, запрет промежуточных приёмок избыточен; поэтапные source snapshots разрывают development cycle |
| 8 | Финальный цель→план→действие→наблюдение цикл сохранён, готовится через реальное действие до Bot |
| 9 | TTS/audio/visemes совместно корректны; самостоятельная проверка не требует всей автономности |
| 10 | Широкая regression/load/soak уместна поздно; добавлен однозначный API freeze перед addons |
| 11 | Мир/addons сохранены отдельно от ядра; обязательный cleanup временных моделей перед full release сохранён |

Каждый прежний bullet, включая duplicate и дневной демонстратор, сопоставлен новому task ID в [PLAN_MIGRATION](../PLAN_MIGRATION.md). Там же входные зависимости, прямые потребители и собственные проверки новых задач. Произвольных «выполнено» для отсутствующих функций нет.

## 4. 3D: достижимость, evidence и оставшиеся риски

Архитектура разделяет mesh rendering, rig/pose math, motion arbitration, gameplay root movement и semantic brain intents. Такое разделение допускает скелет, FK/IK, masks/additive/crossfade, eyes/face, procedural reaction, host secondary physics, внешние модели и pose replication. **Это обоснованный путь, но не доказательство совместимости конкретной библиотеки или достижения качества/масштаба.** Библиотека renderer не обязана сама давать IK/physics: эти модули работают через stable rig/palette/morph interface, что проверяется до выбора.

Ограниченное исследование первичных источников на 2026-11-05:

| Кандидат | Что документировано | Что не доказано для ModeAgnor |
|---|---|---|
| GemRender | glTF skins/morphs, GPU skinning, blending/masks/additive; README описывает `GltfSkeletonReader`/`GltfSkeleton` без mesh/client ResourceManager для server pose. Target 1.21.1 Fabric использует Loader 0.19.3/API 0.116.15/Flywheel 1.0.6/Java21. LGPL-3.0-only | Наша сборка имеет Loader 0.16.10/API 0.102.1; runtime classloading и same rig binding не проверены. Integration guide предупреждает: Iris меняет shader path, PBR зависит от настроек pack, BLEND/transparency ограничены |
| BlendLib | Fabric beta3, 1.21.1 target, Loader >=0.19.5; GLB pose/animation API. Strict exporter v1 не допускает morph targets, max4 weights, требует bake неподдерживаемых modifiers. Runtime Apache-2.0, Blender add-on GPL-3.0-or-later | Не считать полной facial/morph заменой без доказанного альтернативного rig/extension. Наша dependency matrix не проверена; build/startup библиотеки не доказывает visual/world/multiplayer качество |
| Собственный adapter/backend | Позволяет сохранить независимые RigDescriptor/pose contracts | Сейчас реализации и оценки стоимости нет. Не выбирать по умолчанию и не начинать большой renderer в рамках аудита |

Источники: [GemRender README](https://github.com/Warfactory-Official/GemRender/blob/main/README.md) (file SHA `391d3fdc286a091e767d09e448fca9ba9745c2a4`), [Integration guide](https://github.com/Warfactory-Official/GemRender/blob/main/docs/INTEGRATION.md) (file SHA `bb0226bcfabccf4acdcc211606427000a2a883e1`), [BlendLib README](https://github.com/LIy-hub/BlendLib-Public/blob/main/README.md) (file SHA `3d4bf20190f7e1c5736a42f6a23936caa8cf3b71`), [strict exporter checklist](https://github.com/LIy-hub/BlendLib-Public/blob/main/docs/release/blender-export-checklist-v1.md) (file SHA `af716d71fd6cb388d4ac00bf585aa5d5bc71d938`). SHA здесь идентифицирует содержимое файла, не commit. Перед P3.1 выбрать и закрепить конкретный release/commit, повторно проверить его API/license/dependencies.

P3.1 отвечает только на вопрос: можно ли headless читать/вычислять нужный rig и передать стабильные bone/morph overrides в renderer на exact Fabric matrix? Малого разрешённого fixture достаточно; полная anime-модель, gait, physics и два pose-клиента — P3.2–P3.5. Изменять Loader/API сейчас без потребности bootstrap не требуется. Производительность host pose, solver fidelity, cloth clipping и shader compatibility остаются измеряемыми рисками. Загрузка моделей/прототип renderer в этом аудите не выполнялись.

## 5. AI и независимая проверка Blueprint

Технический каталог 1–29 сохранён. Новая часть 0 добавляет граф подсистем, state/I/O/error/persistence/performance/testability matrix и минимальные срезы; устранён дублирующий порядок фаз. Уточнены startup only enabled services, atomic goal handoff, accepted inbound vs pending response, event retention и server pose ownership. Detailed behavior не заменён коротким обзором.

Путь от текста к миру присутствует полностью: user dialogue/history → ContextBuilder → Thinker structured thoughts/emotion/goals → durable GoalInbox → GoalScheduler/Bot → typed whitelist validator/executor/navigation → Motion + world outcomes → Observer/ActionMemory → следующий Thinker. Memorizer читает только History/Thoughts; Judge формирует долговременный Diary; Summary имеет canonical count/watermark; local/cloud Gateway и независимые presets не дают raw LLM output доступ к миру/костям. Voice отдельный источник timing. Поэтому целевая система не сведена к чат-боту.

Циклы History↔Dialogue, Thoughts→Memorizer→Judge→Diary→Thinker, Summary→Context→Thinker и Actions→Observer→Memory→Thoughts являются **runtime feedback**, а не циклической build-зависимостью. Их разделяют snapshots, versions, bounded tasks, source statuses и durable inbox. Первый Thinker может использовать уже имеющиеся History/World/Runtime и явно неполный контекст; после Judge/Summary их данные включаются. Финальная P6/P7 обязана проверить полный цикл, dedupe/retry/stale/no-op/anti-loop. Отдельная проверка validator/summary threshold/ContextSource не требует всего мозга.

## 6. Замечания к коду, без исправления в этом запросе

| Наблюдение | Степень подтверждения | Следующая проверка/задача |
|---|---|---|
| `DataRoots.serverData` под общим gameDir, не world save; doc-comment может обещать больше факта | Подтверждено исходниками; приватных записей пока нет, утечка не установлена | P1.1/P1.2: world-scoped path до первой записи, не использовать legacy server folder |
| `Files.isWritable` не доказывает успешную durable write; нет ACL-denial теста; unused private noarg создаёт null fields | Подтверждён code debt/пробел, production failure не доказан | Реальная write/error/rollback проверка P1.2; dead constructor cleanup только при работе с классом |
| Probe — не auth/permissions/полный handshake | Подтверждено; read-only scope это допускает | P1.4 до mutating operations |
| Harness client-tick delays и burst не доказывают точную границу 19/20 server ticks; PASS до окончательного завершения | Подтверждено по коду; успешные записи учитывают также exit=0 | При изменении limiter добавить целевой boundary test, не делать вывода по одной строке PASS |
| `fabric.mod.json` допускает более широкие диапазоны, чем закреплённая tested build matrix | Подтверждена разница, несовместимость не доказана | P3.1 exact backend matrix; P9.1 release declarations по фактической поддержке |
| Wrapper не закрепляет distribution checksum | Подтверждён hardening gap, не текущая причина остановки | P9.1 build/release reproducibility review |
| Native `glfw.dll+0x101ab` crash первого clientA | Сбой подтверждён логом, root cause неизвестна | Сохранить evidence; при повторении расследовать окружение отдельно, P9 regression |

Нет основания объявлять выбранную server-authoritative архитектуру неработоспособной или заменять её. Нет evidence, что storage bug уже смешивает миры: storage ещё отсутствует. Код не исправлялся ради косметического закрытия замечаний.

## 7. Изменённые документы

- ROADMAP: P0–P10, малые task IDs, prerequisites/contracts/results/tests/exit/deferred/risks; A/B/C и чёткая P0.
- IMPLEMENTATION_BLUEPRINT: сохранён подробный каталог, вместо второго фазового плана — dependency/lifecycle/staging уточнения; полный старый план в archive.
- ARCHITECTURE, ADR-0001–0008 и новый ADR-0009/DECISIONS: актуальные этапы, ownership/outbox/cognition gates, отмена раннего full 3D spike. Платформа и цели не менялись.
- PLAN_MIGRATION: task-by-task трассировка; PROJECT_MAP/INDEX: фактическая структура и источники истины.
- PHASE_0_SPIKES, DEVELOPMENT_LOG, README и проектный skill: согласованные scope/status/next action и исправленные ссылки.
- Workspace `../УКАЗАТЕЛЬ.md`, `../README.md`: устранены неверные утверждения об отсутствии клона/кода/Wrapper.
- Asset Replacement Register: только новые обозначения release P9/P10, политика прав сохранена. Исторические отчёты не переписаны задним числом.

## 8. Непосредственное продолжение

**P1.1 — ограниченный выбор persistence backend и world-scoped storage contract.** Вход: существующий Java21/Fabric bootstrap; модель записи NPC UUID/position/state + отношения двух player UUID. Сначала прочитать локальные storage ограничения Blueprint §7/22 и актуальную документацию выбранного driver; сравнить SQLite и атомарный JSON/journal по одному и тому же сценарию. Прототип ограничить atomic update/rollback, restart, schema upgrade/backup и изоляцией двух world roots. Записать выбор/ограничения в ADR и определить Facade transaction/error contract.

Результат P1.1 — обоснованный backend/путь/контракт, достаточные для P1.2. Не требуются шесть repositories, vector index, gnome queues, renderer или модели. Затем P1.2 реализует минимальный Facade, P1.3 — настоящую NPC. **P1.1 в этом запросе не начата.**

## 9. Повторный контроль качества

Результаты конкретных документальных проверок дополняются ниже после последней правки. Runtime/game builds повторно не запускались: source/build hashes сравниваются, а новая функциональность не внесена. Проверка текста не заменяет будущую проверку storage/renderer/AI.


| Проверка | Результат и граница вывода |
|---|---|
| Хеши исходников, scripts, wrapper и build/config | PASS: все 18 исходных файлов совпадают, новых файлов в этих путях нет |
| Полнота Blueprint | PASS: сохранены все 29 технических разделов и 43 подраздела; прежний фазовый текст доступен целиком в архиве |
| Трассировка старого плана | PASS: 144 пункта из всех 12 старых фаз, без повторяющихся исходных ID; 39 новых task IDs |
| Task-level зависимости | PASS: у 39 задач нет ссылки на будущую/несуществующую задачу или на себя; межфазные prerequisites также направлены вперёд |
| Phase dependency graph | PASS: 17 рёбер, циклов нет. Runtime feedback в Blueprint намеренно отделён от implementation DAG |
| Локальные Markdown-ссылки | PASS: 168 путей проверены, отсутствующих целей нет; внешние ссылки здесь не считаются runtime evidence |
| Повторная смысловая сверка | Исправлены остаточная нижняя сводка журнала, ссылки на удалённые 0.10–0.11, старые bootstrap Open в ADR-0001/0002/0003 и ранние gates ADR-0006/0008. Правильный относительный путь skill — `../../docs/`; проверка устранила ошибку промежуточной редакции |
| `git diff --check` | PASS; отдельно проверены новые Markdown-файлы. Ветка/HEAD не изменены |
| Build/game/provider/renderer tests | Не запускались в этом аудите; приводится существующее evidence и явно назначенные будущие проверки |

Проверка сохранения конечных требований:

| Требование | Сохранённый контракт / этап |
|---|---|
| Полноценные anime mesh, материалы/текстуры/скелет/анимация | Blueprint §13–14, ADR-0006/0008, P3/P9 |
| Тело, голова, руки/ноги, глаза/мимика; IK/FK, blending и procedural реакции | §13, ADR-0007, P3.3–P3.5; не подменено vanilla анимацией |
| Secondary physics и физическое взаимодействие с миром | §12–13, P3.4 и P7; render pose не заменяет world action |
| LLM диалог, история, local/cloud providers | §5–6/10/24, P2 |
| Долговременная/динамическая память, мысли, пять агентов и шесть stores | §7–9, P5/P6 и Bot P7 |
| Самостоятельные решения, движение и реальные действия | §11–12/21, P7; финальный цикл обязателен |
| Связь мысли/эмоции/поведения с анимацией | §13.5, dialogue P3.5, Thinker P6, action P7, speech P8 |
| Сохранение между сессиями и world/NPC/player изоляция | §3/7/18/22, P1 и последующие реальные writers |
| Расширяемые настройки, персонажи, модели, presets и packs | §14/16/24, P1/P2/P4/P5/P9 |
| Голос и независимый lip-sync | §17, P8 |
| Одиночная игра и multiplayer/server authority | §1/15/18/25, проверки с каждой сетевой функцией и P9 |
| Авторский мир/необязательные addons и права | §27–28, P10; правила публичных ассетов действуют на всех этапах |

Доказан непрерывный документальный путь от текущего bootstrap к полной цели; его техническая реализация ещё предстоит. Неопределённости остаются: backend/driver (P1.1), provider capabilities (P2), renderer/headless/morph API и pose/physics budgets (P3), pack transfer policy (P4), retention/day/conflict/index и inference budgets (P6), navigation/reconciliation (P7), audio timing/transport (P8), реальная нагрузка/compatibility и native environment stability (P9). Каждая имеет владельца этапа и способ проверки; ни одна не объявлена решённой ради закрытия документации.
