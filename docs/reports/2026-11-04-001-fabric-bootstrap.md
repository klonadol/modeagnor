# Отчёт 001: Начало Фазы 0 — Fabric bootstrap

- **Дата:** 2026-11-04 (Asia/Vladivostok, UTC+10)
- **Статус:** Partial — Фаза 0 остаётся In progress
- **Ветка:** main
- **HEAD до работы:** 5d06367178325fcb6b41c1075af1ae0c5cc91613
- **HEAD после работы:** без нового коммита; изменения локальные
- **Связанные ADR:** ADR-0001, ADR-0002; новые решения о renderer/storage не принимались
- **Связанный этап:** Фаза 0

## Цель и исходное состояние

Начать разработку согласно ROADMAP и IMPLEMENTATION_BLUEPRINT. До работы репозиторий содержал только документацию. Изучены структура, фазовый маршрут, bootstrap/network/threading contracts и ограничения архитектуры. Первая работа — минимальный Java 21/Fabric 1.21.1 проект, не реализация будущих AI-подсистем.

## Изменения

| Пути | Результат |
|---|---|
| `settings.gradle`, `build.gradle`, `gradle.properties` | Закреплены версии, split main/client source sets, JUnit |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Официальный Gradle 8.8 Wrapper из Gradle v8.8.0 |
| `src/main/resources/fabric.mod.json` | Main/client entrypoints, требования платформы |
| `src/main/java/dev/agnor/Agnor.java`, `DataRoots.java` | Lifecycle, создание общего каталога ресурсов и диагностируемый отказ |
| `src/main/java/dev/agnor/network/ProbeProtocol.java` | Read-only versioned C2S/S2C probe, bounded fixed fields, sender из context, rate limit |
| `src/client/java/dev/agnor/client/AgnorClient.java` | Probe после JOIN; лог server snapshot |
| `src/test/java/dev/agnor/DataRootsTest.java` | Повторный startup, custom directory, независимость каталогов, отказ без повреждения файла |
| `scripts/dev.ps1` | Запуск задач Wrapper с уже установленным JDK ElyPrismLauncher либо JAVA_HOME |
| `.gitignore` | Исключены build/cache/run, IDE и local-assets |
| `docs/PHASE_0_SPIKES.md`, `README.md`, `docs/INDEX.md`, `docs/ROADMAP.md`, `docs/DEVELOPMENT_LOG.md` | Протокол проверок, навигация, фактический статус |

## Проверки

`powershell -File scripts/dev.ps1 build` — **PASS**, BUILD SUCCESSFUL, 10 задач, 11m 15s с первым наполнением cache. Main и client компилируются, remapped JAR собран: `build/libs/agnor-0.1.0-dev.jar`. Три JUnit-теста DataRootsTest прошли без ошибок. `git diff --check` — PASS. JAR проверен на наличие `fabric.mod.json`, common/network и client entrypoint classes.

Gradle использовал существующий JDK 21.0.7 ElyPrismLauncher. Основное время первой сборки ушло на загрузку Minecraft, mappings и Fabric dependencies; диагностический stack dump подтвердил ожидание HTTP, затем сборка продолжилась успешно. Дополнительные JDK/глобальный Gradle не устанавливались.

НЕ ПРОВЕРЕНО: пользовательский путь integrated server и dedicated server + два клиента; реальные packet version/rate-limit scenarios; read-only OS permissions; renderer/IK/gait/secondary physics; модели и права на них; pose sync; storage backend; Sodium/Iris. Эти пункты остаются в матрице Фазы 0. EULA автоматически не принимается.

## Ограничения и следующий шаг

Probe синхронизирует только server tick и не является NPC/pose protocol. Общий каталог не является private world storage. AI, NPC, renderer, Content Pack Loader, UI и persistence не реализованы. Следующий шаг — runtime bootstrap/network smoke test, затем сравнительные renderer/asset/motion/storage spikes. Фаза 1 не объявляется начатой.

## Источники

- [Fabric environment setup](https://wiki.fabricmc.net/tutorial:setup)
- [Fabric Loom](https://github.com/FabricMC/fabric-loom)
- [Gradle Wrapper v8.8.0](https://github.com/gradle/gradle/tree/v8.8.0/gradle/wrapper)
- [Протокол технических проверок](../PHASE_0_SPIKES.md)
