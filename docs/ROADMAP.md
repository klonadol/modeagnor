# План разработки AGNOR

**Текущий статус:** проектирование. Этапы ниже — план, а не заявление о готовой реализации.

## Принципы планирования

1. Сначала рабочая вертикальная функция, затем расширение количества подсистем.
2. Мультиплеер, изоляция данных и серверная авторитетность проверяются с первого прототипа.
3. Не добавлять фоновые LLM-задачи, пока не доказано, что базовый диалог и runtime не блокируют игровой поток.
4. Каждая фаза имеет проверяемый выходной результат и отчёт в `docs/reports/`.
5. Успешная компиляция не равна готовности функции: проверяется пользовательский путь от действия игрока до отображаемого результата и сохранения.
6. Не менять тесты только ради зелёного CI. Ошибочный тест диагностируется и документируется.


## Связь с Implementation Blueprint

Перед началом проектирования и реализации каждой фазы используйте [IMPLEMENTATION_BLUEPRINT.md](IMPLEMENTATION_BLUEPRINT.md), раздел **0.6 «Порядок проектирования по фазам 0–11»**. Сверка исходных требований и выявленные пробелы находятся в разделах 0.5 и 0.8 Blueprint; подробные контракты компонентов — в последующих разделах. Критерии выхода этой дорожной карты остаются обязательными: Blueprint уточняет устройство и порядок проектирования, но не снижает и не заменяет критерии готовности фаз.

Переход к следующей фазе требует не только выполнения текущих задач, но и наличия описанных в Blueprint design artifacts, проверок и явно закрытых/перенесённых Open-вопросов.

## Фазы

### Фаза 0. Документация и технические проверки
**Статус:** документация создана; технические spikes не выполнены.

Задачи:
- Зафиксировать архитектурные решения.
- Создать минимальный Fabric 1.21.1 / Java 21 проект.
- Проверить запуск интегрированного и выделенного сервера.
- Проверить доступ к предполагаемому каталогу `mods/agnor/` на клиенте и сервере.
- Проверить базовый сетевой пакет и синхронизацию простого состояния.
- Сравнить GemRender, BlendLib и вариант собственного GLB renderer на минимальной rigged-модели.
- Проверить базовый semantic motion-task API: look-at, reach и turn-towards без отдельных готовых клипов на каждый случай. На Фазе 0 задачи подаются только тестовым scripted/synthetic driver: проверяется исполнитель движения, а не поведение ИИ-персонажа.
- Сравнить Two-Bone IK / FABRIK или CCD для конечностей и базовый whole-body priority solver.
- Проверить процедурную походку и серверное вычисление pose state на тестовом управлении без зависимости от диалога/гномиков.
- Проверить техническую поддержку skinning, runtime morph-weight control, animation blending, procedural bone overrides и secondary physics. Наличие morph API подтверждает возможность рендеринга лица, но не означает, что эмоции уже подключены к мозгу персонажа.
- Проверить, что host выполняет IK/pose/physics calculations, а клиенты только интерполируют серверные pose snapshots.
- Проверить лицензию, зависимости, Fabric 1.21.1 runtime и совместимость с Sodium/Iris на целевой сборке.
- Найти и проверить готовую тестовую rigged anime-модель с разрешённым использованием; если подходящей нет, использовать локальную временную модель по ADR-0008 и записать её в Asset Replacement Register. Не моделировать с нуля без необходимости.
- Дополнительно проверить один static mesh/GLB без подтверждённого рига и описать минимальную адаптацию.
- Использовать Blender для проверки и минимального ремонта/retargeting тестовых моделей, а не как обязательный этап создания геометрии.
- Зафиксировать зависимости и версии в отдельном отчёте.

Критерий выхода: воспроизводимая сборка, запуск на обеих серверных конфигурациях и подтверждённые технические ограничения.

**Цель короткого прототипного спринта — около одной недели**, при условии готовой тестовой модели/рига и доступного окружения. Это цель демонстратора, а не срок реализации полноценного качества ZZZ:
- День 1: Fabric client/server skeleton, GLB renderer spike и загрузка тестовой модели.
- День 2: чтение/изменение skeleton pose и минимальный Pose Controller.
- День 3: look-at, reach и IK.
- День 4: Whole Body coordination и базовая процедурная походка.
- День 5: простая spring-chain физика волос.
- День 6: host-authoritative pose calculation и отображение на двух клиентах.
- День 7: интеграция, измерения CPU/GPU/network, диагностика и отчёт.

Если библиотека не проходит обязательный тест, renderer не утверждается ради соблюдения срока: фиксируется фактический результат и следующий шаг.

### Фаза 1. Каркас мода и серверно-клиентская граница
Задачи:
- Fabric lifecycle и регистрация мода.
- Разделение common/server/client кода.
- Система конфигураций и каталогов.
- Network protocol с проверкой версий.
- Permissions и проверка отправителя.
- Минимальный экран настроек.
- Базовое логирование без секретов.
- Единый Storage Facade foundation: schema versioning, scope enforcement, transactions и migrations; все игровые и AI-хранилища следующих фаз используют этот интерфейс и выбранный в Фазе 0 backend.

Критерий выхода: мод запускается в одиночной игре и на dedicated server; клиент подключается; сервер валидирует запросы; сохранение конфигурации переживает перезапуск.

### Фаза 2. Character Runtime и первая NPC
Задачи:
- UUID NPC и Character Definition.
- Создание, удаление и загрузка NPC.
- Runtime State и жизненный цикл.
- Отдельные профили отношений по UUID игроков.
- Сохранение мира/NPC/игрока через общий Storage Facade.
- Базовая Character Definition schema и Registry со встроенным/dev definition source для первой NPC; внешний Content Pack Loader в Фазе 4 подключается к тому же Registry, без второй схемы персонажей.
- Memory/task scope на этой фазе — только стабильные ownership IDs; не создавать заранее memory repositories, Gnome Task Scheduler queues или AI workers, которые появятся в Фазах 6–8.
- Memory/task scope на этой фазе — только стабильные ownership IDs; не создавать заранее memory repositories, Gnome Task Scheduler queues или AI workers, которые появятся в Фазах 6–8.
- Создание/активация NPC на сервере не зависит от renderer или готовности клиентской модели: в Phase 2 сохраняются model/asset references, а клиенты показывают временный placeholder `PRESENTATION_NOT_READY` до подключения renderer в Phase 3.
- Минимальный тест двух игроков и одной NPC.

Критерий выхода: NPC и её server state создаются/сохраняются независимо от renderer и восстанавливаются после перезапуска; до Phase 3 два клиента видят одинаковый placeholder; два игрока имеют раздельные профили отношений; один мир не читает данные другого. Полноценное отображение модели не входит в критерий Phase 2.

### Правило временных моделей для фаз 0–4

Если подходящей модели с ясными правами нет, технические прототипы рендера/рига/анимации могут временно использовать локальный dev-only ассет со статусом `TEMPORARY_RESTRICTED`. Его можно включать в собственные локальные dev-сборки и локальные JAR/Content Packs, но нельзя коммитить в публичный Git, включать в публичные релизы или передавать другим игрокам. Источник, прямая ссылка на скачивание (если доступна), checksum и план замены фиксируются в Asset Replacement Register. Перед финальным релизом полной версии после переноса авторского мира в Minecraft (Фаза 11) все временные модели заменяются разрешёнными аналогами или удаляются. Первый самостоятельный релиз companion-мода (Фаза 10) из-за временных локальных моделей не блокируется.

### Фаза 3. Высококачественный 3D-персонаж и исполнительный слой движения
Задачи:
- Подобрать готовую внешнюю polygon-mesh модель с подходящим ригом и проверенными условиями использования.
- Сначала подключить модель напрямую, если её формат и возможности совместимы.
- Через Blender выполнить только необходимые repair/adaptation операции: scale, orientation, materials, rig mapping, skin weights, retargeting и facial mapping.
- Подготавливать собственную модель или недостающие части только если подходящего готового ассета нет.
- Реализовать импорт/загрузку выбранного runtime-формата.
- Реализовать animation state machine, crossfade, body-part masks и additive layers.
- Создать Agnor Motion Engine с semantic motion tasks, Task Planner и приоритетами/прерываниями.
- Реализовать Whole Body Coordinator и интерфейсы solver-ов.
- Реализовать Two-Bone IK, foot IK, look-at и joint limits.
- Реализовать процедурную походку: фаза шага, скорость, перенос веса, таз и движение рук.
- Поддержать сочетание процедурного движения с короткими авторскими клипами, а не клип на каждую комбинацию.
- Подключить технические контроллеры look-at, IK рук/ног, микродвижения и Face Controller. До появления диалога и гномиков управление ими выполняется только тестовым драйвером; это не поведенческая интеграция.
- Подготовить expression channels и test expressions, но не подключать осмысленные эмоции к персонажу: dialogue-driven reactions относятся к Фазе 5, внутренние эмоции Thinker — к Фазе 7.
- Не реализовывать полноценный lip-sync на этой фазе: для него необходим источник audio/viseme timing из Фазы 9.
- Реализовать host-side secondary motion для волос, подвесок и выбранных элементов одежды.
- Подготовить Motion Task API и тестовый harness так, чтобы позже к ним подключились Dialogue Presentation Adapter, Thinker/Affective Mapper и Bot/Navigation без переписывания renderer/solver.
- Реализовать Character Presentation Adapter, который подключает renderer/rig/pose controller к уже существующим NPC UUID и server snapshots из Фазы 2; не создавать вторую NPC identity/lifecycle и не делать renderer prerequisite для server runtime.
- Реализовать Pose Publisher и ограниченную по частоте/размеру синхронизацию pose snapshots.
- Проверить cloth/body clipping и столкновения с упрощёнными collision proxies.
- Синхронизировать authoritative gameplay state и визуальное состояние между клиентами.
- Профилировать GPU/CPU на одной и нескольких NPC.

Критерий выхода: тестовая модель через scripted test harness проходит idle, walk/run, start/stop, повороты, look-at, reach, жесты и тестовые facial expressions; анимационные слои смешиваются без заметных скачков; secondary motion реагирует на тестовое движение и не создаёт критичных пересечений; два клиента видят согласованное положение/состояние; производительность замерена и имеет определённые бюджеты. Этот выход подтверждает технический исполнительный слой, но НЕ означает, что персонаж уже живо реагирует на игрока, имеет осмысленную эмоциональную мимику или умеет говорить. Эти пользовательские функции подключаются после появления соответствующих источников поведения в фазах 5, 7, 8 и 9.

### Фаза 4. Content Packs
Задачи:
- Манифест и schema/compatibility version для декларативных Content Packs; эта версия не является executable Addon API version, которая стабилизируется после Фазы 8 и замораживается в Фазе 10.
- Сканирование, проверка, включение и отключение.
- Внешние модели, текстуры, анимации и определения персонажей.
- Metadata происхождения, автора, лицензии и разрешений для каждого ассета.
- Capability report импортированной модели и fallback для отсутствующих rig/face/physics channels.
- Подключение внешних Character Definition к Registry, созданному в Фазе 2; проверка ID/version и совместимости без создания параллельного реестра персонажей.
- Локальные пользовательские модели без обязательной раздачи другим клиентам.
- Приоритеты и переопределения ресурсов.
- Проверка checksum и версия пакета.
- Безопасная политика загрузки/отказа для клиента.

Критерий выхода: добавление нового тестового персонажа через внешний пакет без изменения JAR; готовая модель подключается без ручного моделирования с нуля; пакет с неизвестной лицензией не попадает в публичную сборку и не раздаётся клиентам; все временные restricted-ассеты внесены в реестр и имеют план замены; несовместимый/повреждённый пакет даёт понятную ошибку.

### Фаза 5. Provider Gateway и живой диалог
Задачи:
- Общий интерфейс LLM-провайдера.
- Пресеты и назначения по компонентам/NPC.
- Поддержка локальных endpoint и облачных API.
- Тайм-аут, retry, streaming при наличии поддержки.
- Диалоговый экран и контекст последних N сообщений (default 10).
- Единственный Context Builder с начальными источниками: Character Definition/system policy, Relationship/Runtime snapshot и durable Conversation History. Lorebook/Diary/Action Memory/Thoughts/Summary пока не активированы.
- Расширяемый Context Source Registry: Фаза 6 подключает memory stores через адаптеры к этому же сборщику контекста.
- Контекст каждой секции отличает NOT_YET_ENABLED от доступного, но пустого источника; отсутствие будущего store не маскируется под успешный пустой retrieval.
- Каждая durable write в Conversation History (accepted inbound message и finalized assistant response; streaming partial не включается) атомарно создаёт downstream event outbox record с message ID/range, scope, direction/status и sequence. Для каждой пары event ID + consumer ID ведётся отдельный delivery state (pending/claimed/completed, attempts/lease, idempotency key и свой watermark); обработка Memorizer не подтверждает событие за Summarizer или Thinker. Пока gnome consumers не активированы, deliveries остаются pending; Phase 7 replay-ит их независимо, без потери/повторов.
- Context Builder поддерживает отдельные dialogue/thinker profiles с собственными allowlists, scope, freshness и token budgets; это один engine, не отдельные копии сборщика контекста.
- Dialogue Presentation Adapter: реальные реакции на собеседника — поворот/взгляд на говорящего, listening/thinking/speaking states, ограниченные conversational expressions и простые жесты через Motion Engine.
- Передавать в Face/Body Controller только валидированные semantic states/expressions, а не сырые LLM-команды на кости/morph weights; при отсутствии metadata использовать нейтральную реакцию.
- Системные промты и роли сообщений.
- Защищённое хранение ключей на хосте.

Критерий выхода: игрок открывает диалог, сервер формирует контекст, вызывает настроенный провайдер асинхронно, ответ отображается клиенту; NPC действительно ориентируется на собеседника, показывает listening/speaking state и валидированную conversational reaction; ошибка/тайм-аут API не зависает на server thread. Это первая диалоговая реактивность, но не полная автономная жизнь NPC.

### Фаза 6. Шесть хранилищ и persistence
Задачи:
- Conversation History.
- Lorebook.
- Diary.
- Action Memory.
- Thoughts.
- Daily Summary.
- Схема SQLite/JSON, миграции, резервные копии.
- Изоляция world/NPC/player.
- Индексация и правила retention.
- Exact/keyword/Always Lorebook activation работает в этой фазе; semantic-only retrieval помечается NOT_YET_ENABLED до Embedding adapter Фазы 7, чтобы не выдавать отсутствие провайдера за пустую выдачу.
- Все хранилища используют единый Storage Facade и backend из Фазы 0/1; Conversation History расширяется, а не переносится во вторую БД.
- Подготовить Action Memory и Thoughts schema/API, но не считать их end-to-end функциями до подключения источников и потребителей в Фазе 7.

Критерий выхода: записи не смешиваются между NPC, игроками и мирами; все хранилища восстанавливаются после перезапуска; история соблюдает настраиваемый лимит.

### Фаза 7. Когнитивные гномики и планировщик

Задачи:
- Реализовать ограниченный World Event Feed Foundation, который собирает server-side события, необходимые Thinker и Action Memory: Phase 2 NPC lifecycle hooks, Phase 5 transactional Conversation History outbox, вход/выход/приближение игрока, существенные наблюдаемые взаимодействия и важные изменения NPC. Это источник данных, не полный автономный Observer и не action executor.
- Подключить и replay накопившиеся Phase 5 dialogue outbox records: каждая delivery обрабатывается отдельно по event ID + consumer ID, с собственным claim/lease, idempotency и watermark commit. Завершение одного consumer не закрывает delivery остальных; restart не теряет и не дублирует обработку.
- Реализовать Gnome Task Scheduler: bounded queues, приоритеты, per-gnome/per-provider limits, deduplication, cancellation, retry/backoff, checkpoint и metrics. Это очередь фоновых AI jobs; Goal Scheduler игровых целей появляется в Фазе 8 и является отдельной системой.
- Совместно подключить Memorizer (History + Thoughts) и Thinker (создаёт/обновляет Thoughts); ThoughtChanged, watermark и защита от stale results проверяются как одна связка.
- Подключить Judge к candidate pipeline и Diary; Thinker использует Diary только после того, как candidate → Judge → transactional Diary write реально подключены.
- Подключить Summarizer к finalized Conversation History и доступным Event Feed/Action Memory событиям; Thinker получает Summary через тот же Context Builder.
- Embedding adapter/profile и независимые provider presets активируемых когнитивных гномиков: Memorizer, Judge, Thinker, Summarizer. Bot-specific provider preset подключается вместе с самим Bot в Фазе 8.
- Affective Presentation Mapper: валидированные emotion/intensity/attention/desire состояния Thinker передаются в Face/Body Controller с smoothing, expiry и fallback; не давать LLM управление костями.
- Goal proposals Thinker сохраняются в durable Goal Proposal Inbox/Outbox через Storage Facade с ID/scope/evidence/expiry/status=PENDING; запись связана с Thought, это не седьмое хранилище памяти. В этой фазе они не исполняются и не меняют игровой мир; Phase 8 claims/validates их атомарно и не исполняет повторно.
- Ручной запуск/отладка из меню: permission check, preview/dry-run, status, cancel/retry и безопасная диагностика.
- Определить Bot typed plan/action-intent contract. Сам Bot как пятый работающий гномик переносится в совместную реализацию Фазы 8, когда готовы Goal Scheduler и Action Executor.

**Критерий выхода:** единый end-to-end цикл Event Feed → Action Memory → Thinker → Thoughts → Memorizer → Judge → Diary → Thinker, плюс Summarizer и Context Builder. Гномики не принимаются как набор отдельных изолированных сервисов: проверяются циклические связи, retry/restart, watermark, scope isolation и деградация. Goal proposals сохраняются, но не исполняются до Фазы 8. Ошибка одного provider не ломает основной диалог.

### Фаза 8. Автономность: Bot, Goal Scheduler, World Observer и действия

Задачи:
- Расширить Event Feed Фазы 7 до полного World Observer: event catalogue, actor/target/outcome/location/time/visibility, relevance, durability, deduplication и coalescing.
- Реализовать Goal Scheduler, который забирает pending proposals из Goal Proposal Inbox, проверяет expiry, evidence, preconditions, priority, dependencies и правила прерывания.
- Реализовать Bot как пятого гномика: преобразование выбранной цели в typed action plan; его асинхронный inference dispatch использует Gnome Task Scheduler, а игровые цели и actions — отдельный Goal Scheduler/Action Runtime.
- Подключить отдельный Bot provider preset/capability validation, если Bot использует LLM для планирования; исполнение validated plan не вызывает модель на каждом игровом тике.
- Одновременно реализовать Action Registry, Action Validator и Action Executor: whitelist, schema, permissions, distance/range, cooldown, world preconditions, idempotency, cancellation и uncertain-result reconciliation.
- Реализовать Navigation/Follow: pathfinding, moving target, repath, stuck detection, obstacles, unloaded chunks, stop/cancel и arrival predicate.
- Подключить Navigation root movement/velocity к Motion Engine; заменить synthetic inputs Фазы 3 на реальные path/target/action inputs.
- Подключить фактические action outcomes и World Observer к Action Memory и Thinker; обновлять цели после успехов, ошибок и внешних событий.
- Обеспечить event/action deduplication и anti-loop: собственные действия NPC не должны бесконечно запускать сами себя.
- Multiplayer visibility, permissions и server authority для всех автономных действий.

**Важно:** Bot, Goal Scheduler, полный World Observer, Action Validator/Executor, Navigation и Motion bridge реализуются и принимаются как единая вертикальная функция. Их нельзя объявлять готовыми по отдельности, потому что они образуют цикл: цель → план → проверка → действие → движение → наблюдаемый результат → память/Thinker → следующая цель.

**Критерий выхода:** NPC самостоятельно выполняет разрешённые цели; Thinker/Bot proposals доходят до реальных проверенных действий и движений; препятствия, moving targets, отмена, смена цели, повтор доставки, side-effect retry и мультиплеерная авторитетность обработаны. Gaze, gesture, locomotion и emotion корректно смешиваются и прерываются. Это первая фаза полноценного автономного поведения.

### Фаза 9. Голос
Задачи:
- TTS adapters и голосовые пресеты.
- Назначение голоса каждому NPC.
- Очередь аудио, прерывание и кэш.
- Воспроизведение на клиентах.
- Подключить TTS/audio viseme timeline к Face Controller и смешивать lip-sync с эмоциональной мимикой по независимым каналам.
- Реализовать синхронизацию рта с реальным playback timing, а не случайным mouth animation.
- Безопасная деградация при недоступности TTS.

Критерий выхода: текстовый диалог остаётся работоспособным без TTS; голос воспроизводится согласованно у подключённых игроков в рамках выбранной сетевой политики; движение губ синхронизировано с audio/viseme timing, а lip-sync не перетирает эмоциональные expression channels.

### Фаза 10. Полировка и первый самостоятельный релиз
Задачи:
- Полный мультиплеерный smoke/regression suite.
- Нагрузочные тесты нескольких NPC и игроков.
- Проверка долгих сессий и перезапуска.
- Обработка ошибок и миграций.
- Настройки производительности и расходов API.
- Инструкции установки и создания пакетов.
- Проверка совместимости с распространёнными модами по матрице тестов.

Критерий выхода: законченный AI companion mod без зависимости от мира Агнор или Create; пройдены документированные пользовательские сценарии; известные ограничения опубликованы.

### Фаза 11. Перенос авторского мира и финальный релиз полной версии
Задачи:
- Перенос мира Агнор в Minecraft.
- Дополнительные пакеты персонажей.
- Addon-интеграции с другими модами.
- После завершения Action/Goal/Event API в Фазе 8 и compatibility hardening в Фазе 10 заморозить стабильный versioned Optional Addon API.
- Отдельный Create adapter после проверки доступной версии и loader compatibility.
- Расширенная голосовая коммуникация и дополнительные виды интеракций.
- Перед финальным релизом полной версии заменить временные модели из Asset Replacement Register на собственные или разрешённые аналоги либо удалить их.

Критерий выхода: авторский мир перенесён в Minecraft, временные модели заменены или удалены, а публичный дистрибутив содержит только материалы, которые разрешено распространять.

## Сквозные проверки на каждой фазе

- Build и тесты соответствующего модуля.
- Одиночный мир: действие → UI → server state → сохранение → повторная загрузка.
- Dedicated server: подключение минимум двух игроков и проверка их изоляции.
- Проверка API timeout/error и отсутствие блокировки server thread.
- Проверка сетевого доступа: клиент не получает секреты и чужие приватные данные.
- Проверка совместимости: изменение только необходимых точек интеграции.
- Отчёт о работе и обновление журнала.

## Правила обновления плана

Любое изменение порядка фаз или критерия выхода должно сопровождаться:
1. отдельным отчётом в `docs/reports/`;
2. обновлением этого файла;
3. ADR, если изменение влияет на архитектурное решение;
4. обновлением [журнала](DEVELOPMENT_LOG.md).