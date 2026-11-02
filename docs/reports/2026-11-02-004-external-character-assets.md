# Отчёт 004: Переиспользование готовых внешних моделей персонажей

- **Дата:** 2026-11-02
- **Статус:** Done (требования и документация обновлены; импортёр не реализовывался)
- **Ветка:** `main`
- **HEAD до работы:** `f78986237ce93b39b55279eab1b64a001cf8c94b` (создан ADR-0008)
- **Связанные ADR:** ADR-0006, ADR-0008
- **Связанные этапы:** Фаза 0 — проверка импорта; Фаза 3 — внешний character asset pipeline; Фаза 4 — Content Packs

## 1. Цель и исходное требование

Пользователь уточнил принцип: не создавать каждую модель с нуля в Blender. Следует переиспользовать готовые персонажные модели, найденные в интернете, включая возможные модели/конверсии персонажей ZZZ, Genshin Impact, Honkai: Star Rail и других игр. Blender остаётся важным инструментом, но в первую очередь для импорта, проверки, адаптации, ремонта и подготовки ассетов.

## 2. Состояние до изменений

- Репозиторий: `klonadol/modeagnor`.
- Ветка: `main`.
- Последний commit до ADR-0008: `39c5ca6d9f16637748eeb32aafc65aac30a5b4aa`.
- Исходный код/импортёр пока не реализованы.
- Проверены актуальные README, architecture, roadmap, decision register, index и development log.
- Новая ветка и PR не создавались.

## 3. Выполненные действия

1. Создан ADR-0008 — переиспользование внешних 3D-моделей персонажей.
2. В архитектуру добавлен External Character Asset Pipeline.
3. Уточнена роль Blender: адаптация и ремонт, а не обязательное создание исходной геометрии.
4. Добавлены кандидаты форматов GLB/glTF, FBX, VRM, PMX/PMD и статических mesh.
5. Введён capability report для каждого ассета: rig, bones, skinning, clips, morphs, spring chains, sockets и collision proxies.
6. Зафиксирована политика происхождения и лицензирования.
7. Обновлены Фаза 0, Фаза 3 и Фаза 4 roadmap.
8. Обновлены README, ADR registry, docs index и development log.
9. Проверены публичная документация Nahida Desktop Static GLB Converter, Blender pipeline и условия HoYoverse.

## 4. Результаты исследования источников

### Blender

Официальная страница Blender Pipeline перечисляет поддержку импорта/экспорта glTF 2.0, FBX, USD, Alembic, OBJ и других форматов. Это подтверждает, что Blender подходит как промежуточная мастерская для нормализации различных ассетов, но не означает, что каждый формат сохраняет исходный rig/animation/morph data без потерь.

Источник: https://www.blender.org/features/pipeline/

### Модели Genshin Impact / Honkai: Star Rail / Zenless Zone Zero

Документация Nahida Desktop описывает Static GLB Converter, который поддерживает конвертацию модов Genshin Impact, Honkai: Star Rail, Zenless Zone Zero и Wuthering Waves в static GLB. Там же указано, что некоторые моды, особенно многие animation mods, могут конвертироваться некорректно; для конверсии требуются отдельные asset layout files.

Это практический исследовательский инструмент для просмотра/подготовки некоторых локальных игровых моделей, но:
- он не является runtime dependency Агнора;
- название и описание «static GLB» не дают гарантии сохранения полного skeletal rig, facial morphs или исходных animation clips;
- каждый результат нужно инспектировать и формировать capability report.

Источник: https://desktop.nahida.live/features/mod-tools/static-glb-converter

### Права на игровые ресурсы HoYoverse

Актуальные Terms of Service HoYoverse, найденные при проверке 2026-11-02, описывают ограниченную персональную лицензию и ограничения на копирование, распространение и использование защищённых материалов/производных работ без предварительного письменного разрешения. Поэтому факт наличия извлечённой модели на стороннем сайте не делает её автоматически разрешённой для распространения.

Источник: https://genshin.hoyoverse.com/en-us/company/terms

Это не юридическое заключение. Применимые разрешения, исключения и правила зависят от конкретного ассета, условий правообладателя и законодательства.

## 5. Новая политика импорта

Порядок предпочтения:
1. Готовая rigged-модель, права на использование которой проверены.
2. Прямой runtime import, если все нужные capabilities поддержаны.
3. Минимальная адаптация в Blender или другом внешнем инструменте.
4. Rig mapping / retargeting / repair при необходимости.
5. Создание недостающей части модели вручную, только если подходящего готового решения нет.
6. Полное моделирование с нуля — крайний вариант.

Потенциальные форматы: GLB/glTF, FBX, VRM, PMX/PMD через importer, а также OBJ/static mesh как ограниченный fallback. Их поддержка будет подтверждаться прототипом, а не предполагаться автоматически.

## 6. Политика лицензирования и мультиплеера

Каждый Content Pack должен иметь metadata источника и прав: author, source URL, license name/URL, permission status, права на модификацию, redistribution, commercial use, attribution и дату проверки.

Для моделей с неизвестным происхождением/лицензией или извлечённых игровых ассетов:
- статус по умолчанию `unknown/restricted`;
- они не добавляются в публичный Git, JAR или релиз без подтверждённого разрешения;
- сервер не раздаёт эти модели клиентам автоматически;
- клиенты устанавливают свои локальные копии, если имеют право на использование;
- в сетевом протоколе могут передаваться model ID, capability metadata и состояние NPC, но не сам защищённый ассет.

## 7. Изменённые файлы

| Путь | Изменение |
|---|---|
| `docs/decisions/ADR-0008-reuse-external-character-assets.md` | Новое решение asset-first |
| `README.md` | Добавлен принцип reuse before build |
| `docs/ARCHITECTURE.md` | External Character Asset Pipeline, capability report, права и синхронизация |
| `docs/ROADMAP.md` | Готовые модели приоритетны в Фазах 0/3/4 |
| `docs/DECISIONS.md` | Зарегистрирован ADR-0008 |
| `docs/INDEX.md` | Добавлена ссылка на ADR-0008 |
| `docs/DEVELOPMENT_LOG.md` | Добавлена запись 004 |
| `docs/reports/2026-11-02-004-external-character-assets.md` | Этот отчёт |

## 8. Проверки и результаты

| Проверка | Результат | Что подтверждено |
|---|---|---|
| GitHub repository/branch/PR state | PASS | Работа продолжена в main; PR не создавался |
| Наличие и содержание ADR-0008 | PASS | ADR создан |
| Architecture / roadmap / index / registry / log | PASS | Документы обновлены |
| Blender official format support | PASS (research) | Blender перечисляет glTF, FBX и другие форматы |
| Nahida Desktop support/caveats | PASS (research) | Документированы игровые источники и ограничение static converter |
| HoYoverse terms review | PASS (research) | Зафиксированы ограничения на защищённые игровые материалы |
| Импорт реальной модели в Minecraft | НЕ ПРОВЕРЕНО | Нет runtime-проекта и импортёра |
| Сохранение рига/анимаций в конвертированном GLB | НЕ ПРОВЕРЕНО | Требуется инспекция конкретных файлов |
| Multiplayer asset sync | НЕ ПРОВЕРЕНО | Прототип ещё не создан |

## 9. Итог и следующий шаг

Подход изменён на asset-first: Агнор должен уметь использовать уже существующие модели и не заставлять пользователя или разработчика создавать всех персонажей с нуля. Blender остаётся инструментом адаптации и ремонта. Для любой модели отдельно проверяются rig/capabilities и условия использования.

Следующий технический шаг — протестировать один готовый rigged asset с разрешённым использованием и один статический GLB, сравнить, сколько адаптации требуется, и подтвердить runtime importer на Fabric 1.21.1.

## 10. Связанные документы

- [ADR-0008: Reuse External Character Assets](../decisions/ADR-0008-reuse-external-character-assets.md)
- [ADR-0006: Character Visual Quality and Physics](../decisions/ADR-0006-character-visual-quality-and-physics.md)
- [Архитектура](../ARCHITECTURE.md)
- [План разработки](../ROADMAP.md)
- [Индекс](../INDEX.md)
