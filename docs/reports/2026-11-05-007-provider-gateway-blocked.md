# Отчёт 007: Main Dialogue Gateway — блокер реального Ollama inference

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10).
- **Статус:** Partial / Blocked — P2.1 не принята; P2.2–P2.4 не начаты.
- **Ветка:** main; без новой ветки/PR/публикации.
- **HEAD до работы:** `338b491517a23720b7ecacfe32fc8c7d13ef8ad6`.
- **HEAD после сохранения исходной P1:** `fcb46e5`.
- **HEAD изменений P2.1:** коммит этого отчёта (см. локальный `git log`).
- **Связанные ADR:** [0013](../decisions/ADR-0013-main-dialogue-gateway.md), 0002, 0004, 0009, 0010–0012.
- **Этап:** ROADMAP P2.1.

## 1. Цель и исходное требование

Последовательно реализовать P2, начиная с bounded Provider Gateway и Main Dialogue preset. Обязательный gate первой задачи — реальный успешный endpoint. Без него переход к P2.2 запрещён заданием пользователя.

## 2. Состояние до изменений

Прочитаны workspace указатель, skill agnor-development, INDEX, PROJECT_MAP, ROADMAP, Blueprint (P2/provider/context/history/outbox/privacy), архитектура, реестр/относящиеся ADR и отчёт 006. Код NpcRuntime, management/protocol/client, WorldConfig, WorldStorage/NpcRepository сверены с документами. Provider/History/ContextBuilder отсутствовали. Проверены сохранённые P1 A1/A2/B1/B2/I1/I2 coordination markers и реализация точек подключения; дорогая игровая матрица P1 повторно не запускалась. Это проверка существующего evidence, не новый runtime PASS.

Исходное дерево содержало 47 modified/untracked файлов P1. Они сохранены отдельным локальным baseline commit `fcb46e5`, без удаления/reset/перезаписи. Обычный первый commit отказал из-за отсутствующей Git author identity; использована per-command identity `Codex <codex@local.invalid>`, настройки пользователя/Git global не менялись.

## 3. Выполненные действия

Реализован server-owned Gateway, Ollama adapter, Main Dialogue preset v1 и env-only Secret Resolver. Добавлены bounded workers/queue, общий deadline с отменой HTTP, byte limit, strict terminal text validation, безопасная классификация ошибок, нулевые automatic retries и независимый circuit breaker Main Dialogue. Agnor открывает только enabled gateway, отменяет его перед shutdown storage/runtime; invalid provider config не ломает NPC lifecycle. Подробный контракт и ограничения — ADR-0013.

Исследован launcher `C:/Users/User/Desktop/Nova-Ternary-Bonsai-Vision.bat` без изменения/запуска. Model ID `ternary-bonsai-vision-20k:latest`, base `ternary-bonsai-vision:2b`, endpoint `http://127.0.0.1:11434`, context 20000. Launcher убивает старые Ollama процессы и пересоздаёт model tag; сервер уже работал, поэтому повторный запуск не нужен.

Один реальный inference выполнен production Gateway через opt-in `providerLive`. После FAIL проведён один прямой диагностический запрос с теми же transport parameters, чтобы получить причину, которую production Gateway правильно скрывает. Это не попытка получить случайный PASS повторением. Ни один запрос не получил текст модели. Установленная версия 0.40.14 и `/api/show` доступны; capabilities: completion/thinking/tools/vision. `thinking.values` отсутствует, но template явно реализует `enable_thinking is false`; top-level `think:false` выбран по документации и локальному template. Фактическое отключение reasoning при генерации **не доказано**, поскольку runner не запускается.

## 4. Изменённые файлы

| Путь | Изменение | Причина |
|---|---|---|
| `src/main/java/dev/agnor/provider/{DialoguePreset,ProviderRequest,ProviderResult,ProviderGateway}.java` | Preset, neutral contracts, Ollama HTTP boundary | P2.1 |
| `src/main/java/dev/agnor/Agnor.java` | Session lifecycle Gateway | Host authority / cancellation |
| `src/test/java/dev/agnor/provider/{ProviderGatewayTest,ProviderLiveCheck}.java` | Controlled contract failures и opt-in real inference | Не подменять live mock-результатом |
| `build.gradle` | Test Gson 2.10.1 и отдельный providerLive task | Gson уже есть в Minecraft runtime; нет новой production dependency |
| `docs/decisions/ADR-0013-main-dialogue-gateway.md`, docs index/map/log, README | Контракт, настройка и точный partial status | Непрерывность работы |

## 5. Решения

ADR-0013 фиксирует schema/config/security/transport contract. Roadmap и Blueprint не изменялись. History/event log/ContextBuilder/диалоговый UI и будущие назначения не создавались. Cloud keys не искались и не читались: cloud acceptance относится к P2.4, до которой работа не дошла.

## 6. Проверки и результаты

JDK 21 из `%APPDATA%/ElyPrismLauncher/java/java-runtime-delta`, GRADLE_USER_HOME=`<repo>/.gradle`.

| Проверка / команда | Результат | Подтверждение |
|---|---|---|
| `gradlew.bat test providerLive --console=plain` | Controlled tests PASS; **providerLive FAIL** | Gateway реально отправил chat, получил UNAVAILABLE, 224ms |
| `gradlew.bat build --console=plain` | PASS | Common/client compile, normal remapped JAR и tests |
| 8 ProviderGatewayTest | PASS | HTTP/think:false/stream:false, env header, error payload suppression, 401/403/404/429/500, отсутствие retry, malformed/oversize/partial/thinking, bounded queue/cancel/deadline/close, missing capability, invalid config, network refusal, circuit breaker |
| Existing 22 tests | PASS | P1 storage/registry/codec/policy regression |
| Default test task live check | 1 SKIPPED | Реальный inference только отдельным opt-in task; skip не считается PASS |
| `/api/version`, `/api/tags`, `/api/show` | PASS metadata only | Сервер работает, tag существует; не доказательство inference |
| Прямой диагностический `/api/chat` | HTTP 500 | Та же ошибка отсутствующего runner |

Итого обычная сборка: 30 successful tests, 1 opt-in skipped. Отдельный реальный live test: 1 failure, 0 success. Игровой server/client сценарий Gateway, неблокировка реального игрового тика, History/restart/privacy/dialogue, cloud **НЕ ПРОВЕРЕНЫ**; не являются выполненной P2.

## 7. Ошибки и диагностика

Первоначальная compile error в скобках endpoint validation исправлена до успешной сборки. При review улучшен порядок terminal cleanup/callback: callback вне монитора Call и после cancel/cleanup. Добавлены explicit boolean/string response validation и запрет остановки NPC из-за provider config.

**Внешний блокер:** Ollama возвращает `error starting llama-server: llama-server binary not found`. Проверены пути из самого error: `<Ollama>/llama-server.exe`, `lib/ollama/llama-server.exe`, соседний `Programs/lib/ollama`, `build/lib/ollama`, `dist/windows-amd64/lib/ollama`, `dist/windows_amd64/lib/ollama`. В установленном `<Ollama>/lib` находится только `Ollama.lnk` (target — ollama app.exe). `rg --files <Ollama>` показывает launcher/app/uninstaller/shortcut, без inference runner и его библиотек. В `%LOCALAPPDATA%/Ollama/server.log` ошибка зафиксирована уже при GPU discovery на startup и затем для двух реальных chat calls. Это проблема установленного Ollama runtime, не timeout/reasoning loop или ошибка Minecraft.

## 8. Риски и ограничения

Установка/переустановка Ollama и поиск совместимого дистрибутива с runner не выполнены в рамках изменения мода. Не менялись launcher, model files, установка Ollama, пользовательские миры и credentials. Нет оснований заявлять полноценную поддержку этой модели до успешного inference. После восстановления runner необходимо повторить `providerLive`, исследовать результат и лишь затем принимать P2.1. Gateway пока не имеет пользовательского consumer; его игровые lifecycle hooks с реальным server runtime отдельно не проверены.

## 9. Итог

- Реализован и проверен controlled HTTP contract Gateway/preset; работа сохранена локально.
- P2.1 **Partial/Blocked**, обязательный реальный endpoint не работает.
- Завершённых задач P2: **0**. P2.2–P2.4/P3 не начинались.
- Следующий шаг: восстановить совместимый `llama-server.exe` и runtime dependencies в Ollama, проверить реальный inference production Gateway. До этого переход к P2.2 запрещён.

## 10. Ссылки и evidence

Ignored local evidence: `build/p2-evidence/ollama-gateway.txt`, `build/test-results/providerLive/TEST-dev.agnor.provider.ProviderLiveCheck.xml` (FAIL), `build/test-results/test/TEST-*.xml`, `build/reports/tests/`, `build/libs/agnor-0.1.0-dev.jar`. Диагностические подробности missing runner зафиксированы выше; server.log принадлежит установленному Ollama. Опубликованных артефактов/PR нет.
