# Отчёт 003: Повторная проверка Фазы 0

- **Дата:** 2026-11-04 (Asia/Vladivostok, UTC+10)
- **Статус:** Partial — Фаза 0 остаётся In progress
- **Ветка / HEAD:** `main`, `5d06367178325fcb6b41c1075af1ae0c5cc91613`
- **Связанные ADR:** ADR-0001, ADR-0002
- **Связанный этап:** Фаза 0

## Цель и исходное состояние

По запросу пользователя продолжить разработку строго с Фазы 0. Найден Git-клон в `modeagnor`; Gradle Wrapper и проектный кэш отсутствовали ранее в доступном Gradle home, Java 21 уже установлена ElyPrismLauncher.

## Действия и изменения

- Настроен проектный Gradle cache в `scripts/dev.ps1` (`.gradle`), чтобы Wrapper не зависел от записи в пользовательский профиль.
- Gradle Wrapper 8.8 загружен, Fabric/Minecraft зависимости разрешились.
- Уточнён статус ROADMAP: Фаза 0 снова `In progress`, Фаза 1 отложена до её критериев выхода.
- Протокол отражает реально наблюдавшиеся runtime результаты.

## Изменённые файлы

`scripts/dev.ps1`, `docs/ROADMAP.md`, `docs/PHASE_0_SPIKES.md`, `docs/DEVELOPMENT_LOG.md`, этот отчёт.

## Проверки

| Команда / сценарий | Результат |
|---|---|
| `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 build` | PASS, BUILD SUCCESSFUL; main/client compile, remapJar и тесты прошли |
| `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 test` | PASS, BUILD SUCCESSFUL; test task UP-TO-DATE после успешного build |
| `jar tf build/libs/agnor-0.1.0-dev.jar` через JDK `jar.exe` | PASS; JAR содержит fabric.mod.json, Agnor, AgnorClient и ProbeProtocol |
| `runServer` до принятия EULA | Fabric загрузил 43 мода и инициализировал AGNOR без client classloading error; сервер корректно остановился на EULA gate |
| `runServer` после принятия EULA в локальном dev run | PASS; сервер создал мир, достиг `Done`, `SERVER_STARTING` создал каталог `run/mods/agnor`; остановлен после проверки |
| `runClient` | Не подтверждён: Gradle достиг `downloadAssets`, видимого меню и lifecycle логов не было; запуск остановлен после нескольких минут ожидания |
| `git diff --check` | PASS |

## Что осталось НЕ ПРОВЕРЕНО

Probe response и rate-limit пользовательским сценарием; интегрированный мир; dedicated server после принятия EULA; подключение двух клиентов; права каталогов и restart сценарий; renderer и совместимость Sodium/Iris; rigged/static assets и их лицензии; motion/IK/gait/spring chain и host pose sync; SQLite/JSON comparison с transaction/migration/concurrency.

## Следующий шаг

Продолжить воспроизводимые Phase 0 spikes: обеспечить запуск клиента после downloadAssets, затем выполнить многоклиентскую probe-проверку. Параллельно подготовить отдельные изолированные harness-проекты для renderer/motion/storage только после получения совместимых зависимостей и тестового asset с ясными правами. Фаза 1 не начинается до закрытия критериев Phase 0 или явной записи доказательных переносов в план.
