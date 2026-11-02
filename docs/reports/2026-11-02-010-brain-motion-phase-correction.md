# Отчёт 010: Исправление порядка интеграции мозга, движения и мимики

- **Дата:** 2026-11-02
- **Статус:** Done (documentation correction; runtime behavior not implemented/verified)
- **Ветка:** main
- **HEAD до работы:** 664daa94bdc57b2e19ddb7b25fda1396b5412466
- **HEAD после содержательных изменений:** 491e06f75110dfa62a7276539f9e9c029d8a934b (затем обновлены отчёт и журнал)
- **Связанные ADR:** ADR-0002, ADR-0004, ADR-0006, ADR-0007
- **Связанные этапы:** Фазы 0, 3, 5, 7, 8, 9

## 1. Причина исправления

В предыдущей редакции порядок фаз смешивал техническую готовность исполнительного слоя (renderer, rig, IK, Face Controller, Motion Engine) с наличием источников поведения. Из-за этого критерии Фазы 3 могли читаться так, будто NPC уже получает осмысленную мимику и lip-sync до появления диалога, Thinker и TTS.

Это неверно. Motion Engine рассчитывает, как выполнить намерение; он не создаёт сам намерение, эмоцию или цель. Нужен поэтапный handoff от cognitive/behavioral systems к presentation/action systems.

## 2. Состояние до изменений

- Ветка main; HEAD 664daa94bdc57b2e19ddb7b25fda1396b5412466.
- Единственная найденная ветка — main; открытых PR нет.
- ROADMAP ставил Phase 3 до Provider Gateway/dialogue (Phase 5) и gnomes (Phase 7).
- В критерии Phase 3 присутствовали мимика и lip-sync; техническое тестирование Face Controller было недостаточно отделено от реального эмоционального поведения.
- Blueprint описывал Motion Engine как исполнитель, но недостаточно явно разделял test harness, dialogue reactions, Thinker affect, autonomous motion и TTS visemes.

## 3. Принятое уточнение последовательности

### Фаза 0
Только техническая проверка renderer/pose/motion/IK/gait/physics через scripted/synthetic test driver. Это не AI и не поведение живого NPC.

### Фаза 3
Создаётся исполнительный слой:
- renderer, rig mapping, animation graph/layers;
- Motion Task API, Task Planner, Whole Body Controller, IK, gait primitives;
- Face Controller и технические facial/expression channels;
- secondary physics, pose replication и performance tests.

Все тестовые движения и выражения подаются тестовым harness; procedural gait получает только synthetic velocity/root-motion input. На этой фазе нет dialogue-driven reactions, Thinker-driven emotional state, world pathfinding/follow, autonomous movement или работающего TTS lip-sync.

### Фаза 5
Dialogue Presentation Adapter подключает первые реакции на собеседника:
- повернуться/посмотреть на отправителя;
- listening/thinking/speaking states;
- ограниченные валидированные conversational expressions и простые жесты.
Сырые LLM-команды на кости/morph weights не принимаются. При отсутствии валидированной metadata используется нейтральное состояние.

### Фаза 7
Thinker предоставляет структурированные внутренние состояния, emotion/intensity/attention/desire и goal proposals. Affective Presentation Mapper превращает валидированные данные в устойчивые face/body presentation states. Cognitive goals отделены от presentation state: эмоция не равна команде действия.

Bot получает typed plan/action-intent contract, но world-side execution не начинается до готовности Фазы 8.

### Фаза 8
Впервые соединяются Brain/Thinker/Bot, World Observer, Goal Scheduler, Action Validator/Executor, Navigation и Motion Engine. Здесь появляется полноценное автономное поведение: NPC может идти, следовать, останавливаться, взаимодействовать, реагировать на события, сочетать задачи и корректно их прерывать.

### Фаза 9
TTS/audio playback предоставляет реальный viseme timeline; Face Controller выполняет lip-sync, не затирая независимые эмоциональные expression channels.

## 4. Изменённые файлы

| Путь | Изменение | Причина |
|---|---|---|
| docs/IMPLEMENTATION_BLUEPRINT.md | Добавлен принцип «исполнитель движения не является мозгом», фазовая таблица интеграции, четыре раздельных flow (cognitive/presentation/action/speech); исправлены phase crosswalk и design gates | Устранить архитектурное смешение возможностей renderer и поведения AI |
| docs/ROADMAP.md | Фаза 0 явно использует test driver; Фаза 3 ограничена техническим исполнительным слоем; добавлены реальные реакции в Фазе 5, affect mapping в Фазе 7, brain-to-motion integration в Фазе 8 и viseme lip-sync в Фазе 9 | Сохранить честную последовательность функциональных зависимостей |
| docs/ARCHITECTURE.md | Добавлено пояснение, что motion implementation и live behavior — разные критерии, с пофазным порядком подключения источников | Согласовать общий архитектурный обзор с Blueprint |

## 5. Проверки

| Проверка | Результат | Что подтверждено |
|---|---|---|
| GitHub branch search | PASS | Ветка main |
| Open PR search | PASS | Открытых PR нет |
| Blueprint phase review | PASS | Phase 3 больше не заявляет осмысленную мимику/lip-sync или автономность |
| Roadmap phase review | PASS | Реакции, Thinker affect, autonomous motion и lip-sync назначены фазам 5/7/8/9 |
| Cross-document consistency | PASS | ARCHITECTURE, ROADMAP и Blueprint описывают одинаковый порядок |
| Bot Phase 7/8 boundary | PASS | Plan contract отделён от world-side execution |
| Runtime behavior / animation tests | НЕ ПРОВЕРЕНО | В рамках задачи менялась только документация; код и runtime не проверялись |

## 6. Риски и оставшиеся условия

- Фаза 3 даст проверяемый renderer/Motion Engine, но персонаж до подключения диалога/гномиков/событий будет только технически управляем тестовым драйвером.
- Фаза 5 создаёт ограниченные реакции на диалог, но не должна называться полноценной автономной жизнью.
- Фаза 7 создаёт внутреннее эмоциональное состояние и его визуальное отображение, но не подменяет проверку действий и навигации.
- Полноценное автономное движение подтверждается только в Фазе 8 end-to-end тестом.
- Lip-sync подтверждается только в Фазе 9 реальным audio/viseme timing.
- Если любой источник эмоции/намерения не имеет валидной metadata, должен работать явный fallback, а не выдуманное поведение.

## 7. Итог

Исправлено главное архитектурное смешение: **исполнитель движения не равен мозгу персонажа**. Ранние фазы создают и проверяют механизм; следующие фазы подключают к нему реальные источники намерений в правильной последовательности. Критерии не скрывают отсутствие мозга за тестовыми анимациями.
