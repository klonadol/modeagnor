# Фаза 0 — протокол технических проверок

Статус 2026-11-05: **P0 Verified в пересмотренном scope bootstrap**. Это протокол evidence и воспроизведения, не отдельный gate. Старый расширенный render/motion/storage prototype не выполнен и перенесён по ROADMAP/ADR-0009.

## Bootstrap и границы

Один JAR, Loom splitEnvironmentSourceSets: main содержит common/server и сетевые DTO; client содержит только представление. Main не импортирует клиентские классы. Java 21, Minecraft 1.21.1, Yarn 1.21.1+build.3, Loader 0.16.10, Fabric API 0.102.1+1.21.1, Loom 1.7.4, Gradle 8.8 — закреплённые версии первого spike, не заявление о совместимости будущих renderer-библиотек.

Client entrypoint и `SERVER_STARTING` создают каталог `<gameDir>/mods/agnor` на соответствующей стороне. Это только общий каталог ресурсов; приватные данные миров здесь не сохраняются. Повторный запуск сохраняет содержимое. Ошибка создания прерывает запуск с явной причиной. Постоянное хранилище и world scope остаются Open до отдельного storage spike. Существующий `server/` — пустой legacy-каталог прежней незавершённой работы, не world-scoped backend; использовать его для приватных данных нельзя.

## Probe contract v1

- C2S `agnor:probe_request`: protocol int32, correlation sequence int64; 12 байт тела.
- S2C `agnor:probe_snapshot`: protocol int32, correlation sequence int64, status int32, server tick int32; 20 байт тела.
- Sequence — correlation ID запроса, не NPC pose sequence. Probe не меняет игровой state, повтор безопасен.
- Status: 0 OK, 1 VERSION_MISMATCH, 2 INVALID_SEQUENCE. Ошибки не раскрывают tick.
- Получатель — только отправитель запроса. UUID берётся из server connection context. В dev offline-стенде проверяется адресность, а не внешняя аутентификация.
- Fabric PlayNetworking handler исполняется на server thread; работа ограничена проверками и ответом. Не запускает IO/HTTP/AI.
- Не более одного ответа на игрока за 20 ticks: лишние запросы отбрасываются. Bookkeeping очищается при disconnect/server stop.
- Клиент отправляет один probe после JOIN, если сервер объявил канал; результат пишет в лог. Автоматический retry и UI пока отсутствуют.
- Мод не обязателен для vanilla-клиента; probe не является продуктовой negotiation/permissions-системой P1.

## Воспроизведение

Windows: `powershell -File scripts/dev.ps1 build`, `powershell -File scripts/dev.ps1 runClient`, `powershell -File scripts/dev.ps1 runServer`.
Другие системы: Java 21 JDK и `./gradlew build`, `./gradlew runClient`, `./gradlew runServer`.
Dedicated server может потребовать принятия Minecraft EULA в созданном run-каталоге; принимайте её после ознакомления. Не включать online-mode=false для обычной игры.

1. Собрать JAR; проверить наличие main/client entrypoints в JAR.
2. Запустить client, создать одиночный мир: проверить создание каталога и `AGNOR probe response: status=0` в логе.
3. Запустить dedicated server и подключить два клиента: проверить отдельные ответы с server tick и отсутствие client classloading error на сервере.
4. Проверить неподдерживаемую версию, отрицательный sequence и burst запросов тестовым клиентом; убедиться, что сервер не падает и не меняет state.
5. Остановить/перезапустить и проверить marker. Custom gameDir/file collision покрыты unit tests. Реальный ACL denial пока НЕ ПРОВЕРЕНО; это отдельный failure scenario при изменении filesystem/P9, не выполненный результат P0.

## Граница результата и перенесённые проверки

| Проверка | Свидетельство / назначение |
|---|---|
| Bootstrap, сборка, каталог | Build PASS, три DataRoots tests PASS; [отчёт 004](reports/2026-11-04-004-phase-0-client-network.md) |
| Integrated/dedicated, restart, два клиента | Логи `build/phase0-evidence/`: адресность, version/sequence errors, burst/replay и marker PASS. Прошлый первый clientA native GLFW crash не объяснён; последующие проходы не доказывают исправление |
| Storage backend, migrations/world isolation | P1.1–P1.2; существующий marker не доказывает сохранение NPC/БД |
| Renderer/format/headless pose | Ограниченный риск P3.1, не P0 |
| Rigged/static assets, Blender adaptation | P3.2, без предварительной загрузки больших наборов |
| Motion/IK/gait/secondary physics | Реализация P3.3–P3.4; это не bootstrap spike |
| Host pose snapshots/two clients/performance | P3.5; probe sequence не является pose sequence |
| Online authentication/raw malformed/точная граница 19/20 tick/ACL | Не подтверждены текущим harness; по затронутому контракту и P9.1 |

Команды ниже нужны для воспроизведения или регрессии после релевантных изменений, а не для повторного запуска перед каждой задачей. Начинать P1.1 разрешено: renderer/storage/motion не входят в критерий P0.

## Воспроизводимый двухклиентский probe-стенд

`src/probe` — отдельный тестовый mod/source set. Подключается только с `-Pphase0`, не входит в обычный JAR. `scripts/dev.ps1` автоматически передаёт этот флаг для задач `probeClasses`, `runProbeServer`, `runProbeA`, `runProbeB`. Существующие `run/` и пользовательские установки не используются стендом.

1. Создать `build/phase0/server/server.properties`:

```properties
server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
view-distance=2
simulation-distance=2
max-players=2
level-name=probe-world
```

2. EULA должна быть принята пользователем в `build/phase0/server/eula.txt`. При уже принятой EULA в локальном dev `run/eula.txt` можно использовать её копию. Скрипты не принимают EULA автоматически.
3. В отдельном терминале запустить `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 runProbeServer`, дождаться `Done`. Gradle проверяет loopback address/port и отказывается запускать этот стенд на внешнем интерфейсе. Offline-mode используется только здесь для двух синтетических dev-идентичностей; проверка Mojang authentication не выполняется.
4. В двух терминалах запустить `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 runProbeA` и `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 runProbeB`. На первом запуске нажать Continue на экране accessibility. Клиенты автоматически подключаются к loopback-серверу.
5. Оба клиента должны увидеть двух игроков, проверить ровно пять ответов (JOIN, неверная версия, отрицательный sequence, первый запрос burst, безопасный replay), записать `PHASE0 A/B PASS` и штатно завершиться. Ответы второго игрока или лишние ответы приводят к FAIL. Тайм-аут ожидания — 3600 client ticks. Проверять также exit code, а не только строку PASS.
6. Остановить сервер командой `stop`, дождаться сохранения. Для restart-проверки сохранить marker в `mods/agnor`, повторить запуск и оба сценария, проверить marker и стабильные dev UUID.

Логи: `build/phase0/{server,clientA,clientB}/logs/latest.log`. Локальные свидетельства текущей работы: `build/phase0-evidence/`. Они игнорируются Git. Стенд проверяет настоящий Fabric C2S/S2C pipeline; он не является pose-sync проверкой, permissions-системой Фазы 1 или AI runtime.
