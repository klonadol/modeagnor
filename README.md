# AGNOR

**P4 Verified, 2026-11-06:** P4.1→P4.2→P4.3 завершены последовательно. Внешние Content Packs v1, atomic Registry/rollback, выбор существующей NPC и consented bounded sync работают без JAR rebuild. [Итоговая приёмка](docs/reports/2026-11-06-008-phase4-acceptance.md), [формат и команды](docs/CONTENT_PACKS.md), [стенд](src/p4Check/README.md). Dedicated2clients/restart/integrated и реальная Ollama PASS;102tests PASS/3opt-in skipped. Restricted Firefly передано0bytes. P5 не начата.

**Агнор** — Minecraft-мод с автономными ИИ-персонажами: выразительными 3D-аниме-моделями, диалогами, голосом, долговременной памятью и самостоятельным поведением.

На первом этапе приоритет — законченный companion-мод. Перенос мира Агнор и интеграции с крупными контентными модами являются последующими расширениями и не должны быть обязательными для работы ядра. Основной принцип разработки: **не создавать с нуля то, что можно надёжно переиспользовать**. Для персонажей сначала ищем готовые внешние модели и адаптируем их; Blender используем прежде всего для импорта, проверки, ремонта ригов и подготовки ассетов, а не для обязательного ручного моделирования каждой девушки.

## Работа с временными моделями

Если подходящей модели с понятными правами нет, можно временно использовать локальную копию, в том числе в собственной dev-сборке, JAR или Content Pack. Не коммитим её в публичный репозиторий, не включаем в публичный релиз и не передаём другим игрокам или через синхронизацию. Записываем модель, страницу источника, прямую ссылку на скачивание (если есть), локальное расположение и план замены в [реестре](docs/assets/ASSET_REPLACEMENT_REGISTER.md). Заменять такие модели нужно только перед финальным релизом полной версии после переноса авторского мира в Minecraft (P10); первый самостоятельный релиз companion-мода (P9) это требование не блокирует.

## Статус проекта

P0–P4 завершены в границах ROADMAP. Модели/производные не входят в Git/JAR. Основной путь — [Content Packs](docs/CONTENT_PACKS.md) в `mods/agnor/packs/<id>` или `<id>.zip`: admin `/agnor packs enable <id>`, owner `/agnor character namespace:id`, client `/agnorpack`. Host считает motion по CPU metadata внешнего binding, client требует exact inventory digest. Прежний `-Dagnor.motionAsset=<absolute capabilities.json>` остаётся dev adapter для встроенного definition; [старый P3.5 стенд](src/p35Check/README.md) сохранён.

Для сборки сначала `powershell -ExecutionPolicy Bypass -File scripts/prepare-renderer.ps1`, затем Wrapper/`scripts/dev.ps1 build`. Gradle9.7.1/Loom1.16.3/API0.116.15, Java21. Optional renderer: GemRender0.1.20 ([upstream, LGPL-3.0-only](https://github.com/Warfactory-Official/GemRender/releases/tag/v0.1.20)) + Flywheel1.0.6; AGNOR их не включает. Локальный dev resource pack готовится `scripts/prepare-p32-check.ps1`, [инструкции стенда](src/p32Check/README.md). Без renderer/ресурсов остаётся armor-stand fallback. Формат/выбор/reload/права описаны в [Content Packs](docs/CONTENT_PACKS.md).

История выбора renderer и предыдущих фаз сохранена в отчётах012–014 и [итоге P2](docs/reports/2026-11-05-011-phase2-complete.md). NPC сохраняется и ведёт приватный диалог через локальную Ollama или OpenRouter; History/Context Builder/events, два игрока, restart, retry и отказы проверены.

## Настройка и диалог

Main Dialogue по умолчанию выключен. На **хосте** создать `<server-save>/agnor/main-dialogue.properties`, затем перезапустить сервер:

```properties
version=1
enabled=true
adapter=OLLAMA
endpoint=http://127.0.0.1:11434
model=ternary-bonsai-text-20k:latest
timeoutMillis=90000
workers=1
queue=8
maxOutputTokens=256
maxResponseBytes=65536
historyRecentN=10
contextBudgetBytes=12000
```

Модель сначала импортировать/запустить через desktop `Nova-Ternary-Bonsai-Text.bat`; используется один `Ternary-Bonsai-2-9B-Q2_0.gguf`, без vision projector. Gateway отправляет `think:false` после проверки capabilities. Не запускайте второй Ollama server при работающем endpoint.

Облачный режим использует тот же файл: заменить `adapter=OPENROUTER`, `endpoint=https://openrouter.ai`, `model=google/gemini-2.5-flash-lite`. Credential задаётся **ссылкой**, без значения ключа: либо `secretEnv=AGNOR_OPENROUTER_KEY`, либо `secretFile=C:/absolute/private/keys.txt` и `secretSection=OpenRouter`. Для file resolver исходный внешний UTF-8 файл содержит строку `OpenRouter:`, а следующая непустая строка — его ключ. Файл не копируется в проект/пакеты; одновременно env и file не указывать. Не храните реальные ключи в world preset. Проверен именно OpenRouter Chat API, совместимость всех других API не предполагается. Оба adapter non-streaming.

После `/agnor spawn` или `/agnor select <UUID>`:

- `/agnor say Привет!` — отправить сообщение (до 1024 символов); появятся request ID и приватный ответ.
- `/agnor history` — показать последние N committed messages текущей NPC и вашего UUID, default 10.
- `/agnor retry <request UUID>` — получить прежний результат без нового inference/сообщения. Terminal failure/restart не перезапускает uncertain LLM call; новый запрос отправляется отдельной командой say.
- `/agnor cancel` — отменить текущий запрос, сохранив принятый inbound.

Нужно быть в той же dimension, не дальше 64 блоков. Нельзя одновременно отправлять несколько разных запросов с одного connection. History сохраняется в world DB; recent N не удаляет старые protected records. Приватность относится к server scope/delivery; собственный видимый текст попадает в штатный локальный Minecraft chat log, который нельзя автоматически публиковать как безопасный diagnostics bundle.

Для разработчика: `gradlew.bat build` с JDK 21. Отдельные реальные проверки `providerLive`, `historyLive`, `cloudLive` запускаются явно; cloudLive использует named section разрешённого host file на Desktop/silly. Ни один live task не запускается автоматически обычной сборкой. Gameplay fixtures: `-Pp2Check` и `scripts/run-p2-check.ps1`; подробности/ограничения — отчёт 011 и ADR-0013–0016.

## Минимальное управление NPC

В запущенном dev-мире: `/agnor spawn` создаёт и выбирает NPC; `/agnor select <UUID>` выбирает существующую NPC и показывает её состояние. `/agnor select` показывает уже выбранную. `/agnor relationship <UUID> <число от -100 до 100>` меняет только ваше отношение; это простое тестовое состояние, пока без AI. Для select/relationship нужно находиться в той же dimension, в пределах 64 блоков.

`/agnor delete [UUID]` удаляет выбранную или указанную NPC; доступно владельцу и администратору level 2+. Данные хранятся в `<server-save>/agnor/state.db`, настройки — рядом в `config.properties` (читаются при запуске; при отсутствии файла действуют defaults `enabled=true`, `autosaveSeconds=30`). Начальные лимиты — 8 активных NPC на владельца и 64 на мир. UUID сохраняется после restart, выбор сбрасывается при переподключении. Placeholder явно временный; полноценный 3D renderer ещё впереди.

## С чего начать

- [Индекс документации](docs/INDEX.md) — где искать информацию и куда вносить изменения.
- [Архитектура](docs/ARCHITECTURE.md) — подсистемы, границы ответственности, данные, сетевой и runtime-потоки.
- [План разработки](docs/ROADMAP.md) — этапы, зависимости и критерии завершения.
- [Реестр решений](docs/DECISIONS.md) — согласованные архитектурные решения и ссылки на ADR.
- [Журнал разработки](docs/DEVELOPMENT_LOG.md) — хронология действий, проверок и результатов.
- [Шаблон отчёта](docs/templates/DEVELOPMENT_REPORT_TEMPLATE.md) — отдельный отчёт на каждую завершённую работу.
- [Шаблон решения](docs/templates/DECISION_TEMPLATE.md) — отдельная запись на каждое архитектурное решение.

## Базовые ориентиры

| Область | Зафиксированный ориентир |
|---|---|
| Minecraft | 1.21.1 |
| Загрузчик | Fabric |
| Java | 21 |
| Модель исполнения | Сервер хоста authoritative |
| Внешние ИИ-сервисы | Локальные или облачные API, через адаптеры |
| 3D | Полноценные rigged polygon-mesh модели; renderer выбирается после проверки совместимых библиотек и прототипа |
| Данные | SQLite на save, typed NPC/relationships и world Properties реализованы; переносимые определения/content packs ещё впереди |
| Контент | Внешние расширяемые пакеты рядом с JAR, не требующие пересборки мода |
| Мультиплеер | Закладывается с первого этапа |
| Create | Только будущая необязательная интеграция; совместимость сначала проверяется отдельно |

## Принцип документации

Код, фактическое поведение и тесты имеют приоритет над предположениями в документации. После каждого существенного изменения реализации нужно создать отдельный отчёт в `docs/reports/`, обновить [журнал разработки](docs/DEVELOPMENT_LOG.md), а при изменении архитектурного решения — соответствующий ADR и [реестр решений](docs/DECISIONS.md).

Не записывайте API-ключи, пароли, персональные данные игроков или другие секреты в Git.

Текущая структура: [карта проекта](docs/PROJECT_MAP.md). Обоснование нового порядка: [аудит](docs/reports/2026-11-05-001-architecture-replan.md).
