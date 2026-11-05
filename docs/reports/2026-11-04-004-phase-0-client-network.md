# Отчёт 004: Client lifecycle и двухклиентский probe Фазы 0

- **Дата:** 2026-11-04 (Asia/Vladivostok)
- **Статус:** Partial — Фаза 0 In progress
- **Ветка:** main
- **HEAD до/после:** 5d06367178325fcb6b41c1075af1ae0c5cc91613 (коммиты не создавались)
- **Связанные ADR:** ADR-0001, ADR-0002, ADR-0003
- **Связанный этап:** Фаза 0, bootstrap/catalog/network proof; Blueprint 0.6, 1–3, 15, 25

## 1. Цель и исходное требование

Продолжить с первой незавершённой задачи: реальный запуск integrated/client и проверка базового packet на dedicated server с двумя клиентами. Не объявлять Фазу 0 завершённой без всех её gates.

## 2. Состояние до изменений

Рабочее дерево уже содержало незакоммиченные build/source/docs/skill. Они сохранены. Существовали bootstrap, DataRoots, read-only probe и три теста DataRoots. Ни NPC, ни renderer, ни storage backend не реализованы. Skill и нижняя сводка журнала ошибочно объявляли переход к Фазе 1 вопреки текущему ROADMAP. GitHub connector вернул пустые PR search, commit statuses и PR-triggered workflow runs для HEAD; это не успешный CI и не проверка локальных изменений. Actions не запускались.

## 3. Выполненные действия

Прочитаны skill, architecture, roadmap, blueprint и относящиеся ADR/отчёты. Повторный runClient завершил downloadAssets без изменения зависимостей. Через UI создан новый локальный dev-мир, подтверждены загрузка, probe response, save/quit и повторный вход. Контрольный marker пережил повторный SERVER_STARTING.

Выявлено отсутствие client catalog при подключении к dedicated: SERVER_STARTING не вызывается в удалённом клиенте. Создание через существующий DataRoots добавлено в client initializer, с явным отказом при IOException. Общий серверный код и wire contract не менялись.

Создан отдельный opt-in source set `probe` и local-only run configurations. Скриптовые клиенты используют production codecs/handlers, ждут двух игроков, проверяют ответы и заканчивают работу. Никаких NPC, AI, renderer или persistence заглушек не добавлено.

## 4. Изменённые файлы

| Файлы | Изменение |
|---|---|
| `src/client/java/dev/agnor/client/AgnorClient.java` | Подготовка клиентского каталога независимо от local server |
| `src/probe/java/dev/agnor/probe/ProbeHarness.java`, `src/probe/resources/fabric.mod.json` | Изолированный двухклиентский network acceptance harness |
| `build.gradle`, `scripts/phase0.gradle`, `scripts/dev.ps1` | Opt-in запуск стенда; loopback guard; обычный artifact не содержит harness |
| `skills/agnor-development/SKILL.md`, `docs/DEVELOPMENT_LOG.md`, `docs/ROADMAP.md`, `docs/PHASE_0_SPIKES.md` | Фактический статус, результаты, воспроизведение |

## 5. Решения

Новых архитектурных решений нет. Границы и очередность фаз не менялись. Исправлена устаревшая фактическая сводка; старые отчёты оставлены историей. Существующий пустой `mods/agnor/server` не используется как хранилище мира.

## 6. Проверки и результаты

| Проверка | Результат / свидетельство |
|---|---|
| `scripts/dev.ps1 runClient`, новый одиночный мир | PASS: UI world visible; integrated startup; probe status=0 tick=31 |
| Save/quit → повторный вход | PASS: второй SERVER_STARTING; probe status=0 tick=9; marker сохранён |
| `scripts/dev.ps1 probeClasses` | PASS после исправления тестового вызова unregisterGlobalReceiver (требует Identifier) |
| `runProbeServer` | PASS: Done, gameDir=`build/phase0/server`, каталог создан; listen только 127.0.0.1:25575 |
| `runProbeA` + `runProbeB` | PASS: оба видят двух игроков, пять ожидаемых адресных ответов, коды 0/1/2, error tick=0, burst/replay; оба exit=0 |
| Remote-only client directory | PASS: новый clientA создаёт `build/phase0/clientA/mods/agnor` до подключения |
| `scripts/dev.ps1 build` | PASS; существующие 3 DataRoots tests UP-TO-DATE; test logic не менялась |
| JAR inventory | PASS: main/client entrypoints и ProbeProtocol присутствуют, ProbeHarness отсутствует |

Первый успешный dedicated test: A UUID `35deb110-752a-3928-95c4-cc89250ce08e`, B UUID `0d3214da-0cab-3a46-8ffd-470d2bef9d55`; A PASS 23:45:57, B PASS 23:45:56. Сервер остановлен через `stop`, сохранение завершилось, exit=0.

Повторный запуск dedicated и обоих клиентов после исправления: **PASS**, A 23:48:41, B 23:48:42, оба exit=0. UUID и marker `dedicated-restart-preserve` сохранились. Оба клиента создали собственные каталоги независимо от серверного. Сервер снова штатно остановлен через `stop`. Логи: `build/phase0-evidence/clientA-restart-pass.log`, `clientB-restart-pass.log`, `server-restart.log`. Native crash при повторе не возник. `git diff --check` PASS. Запущенные этой работой dev-клиенты/серверы завершены; пользовательский Minecraft 1.21.11 не изменялся.

Локальные evidence files: `build/phase0-evidence/integrated-first.log`, `integrated-restart.log`, `clientA-pass.log`, `clientB-pass.log`, `server-first.log`, `clientA-native-crash.log`.

## 7. Ошибки и диагностика

Первый clientA завершился нативным EXCEPTION_ACCESS_VIOLATION в `glfw.dll+0x101ab`, Java stack: `RenderSystem.limitDisplayFPS`. Это не Java assertion и не отказ probe. Причина не установлена; crash-log сохранён. Повторный запуск прошёл без обновления Java/LWJGL/драйверов. Отдельно первоначальный compile harness выявил неверный тип аргумента unregister; исправлен вызов `.ID.id()`, wire contract и production tests не менялись.

## 8. Риски и ограничения

Offline loopback dev identities проверяют connection addressing/разные UUID, но **НЕ ПРОВЕРЕНО** внешнее Mojang authentication. Реальный filesystem ACL denial **НЕ ПРОВЕРЕНО**; unit test проверяет безопасный отказ при file/directory collision. Malformed/truncated wire payload и точная граница rate-limit 19/20 ticks этим harness не проверяются. Native GLFW причина остаётся неизвестной. Renderer/assets/motion/host pose/storage **НЕ ПРОВЕРЕНО** и не считаются завершёнными.

## 9. Итог

Integrated runtime/re-entry и базовая dedicated сеть получили реальные подтверждения. Client catalog defect исправлен. Фаза 0 ещё не завершена. Следующий шаг после runtime/restart — renderer comparison в пределах Фазы 0; в Фазу 1 не переходить.

## 10. Ссылки

- [Протокол и команды](../PHASE_0_SPIKES.md)
- [Roadmap](../ROADMAP.md)
- [Fabric Loom source-set documentation](https://docs.fabricmc.net/develop/loom/); конкретный run DSL дополнительно проверен по локальному Loom 1.7.4 `RunConfigSettings` через javap.
