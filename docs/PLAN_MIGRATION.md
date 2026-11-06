# Матрица переноса и аудит прежних задач

Старые номера относятся к [снимку ROADMAP](archive/ROADMAP_2026-11-04.md), новые P-ID — к действующему [плану](ROADMAP.md). Каждая исходная задача сохранена ниже, включая дубли и семидневный демонстратор. Текст старой строки — цитата исторического требования, не новый gate.

Новая задача в ROADMAP задаёт конкретную функцию, входные зависимости, проверку А/Б и критерий результата; все потребители перечислены в таблице контрактов ниже. Несколько назначений в одной строке означают разделение старой крупной задачи. Отдельная проверка допустима по своему срезу, финальная интеграция — только в его фазе.

## Прежняя фаза 0

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 0.1 — Зафиксировать архитектурные решения. | P0.1 | Оставлено в P0: подтверждённая минимальная основа; доказательства ограничены bootstrap/probe. |
| 0.2 — Создать минимальный Fabric 1.21.1 / Java 21 проект. | P0.1 | Оставлено в P0: подтверждённая минимальная основа; доказательства ограничены bootstrap/probe. |
| 0.3 — Проверить запуск интегрированного и выделенного сервера. | P0.2 | Оставлено в P0: подтверждённая минимальная основа; доказательства ограничены bootstrap/probe. |
| 0.4 — Проверить доступ к предполагаемому каталогу `mods/agnor/` на клиенте и сервере. | P0.2 | Оставлено в P0: подтверждённая минимальная основа; доказательства ограничены bootstrap/probe. |
| 0.5 — Проверить базовый сетевой пакет и синхронизацию простого состояния. | P0.3 | Оставлено в P0: подтверждённая минимальная основа; доказательства ограничены bootstrap/probe. |
| 0.6 — Сравнить GemRender, BlendLib и вариант собственного GLB renderer на минимальной rigged-модели. | P3.1 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.7 — Проверить базовый semantic motion-task API: look-at, reach и turn-towards без отдельных готовых клипов на каждый случай. На Фазе 0 задачи подаются только тестовым scripted/synthetic driver: проверяется исполнитель движения, а не поведение ИИ-персонажа. | P3.3 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.8 — Сравнить Two-Bone IK / FABRIK или CCD для конечностей и базовый whole-body priority solver. | P3.3 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.9 — Проверить процедурную походку и серверное вычисление pose state на тестовом управлении без зависимости от диалога/гномиков. | P3.4 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.10 — Проверить техническую поддержку skinning, runtime morph-weight control, animation blending, procedural bone overrides и secondary physics. Наличие morph API подтверждает возможность рендеринга лица, но не означает, что эмоции уже подключены к мозгу персонажа. | P3.1, P3.3, P3.4 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.11 — Проверить, что host выполняет IK/pose/physics calculations, а клиенты только интерполируют серверные pose snapshots. | P3.5 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.12 — Проверить лицензию, зависимости, Fabric 1.21.1 runtime и совместимость с Sodium/Iris на целевой сборке. | P3.1, P3.5 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.13 — Найти и проверить готовую тестовую rigged anime-модель с разрешённым использованием; если подходящей нет, использовать локальную временную модель по ADR-0008 и записать её в Asset Replacement Register. Не моделировать с нуля без необходимости. | P3.2 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.14 — Дополнительно проверить один static mesh/GLB без подтверждённого рига и описать минимальную адаптацию. | P3.2 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.15 — Использовать Blender для проверки и минимального ремонта/retargeting тестовых моделей, а не как обязательный этап создания геометрии. | P3.2 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.16 — Зафиксировать зависимости и версии в отдельном отчёте. | P0.1, P3.1 | Обязательность раннего gate снята: выбор API перед 3D, полноценная motion/physics реализация — отдельные срезы P3. |
| 0.17 — День 1: Fabric client/server skeleton, GLB renderer spike и загрузка тестовой модели. | P0.1, P3.1 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.18 — День 2: чтение/изменение skeleton pose и минимальный Pose Controller. | P3.2 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.19 — День 3: look-at, reach и IK. | P3.3 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.20 — День 4: Whole Body coordination и базовая процедурная походка. | P3.3, P3.4 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.21 — День 5: простая spring-chain физика волос. | P3.4 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.22 — День 6: host-authoritative pose calculation и отображение на двух клиентах. | P3.5 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |
| 0.23 — День 7: интеграция, измерения CPU/GPU/network, диагностика и отчёт. | P3.5 | Отменено календарное обязательство семидневного demo; функции сохранены в соответствующих задачах без повторного прототипирования. |

## Прежняя фаза 1

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 1.1 — Fabric lifecycle и регистрация мода. | P0.1 | Уже выполнено bootstrap; повторный каркас не нужен, только дальнейшее расширение при изменении lifecycle. |
| 1.2 — Разделение common/server/client кода. | P0.1 | Уже выполнено bootstrap; повторный каркас не нужен, только дальнейшее расширение при изменении lifecycle. |
| 1.3 — Система конфигураций и каталогов. | P1.2 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |
| 1.4 — Network protocol с проверкой версий. | P1.4 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |
| 1.5 — Permissions и проверка отправителя. | P1.4 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |
| 1.6 — Минимальный экран настроек. | P1.4 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |
| 1.7 — Базовое логирование без секретов. | P1.4, P2.1 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |
| 1.8 — Единый Storage Facade foundation: schema versioning, scope enforcement, transactions и migrations; все игровые и AI-хранилища следующих фаз используют этот интерфейс и выбранный в Фазе 0 backend. | P1.1, P1.2 | Foundation создаётся с первым реальным consumer; уже подтверждённый bootstrap не реализуется повторно. |

## Прежняя фаза 2

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 2.1 — UUID NPC и Character Definition. | P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.2 — Создание, удаление и загрузка NPC. | P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.3 — Runtime State и жизненный цикл. | P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.4 — Отдельные профили отношений по UUID игроков. | P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.5 — Сохранение мира/NPC/игрока через общий Storage Facade. | P1.2, P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.6 — Базовая Character Definition schema и Registry со встроенным/dev definition source для первой NPC; внешний Content Pack Loader в Фазе 4 подключается к тому же Registry, без второй схемы персонажей. | P1.3, P4.2 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.7 — Memory/task scope на этой фазе — только стабильные ownership IDs; не создавать заранее memory repositories, Gnome Task Scheduler queues или AI workers, которые появятся в Фазах 6–8. | P1.3 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.8 — Memory/task scope на этой фазе — только стабильные ownership IDs; не создавать заранее memory repositories, Gnome Task Scheduler queues или AI workers, которые появятся в Фазах 6–8. | P1.3 | Точный дубль предыдущего пункта удалён; ownership требование сохранено один раз. |
| 2.9 — Создание/активация NPC на сервере не зависит от renderer или готовности клиентской модели: в Phase 2 сохраняются model/asset references, а клиенты показывают временный placeholder `PRESENTATION_NOT_READY` до подключения renderer в Phase 3. | P1.3, P3.2 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |
| 2.10 — Минимальный тест двух игроков и одной NPC. | P1.4 | Runtime объединён с его persistence/операциями в первый пользовательский срез; renderer независим. |

## Прежняя фаза 3

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 3.1 — Подобрать готовую внешнюю polygon-mesh модель с подходящим ригом и проверенными условиями использования. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.2 — Сначала подключить модель напрямую, если её формат и возможности совместимы. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.3 — Через Blender выполнить только необходимые repair/adaptation операции: scale, orientation, materials, rig mapping, skin weights, retargeting и facial mapping. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.4 — Подготавливать собственную модель или недостающие части только если подходящего готового ассета нет. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.5 — Реализовать импорт/загрузку выбранного runtime-формата. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.6 — Реализовать animation state machine, crossfade, body-part masks и additive layers. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.7 — Создать Agnor Motion Engine с semantic motion tasks, Task Planner и приоритетами/прерываниями. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.8 — Реализовать Whole Body Coordinator и интерфейсы solver-ов. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.9 — Реализовать Two-Bone IK, foot IK, look-at и joint limits. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.10 — Реализовать процедурную походку: фаза шага, скорость, перенос веса, таз и движение рук. | P3.4 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.11 — Поддержать сочетание процедурного движения с короткими авторскими клипами, а не клип на каждую комбинацию. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.12 — Подключить технические контроллеры look-at, IK рук/ног, микродвижения и Face Controller. До появления диалога и гномиков управление ими выполняется только тестовым драйвером; это не поведенческая интеграция. | P3.3, P3.5 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.13 — Подготовить expression channels и test expressions, но не подключать осмысленные эмоции к персонажу: dialogue-driven reactions относятся к Фазе 5, внутренние эмоции Thinker — к Фазе 7. | P3.3, P3.5, P6.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.14 — Не реализовывать полноценный lip-sync на этой фазе: для него необходим источник audio/viseme timing из Фазы 9. | P8.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.15 — Реализовать host-side secondary motion для волос, подвесок и выбранных элементов одежды. | P3.4 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.16 — Подготовить Motion Task API и тестовый harness так, чтобы позже к ним подключились Dialogue Presentation Adapter, Thinker/Affective Mapper и Bot/Navigation без переписывания renderer/solver. | P3.3 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.17 — Реализовать Character Presentation Adapter, который подключает renderer/rig/pose controller к уже существующим NPC UUID и server snapshots из Фазы 2; не создавать вторую NPC identity/lifecycle и не делать renderer prerequisite для server runtime. | P3.2 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.18 — Реализовать Pose Publisher и ограниченную по частоте/размеру синхронизацию pose snapshots. | P3.5 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.19 — Проверить cloth/body clipping и столкновения с упрощёнными collision proxies. | P3.4 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.20 — Синхронизировать authoritative gameplay state и визуальное состояние между клиентами. | P3.5 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |
| 3.21 — Профилировать GPU/CPU на одной и нескольких NPC. | P3.5 | Цель сохранена; технические capability, pose и реальные источники намерений разделены на проверяемые задачи. |

## Прежняя фаза 4

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 4.1 — Манифест и schema/compatibility version для декларативных Content Packs; эта версия не является executable Addon API version, которая стабилизируется после Фазы 8 и замораживается в Фазе 10. | P4.1 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.2 — Сканирование, проверка, включение и отключение. | P4.1, P4.2 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.3 — Внешние модели, текстуры, анимации и определения персонажей. | P4.2 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.4 — Metadata происхождения, автора, лицензии и разрешений для каждого ассета. | P4.1 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.5 — Pack prompts, voice descriptors и declarative behavior settings загружаются как пассивные данные; они не исполняются при сканировании/активации пакета и становятся доступными соответствующим сервисам только при их включении в фазах 5/7/8/9. | P4.2 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.6 — Capability report импортированной модели и fallback для отсутствующих rig/face/physics channels. | P4.2 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.7 — Подключение внешних Character Definition к Registry, созданному в Фазе 2; проверка ID/version и совместимости без создания параллельного реестра персонажей. | P4.2 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.8 — Локальные пользовательские модели без обязательной раздачи другим клиентам. | P4.3 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.9 — Приоритеты и переопределения ресурсов. | P4.1 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.10 — Проверка checksum и версия пакета. | P4.1 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |
| 4.11 — Безопасная политика загрузки/отказа для клиента. | P4.3 | Сохранено после runtime/renderer; используется существующий Registry, пассивные данные активируются с consumer. |

## Прежняя фаза 5

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 5.1 — Общий интерфейс LLM-провайдера. | P2.1 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.2 — Preset Registry и назначения по компонентам/NPC; в Phase 5 активируется только Main Dialogue preset. Preset slots для gnomes/embedding включаются в Phase 7, Bot — в Phase 8, TTS — в Phase 9; отсутствие будущих consumers/presets не блокирует чат. | P2.1 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.3 — Поддержка локальных endpoint и облачных API. | P2.4 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.4 — Тайм-аут, retry, streaming при наличии поддержки. | P2.1, P2.4 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.5 — Диалоговый экран и контекст последних N сообщений (default 10). | P2.2, P2.3 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.6 — Единственный Context Builder с начальными источниками: Character Definition/system policy, Relationship/Runtime snapshot и durable Conversation History. Lorebook/Diary/Action Memory/Thoughts/Summary пока не активированы. | P2.2 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.7 — Расширяемый Context Source Registry: Фаза 6 подключает memory stores через адаптеры к этому же сборщику контекста. | P2.2, P5.2 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.8 — Контекст каждой секции отличает NOT_YET_ENABLED от доступного, но пустого источника; отсутствие будущего store не маскируется под успешный пустой retrieval. | P2.2 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.9 — Каждая durable write в Conversation History (accepted inbound message и finalized assistant response; streaming partial не включается) атомарно создаёт downstream event outbox record с message ID/range, scope, direction/status и sequence. Для каждой пары event ID + consumer ID ведётся отдельный delivery state (pending/claimed/completed, attempts/lease, idempotency key и свой watermark); обработка Memorizer не подтверждает событие за Summarizer или Thinker. Пока gnome consumers не активированы, deliveries остаются pending; Phase 7 replay-ит их независимо, без потери/повторов. | P2.3, P6.3 | Атомарный History+event нужен сразу; per-consumer delivery/leases и workers — только при активации P6, protected replay range сохраняется. |
| 5.10 — Context Builder поддерживает отдельные dialogue/thinker profiles с собственными allowlists, scope, freshness и token budgets; это один engine, не отдельные копии сборщика контекста. | P2.2, P6.2 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.11 — Dialogue Presentation Adapter: реальные реакции на собеседника — поворот/взгляд на говорящего, listening/thinking/speaking states, ограниченные conversational expressions и простые жесты через Motion Engine. | P3.5 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.12 — Передавать в Face/Body Controller только валидированные semantic states/expressions, а не сырые LLM-команды на кости/morph weights; при отсутствии metadata использовать нейтральную реакцию. | P3.5 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.13 — Системные промты и роли сообщений. | P2.2 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |
| 5.14 — Защищённое хранение ключей на хосте. | P2.1 | Диалог перенесён раньше 3D; visual reaction подключается к готовому диалогу отдельно, future consumers его не блокируют. |

## Прежняя фаза 6

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 6.1 — Conversation History. | P2.2 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.2 — Lorebook. | P5.1 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.3 — Diary. | P6.3 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.4 — Action Memory. | P6.1 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.5 — Thoughts. | P6.2 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.6 — Daily Summary. | P6.4 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.7 — Схема SQLite/JSON, миграции, резервные копии. | P1.1, P1.2, P5.3 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.8 — Изоляция world/NPC/player. | P1.2 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.9 — Индексация и правила retention. | P5.3, P6.5 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.10 — Exact/keyword/Always Lorebook activation работает в этой фазе; semantic-only retrieval помечается NOT_YET_ENABLED до Embedding adapter Фазы 7, чтобы не выдавать отсутствие провайдера за пустую выдачу. | P5.1, P6.5 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.11 — Все хранилища используют единый Storage Facade и backend из Фазы 0/1; Conversation History расширяется, а не переносится во вторую БД. | P1.2 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.12 — Подготовить Action Memory и Thoughts schema/API, но не считать их end-to-end функциями до подключения источников и потребителей в Фазе 7. | P6.1, P6.2 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |
| 6.13 — Зафиксировать единицу message count для History и Summarizer: одна durable History record — одно сообщение; accepted inbound player message и finalized assistant reply считаются отдельно. Streaming partial, system prompt, retries и uncommitted interrupted partial не считаются. Default summary threshold — 10 committed records; точный диапазон определяется sequence/watermark. | P2.2, P6.4 | Уже согласованное правило count фиксируется с History, а не после её writer; реальная threshold integration в P6. |
| 6.14 — Точное определение периода Daily Summary (Minecraft day или calendar/server-local day) закрыть до реализации Summarizer в Фазе 7. | P6.4 | Store создаётся с writer и полезным read path, а не как пустая инфраструктура; общий storage не дублируется. |

## Прежняя фаза 7

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 7.1 — Реализовать ограниченный World Event Feed Foundation, который собирает server-side world/NPC события для Thinker и Action Memory: NPC lifecycle hooks, вход/выход/приближение игрока, существенные наблюдаемые взаимодействия и важные изменения NPC. При включении feed строит baseline snapshot текущего состояния; события до его активации не реконструируются задним числом. После активации он пишет typed world/action events; содержимое диалоговых сообщений остаётся в Conversation History и не копируется в Action Memory. Это не полный автономный Observer и не action executor. | P6.1 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.2 — Подключить и replay накопившиеся Phase 5 dialogue outbox records: каждая delivery обрабатывается отдельно по event ID + consumer ID, с собственным claim/lease, idempotency и watermark commit. Завершение одного consumer не закрывает delivery остальных; restart не теряет и не дублирует обработку. | P6.3 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.3 — Применять consumer-specific trigger policy: Memorizer batch-ит новые finalized History ranges; Summarizer учитывает каждый committed message record и создаёт задачу только при переходе watermark через threshold 10; Thinker запускается по ConversationTurnFinalized, значимым world/NPC events и периодическому интервалу около 30 секунд, объединяя частые triggers в одну pending task. Consumer delivery ack допускается только вместе с durable task/aggregation update. | P6.2, P6.3, P6.4 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.4 — Реализовать Gnome Task Scheduler: bounded queues, приоритеты, per-gnome/per-provider limits, deduplication, cancellation, retry/backoff, checkpoint и metrics. Это очередь фоновых AI jobs; Goal Scheduler игровых целей появляется в Фазе 8 и является отдельной системой. | P6.2 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.5 — Совместно подключить Memorizer (History + Thoughts) и Thinker (создаёт/обновляет Thoughts); ThoughtChanged, watermark и защита от stale results проверяются как одна связка. | P6.2, P6.3 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.6 — Подключить Judge к candidate pipeline и Diary; Thinker использует Diary только после того, как candidate → Judge → transactional Diary write реально подключены. | P6.3 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.7 — Подключить Summarizer к finalized Conversation History и доступным Event Feed/Action Memory событиям; Thinker получает Summary через тот же Context Builder. | P6.4 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.8 — Embedding adapter/profile и независимые provider presets активируемых когнитивных гномиков: Memorizer, Judge, Thinker, Summarizer. Bot-specific provider preset подключается вместе с самим Bot в Фазе 8. В этой фазе становятся активными соответствующие gnome destinations Provider Gateway; Main Dialogue preset сохраняет независимость. | P6.2, P6.3, P6.4, P6.5 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.9 — Affective Presentation Mapper: валидированные emotion/intensity/attention/desire состояния Thinker передаются в Face/Body Controller с smoothing, expiry и fallback; не давать LLM управление костями. | P6.2 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.10 — Goal proposals Thinker сохраняются в durable Goal Proposal Inbox/Outbox через Storage Facade с ID/scope/evidence/expiry/status=PENDING; запись связана с Thought, это не седьмое хранилище памяти. В этой фазе они не исполняются и не меняют игровой мир; Phase 8 claims/validates их атомарно и не исполняет повторно. | P6.2 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.11 — Ручной запуск/отладка из меню: permission check, preview/dry-run, status, cancel/retry и безопасная диагностика. | P6.5 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |
| 7.12 — Определить Bot typed plan/action-intent contract. Сам Bot как пятый работающий гномик переносится в совместную реализацию Фазы 8, когда готовы Goal Scheduler и Action Executor. | P7.1, P7.3 | Runtime feedback разрезан по snapshots/наличию sources; срезы проверяются отдельно, итоговый цикл обязателен. |

## Прежняя фаза 8

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 8.1 — Расширить Event Feed Фазы 7 до полного World Observer: event catalogue, actor/target/outcome/location/time/visibility, relevance, durability, deduplication и coalescing. | P7.4 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.2 — Реализовать Goal Scheduler, который забирает pending proposals из Goal Proposal Inbox, проверяет expiry, evidence, preconditions, priority, dependencies и правила прерывания. | P7.3 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.3 — Реализовать Bot как пятого гномика: преобразование выбранной цели в typed action plan; его асинхронный inference dispatch использует Gnome Task Scheduler, а игровые цели и actions — отдельный Goal Scheduler/Action Runtime. Здесь активируется Bot-specific provider preset, если LLM используется для planning. | P7.3 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.4 — Подключить отдельный Bot provider preset/capability validation, если Bot использует LLM для планирования; исполнение validated plan не вызывает модель на каждом игровом тике. | P7.3 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.5 — Одновременно реализовать Action Registry, Action Validator и Action Executor: whitelist, schema, permissions, distance/range, cooldown, world preconditions, idempotency, cancellation и uncertain-result reconciliation. | P7.1 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.6 — Реализовать Navigation/Follow: pathfinding, moving target, repath, stuck detection, obstacles, unloaded chunks, stop/cancel и arrival predicate. | P7.2 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.7 — Подключить Navigation root movement/velocity к Motion Engine; заменить synthetic inputs Фазы 3 на реальные path/target/action inputs. | P7.2 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.8 — Подключить фактические action outcomes и World Observer к Action Memory и Thinker; обновлять цели после успехов, ошибок и внешних событий. | P7.4 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.9 — Обеспечить event/action deduplication и anti-loop: собственные действия NPC не должны бесконечно запускать сами себя. | P7.4 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |
| 8.10 — Multiplayer visibility, permissions и server authority для всех автономных действий. | P7.4 | Сначала настоящее валидируемое действие и navigation, затем Bot/Goal; полный цикл остаётся exit фазы. |

## Прежняя фаза 9

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 9.1 — TTS adapters и голосовые пресеты; здесь активируется TTS destination/preset Provider Gateway, ранее существовавший только как конфигурационный slot. | P8.1 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.2 — Назначение голоса каждому NPC. | P8.1 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.3 — Очередь аудио, прерывание и кэш. | P8.1, P8.2 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.4 — Воспроизведение на клиентах. | P8.2 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.5 — Подключить TTS/audio viseme timeline к Face Controller и смешивать lip-sync с эмоциональной мимикой по независимым каналам. | P8.2 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.6 — Реализовать синхронизацию рта с реальным playback timing, а не случайным mouth animation. | P8.2 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |
| 9.7 — Безопасная деградация при недоступности TTS. | P8.1 | TTS/timing/face остаются совместной функцией; не требуют готового Bot для независимой проверки. |

## Прежняя фаза 10

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 10.1 — Полный мультиплеерный smoke/regression suite. | P9.1 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.2 — Нагрузочные тесты нескольких NPC и игроков. | P9.2 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.3 — Проверка долгих сессий и перезапуска. | P9.2 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.4 — Обработка ошибок и миграций. | P9.1 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.5 — Настройки производительности и расходов API. | P9.3 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.6 — Инструкции установки и создания пакетов. | P9.3 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |
| 10.7 — Проверка совместимости с распространёнными модами по матрице тестов. | P9.1 | Широкая регрессия после появления функций; early checks остаются локальными по затронутому контракту. |

## Прежняя фаза 11

| Старый ID и исходная задача | Новые задачи | Техническая причина / оценка |
|---|---|---|
| 11.1 — Перенос мира Агнор в Minecraft. | P10.1 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |
| 11.2 — Дополнительные пакеты персонажей. | P10.1 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |
| 11.3 — Addon-интеграции с другими модами. | P10.2 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |
| 11.4 — После завершения Action/Goal/Event API в Фазе 8 и compatibility hardening в Фазе 10 заморозить стабильный versioned Optional Addon API. | P9.3 | В старом плане freeze находился одновременно в фазах 10 и 11; одна точка freeze P9.3 после рабочего Action API и hardening. |
| 11.5 — Отдельный Create adapter после проверки доступной версии и loader compatibility. | P10.2 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |
| 11.6 — Расширенная голосовая коммуникация и дополнительные виды интеракций. | P10.2 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |
| 11.7 — Перед финальным релизом полной версии заменить временные модели из Asset Replacement Register на собственные или разрешённые аналоги либо удалить их. | P10.3 | Мир/addons после standalone; API freeze перенесён к завершению его hardening, до первого addon. |

## Контракты назначений: входы, последующие задачи, независимая проверка

Таблица разворачивает каждое назначение из матрицы: необходимые основы и конкретная проверка. Колонка потребителей содержит прямые task-level зависимости; межфазные интеграции и обязательность полного release дополнительно заданы графом/exit ROADMAP. Отсутствие прямого consumer не означает удаления функции.

| Новая задача | Предусловия | Прямые последующие задачи | Проверка собственного среза |
|---|---|---|---|
| P0.1 | — | P0.2 | А: сборка JAR; client classes не загружаются dedicated server |
| P0.2 | P0.1 | P0.3 | А: integrated/dedicated startup, нестандартный gameDir, повторное создание, конфликт с файлом |
| P0.3 | P0.2 | Интеграция своей фазы / P9, затем P10 | А: два dev UUID, ошибки version/sequence, burst и replay, marker после restart |
| P1.1 | P0 | P1.2 | Б: сравнить SQLite/JSON на одном NPC + двух relationship records; atomic update/rollback, restart, migration и два мира; зафиксировать выбор ADR |
| P1.2 | P1.1 | P1.3, P2.2 | А: schema version, scoped transaction, backup/restore одной версии, ошибка записи без потери предыдущего состояния; save/reload config |
| P1.3 | P1.2 | P1.4, P6.1 | А: встроенное dev definition; create/load/unload/delete с tombstone/idempotency; сохранение позиции/runtime и отдельных отношений |
| P1.4 | P1.3 | P2.1, P7.1 | А: spawn/select/delete через минимальный UI/команду, owner/admin checks, protocol version/size limits, безопасные логи без секретов, два игрока и два мира |
| P2.1 | P1.4 | P2.3, P6.2 | А: endpoint/host secrets, capabilities, bounded async IO, timeout/cancel, ограниченный retry; один реальный endpoint сначала |
| P2.2 | P1.2 | P2.3, P5.1 | А: accepted inbound и final reply, sequence/scope, recent N default 10, roles/system policy, runtime/relationship snapshot, бюджет контекста |
| P2.3 | P2.1, P2.2 | P2.4, P3.5, P8.1 | А: UI → validated request → context → provider → committed reply → private UI; History+event atomic, restart и request dedupe |
| P2.4 | P2.3 | Интеграция своей фазы / P9, затем P10 | А: проверить оба режима конфигурации, streaming если заявлен capability, disconnect/cancel/stale reply, secret redaction; mock для отказов и реальный happy path |
| P3.1 | P1 | P3.2 | Б: сопоставить GemRender/BlendLib/custom по текущим исходникам и лицензиям; один малый rig+morph fixture, headless pose read/evaluate, client palette/morph override и exact Fabric matrix; остановиться после ответа по API, не писать gait/physics |
| P3.2 | P3.1 | P3.3, P4.1 | А: renderer adapter, import/rig mapping/materials/textures/scale; одна rigged anime-модель и static capability fallback, минимальная Blender адаптация при необходимости; повторная загрузка и отсутствие GPU dependencies на сервере |
| P3.3 | P3.2 | P3.4, P8.2 | А: clip crossfade/masks/additive, FK/IK и joint limits, foot/look/reach/turn, глаза/мимика, whole-body arbitration; сначала простой Two-Bone, FABRIK/CCD лишь для нерешённых цепей |
| P3.4 | P3.3 | P3.5 | А: scripted root velocity, start/stop/turn/run, pelvis/weight/arms; host spring chains, ограниченные collision proxies и clipping scenes |
| P3.5 | P3.4, P2.3 | P7.2 | А: pose sequence/rig hash/reconnect, два клиента; listening/thinking/speaking, взгляд на собеседника, валидированные жесты/expressions, neutral fallback; замеры CPU/GPU/bytes на одной и нескольких NPC |
| P4.1 | P3.2 | P4.2 | А: schema/version/hash, path traversal/limits/signatures, provenance/rights, dependencies/cycles/override priority |
| P4.2 | P4.1 | P4.3, P5.1, P8.1 | А: stage/enable/disable/reload/rollback, capability report, внешний definition с тем же NPC identity; fallback недостающих channels |
| P4.3 | P4.2 | Интеграция своей фазы / P9, затем P10 | А: local user dirs, выбор/настройки, version mismatch/reconnect; policy отказа/получения разрешённых ассетов с лимитами; запрещённые ассеты не передаются |
| P5.1 | P2.2, P4.2 | P5.2 | А: CRUD, Always/exact/keyword, роли, scope, priorities и pack/local overrides |
| P5.2 | P5.1 | P5.3 | А: реальные диалоги с/без matching knowledge, source IDs, budget/truncation, NOT_YET_ENABLED для semantic search |
| P5.3 | P5.2 | P6.1 | А: History просмотра/настройки N, backup/restore/migration/retention текущих stores, два игрока; не стирать необработанный outbox source |
| P6.1 | P1.3, P5.3 | P6.2, P7.1 | А: baseline при включении, типизированные события мира, dedupe/coalesce; сообщения не копируются в Action Memory |
| P6.2 | P6.1, P2.1 | P6.3, P7.3 | А: bounded jobs и per-consumer replay/cursor с atomic enqueue ack, независимый preset, context profile, periodic/turn/event triggers, stale/expiry/cancel; Thoughts и pending proposal атомарны; подключить affect mapper к P3 |
| P6.3 | P6.2 | P6.4 | А: History+Thoughts only → candidate → Judge → provenance/versioned Diary; Thinker читает реально созданную запись; per-consumer replay/lease и atomic enqueue ack |
| P6.4 | P6.3 | P6.5 | Б: выбрать day boundary/timezone до writer; А: каждые 10 committed records, input range/watermark, предыдущая Summary + events, restart без повторного count |
| P6.5 | P6.4 | P7.4 | А: semantic retrieval/duplicate candidates, versioned index/rebuild, keyword fallback, ручной dry-run/status/cancel/retry с правами; отказ одного provider и restart полного цикла |
| P7.1 | P1.4, P6.1 | P7.2 | А: typed plan/action registry, whitelist/schema/rights/range/cooldown, server-thread executor, idempotency, cancellation; ручная цель служит настоящим входом, deterministic plan — тестом validator |
| P7.2 | P7.1, P3.5 | P7.3 | А: walk_to/follow, moving target, repath/stuck/unloaded chunks, stop/arrival, реальная velocity→gait; observable outcomes |
| P7.3 | P7.2, P6.2 | P7.4 | А: atomic claim, expired/rejected goal, priority/preemption/dependencies, Bot preset и async typed planning; цель Thinker реально исполняется |
| P7.4 | P7.3, P6.5 | P9.1 | А: полный Observer/visibility/relevance, outcomes→Action Memory→Thinker, uncertain-effect reconciliation, anti-loop, два клиента и restart |
| P8.1 | P2.3, P4.2 | P8.2 | А: bounded async synthesis, cache keys, capability visemes/timing, timeout и текстовый fallback |
| P8.2 | P8.1, P3.3 | P9.1 | А: real audio clock→mouth, отдельные emotion channels, очередь/interrupt, join/seek/stale audio, два клиента и privacy policy |
| P9.1 | P7.4, P8.2 | P9.2 | А/В: integrated/dedicated, online identity в разрешённом окружении, malformed packets, ACL failures, upgrade/rollback/restore, моды exact matrix; воспроизведение native crash при повторении |
| P9.2 | P9.1 | P9.3 | В: несколько NPC/игроков, soak/restart, queue/cost/memory/bandwidth budgets; сцены anime quality, face/body/physics и autonomy; устранить критические дефекты |
| P9.3 | P9.2 | P10.1, P10.2 | А: performance/API-cost controls, полный пользовательский сценарий установки/пакетов/персонажей/providers, stable versioned optional Addon API на проверенных Action/Goal/Event contracts |
| P10.1 | P9.3 | P10.3 | А: inventory/mapping, sandbox conversion, checkpoints/rollback, реальные NPC/пакеты, сценарии локаций |
| P10.2 | P9.3 | P10.3 | Б: проверить доступную Fabric/Create matrix до adapter; А: addon failure isolation/versioning, дополнительные voice/world interactions |
| P10.3 | P10.1, P10.2 | Интеграция своей фазы / P9, затем P10 | В: полный сценарий мира, замена/удаление всех temporary assets, аудит дистрибутива и сохранений |

## Дополнительные требования вне списков задач

| Старое требование | Новое место / оценка |
|---|---|
| Storage spike из прежнего Blueprint 0.6 | P1.1: непосредственно перед первой NPC transaction; не P0 и не будущий vector backend |
| Семидневный прототип как общий design gate ADR-0007 | Удалён календарный gate; P3.1 ограничен API-риском, оставшиеся функции P3.2–P3.5 |
| Build, одиночный мир, два клиента, API errors на каждой фазе | А по изменённому поведению; Б по риску; В после всех участников. Полная матрица P9.1, не повторять всё для каждого изменения |
| Временные модели старых фаз 0–4 и release gates 10/11 | Реестр/права остаются; P3/P4 локальный dev, P9 любой public artifact без restricted материалов, P10 полная очистка локального набора |
| Design artifacts всех компонентов перед переходом фазы | Только контракт следующего среза; Open закрывается перед первым потребителем, не всё заранее |
| Совместная приёмка всех cognitive workers / Bot stack | Итоговая интеграция сохранена P6/P7; завершённые промежуточные срезы принимаются независимо |
| Отчёт, журнал, проверка diff/секретов/границ | Сохранено как рабочая дисциплина skill/INDEX, не отдельная продуктовая фаза |

Покрытие: **144 исходных bullet-задач/пунктов демонстратора**, 12 прежних фаз. Исходные exit criteria переоценены в отчёте аудита; новые указаны только в ROADMAP.
