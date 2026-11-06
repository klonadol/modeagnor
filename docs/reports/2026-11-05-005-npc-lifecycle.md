# Отчёт 005: завершение зависимости P1.2 и lifecycle P1.3

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10).
- **Статус:** Done — P1.3, в перечисленных ниже проверенных границах.
- **Ветка / HEAD до и после:** main / `338b491517a23720b7ecacfe32fc8c7d13ef8ad6`. Коммит/переключение ветки не выполнялись.
- **ADR:** [0010](../decisions/ADR-0010-world-scoped-storage.md), [0011](../decisions/ADR-0011-npc-lifecycle.md).

## Исходная задача и последовательность

Пользователь потребовал последовательное продолжение без перехода к следующей задаче до реализации предыдущей. До этой работы дерево содержало незакоммиченные P1.2 и начало P1.3 из отчётов 003–004; изменения сохранены. Сначала проверены требования P1.2 и исправлены два подтверждённых дефекта: open не должен генерировать новую identity при потере metadata, restore отсутствующего snapshot не должен создавать пустую DB. Целевые storage tests прошли до продолжения P1.3. Далее добавлена защита от reset уже существующей пустой DB и проверена отдельным тестом.

Критерии P1.2: initial schema/version и WorldId, scoped transaction, same-version/world backup/restore, error без потери прежней записи (реальная SQLITE_FULL через max_page_count в отчёте 004), config save/reload подтверждены. Реальный disk-full/ACL ОС не подменён этим тестом; это отдельное ограничение. После этой зависимости выполнена P1.3. P1.4 не начиналась.

## Реализация и файлы

| Файлы | Что реализовано |
|---|---|
| `character/RuntimeSnapshot.java`, `character/NpcRuntime.java` | Stable instance/entity UUID, versioned runtime pose, create/load/unload/delete, queued IO, startup restore, periodic/stop save, временная vanilla entity без собственного NBT writer |
| `storage/NpcSchema.java`, `storage/NpcRepository.java` | Schema v2, typed SQL, раздельные отношения игроков, create/delete operation dedupe, tombstone, revision checks |
| `storage/WorldStorage.java` | Миграция v1→v2 с backup и transactional DDL; отказ вместо reset WorldId, защита от отсутствующего restore source; JDBC transaction теперь только для infrastructure package |
| `Agnor.java` | Server session runtime startup/tick/stop, применение world config |
| `storage/WorldStorageTest.java`, `storage/NpcRepositoryTest.java` | Отказные случаи storage, typed lifecycle, сохранение relationships, миграции и изоляция |
| `scripts/lifecycle-check.gradle`, `src/lifecycleCheck/`, opt-in блок `build.gradle` | Изолированный dev harness для dedicated/integrated двухпроцессного сценария; не входит в normal JAR |
| README, ROADMAP, PROJECT_MAP, журнал, Blueprint/архитектура, ADR/индекс, workspace указатель | Актуальный статус, контракт и фактические результаты |

## Проверки и результаты

Использован существующий JDK 21 из `%APPDATA%/ElyPrismLauncher/java/java-runtime-delta`; `GRADLE_USER_HOME=<repo>/.gradle`. Основные команды:

```powershell
.\gradlew.bat test --tests dev.agnor.storage.WorldStorageTest --console=plain
.\gradlew.bat build lifecycleCheckClasses -PlifecycleCheck --console=plain
.\gradlew.bat runLifecycleCheck -PlifecycleCheck -PlifecycleStage=1 --console=plain
.\gradlew.bat runLifecycleCheck -PlifecycleCheck -PlifecycleStage=2 --console=plain
.\gradlew.bat runLifecycleClient -PlifecycleCheck -PlifecycleStage=1 --console=plain
.\gradlew.bat runLifecycleClient -PlifecycleCheck -PlifecycleStage=2 --console=plain
.\gradlew.bat build --console=plain
```

Финальная build: exit 0, `BUILD SUCCESSFUL in 10s`; 8 tasks executed, 2 up-to-date. Всего **17 unit tests**, failures/errors/skipped=0: DataRoots 3, DefinitionRegistry 2, WorldStorage 8, NpcRepository 4. Проверены stable IDs/reopen, scope rejection, SQL FK/FULL, same-version backup restore/config, unknown/duplicate definition, optimistic revision, idempotency payload conflict, tombstone/replay, два save roots, migration success с backup и rollback DDL при отказе без потери v1/identity/данных.

### Dedicated и integrated

Оба режима прошли одинаковые две стадии в разных процессах:

1. Create `agnor:dev`, проверка entity по тому же UUID, повтор create с тем же operation ID, изменение pose, unload (entity исчезает), load (pose сохранён), запись разных отношений для двух synthetic player UUID. Штатный stop сохраняет NPC.
2. Новый процесс открывает тот же save: WorldId стабилен, NPC UUID/pose и оба отношения восстановлены; delete, повтор delete, отказ load tombstone; replay первоначального create возвращает deleted snapshot и не создаёт entity.

Dedicated: loopback `127.0.0.1:25576`, offline-mode только в локальном fixture. Рабочий каталог `build/lifecycle-check/server`. В `eula.txt` скопирована ранее принятая пользователем dev EULA из `run/eula.txt`, новая EULA автоматически не принималась. Integrated: отдельный `build/lifecycle-check/client/saves/check-world`, создан из dev world data с исключённым каталогом `agnor` и session.lock; новый WorldId, отдельный storage. Сохранения пользователя не менялись. Клиент открывал fixture через `--quickPlaySingleplayer` и штатно завершался после сохранения integrated server.

Фактические успешные результаты:

| Запуск | PASS marker в логе | Gradle exit / время |
|---|---|---|
| Dedicated stage 1 | `LIFECYCLE CHECK PASS stage=1` | 0 / 23s |
| Dedicated restart stage 2 | `LIFECYCLE CHECK PASS stage=2` | 0 / 21s |
| Integrated stage 1 | `LIFECYCLE CHECK PASS stage=1` | 0 / 36s |
| Integrated restart stage 2 | `LIFECYCLE CHECK PASS stage=2` | 0 / 40s |

### Первоначальная неудача

Первый dedicated harness обращался к runtime в SERVER_STARTED до выполнения listener мода из-за порядка инициализаторов. Возникло `NPC runtime unavailable`, хотя Gradle сообщил exit 0/BUILD SUCCESSFUL. Это **FAIL**, не принятое свидетельство. Стенд исправлен: его сценарий начинается на первом END_SERVER_TICK. Неудачный лог сохранён как `stage1-attempt1.log`; последующие проходы проверены по PASS marker, штатному save и exit code. В первом создании flat fixture также был warning/error generator settings о layers; он не препятствовал запуску, изолированное world data далее использовалось для storage/lifecycle, без оценки генерации мира.

## Локальные evidence и воспроизведение

- `build/lifecycle-check/{stage1-attempt1,stage1,stage2,integrated1,integrated2}.log`;
- `build/lifecycle-check/{server,client}/{npc-id.txt,PASS-1,PASS-2}`;
- DB: `server/check-world/agnor/state.db`, `client/saves/check-world/agnor/state.db`;
- `build/test-results/test/TEST-*.xml` и `build/libs/agnor-0.1.0-dev.jar`.

Финальная независимая сверка файлов: 132 локальные ссылки разрешаются, XML подтверждает 17 tests без failures/errors/skipped; normal JAR содержит runtime и nested SQLite driver и не содержит классов harness. Обе БД открыты read-only: schema=2, разные WorldId, ровно одна deleted NPC, pose `(3,81,4,12,2)`, два relationship значения `21`/`-7`, две operation записи; integrity_check=ok и foreign_key_check пуст. Все четыре успешных лога содержат свой PASS marker и BUILD SUCCESSFUL. `git diff --check` прошёл (только уведомления Git о LF/CRLF). Краткий локальный итог сохранён в `build/lifecycle-check/final-checks.txt`.

Стенд opt-in (`-PlifecycleCheck`), обычные `build`/JAR не включают harness. Для полного нового прогона требуется свежий fixture directory: в завершённом stage 2 сохраняется tombstone, повторять stage 1 на нём как новый сценарий нельзя. Сначала архивировать старый `build/lifecycle-check`, приготовить новый loopback server.properties (port 25576, level-name check-world, view/simulation distance 2, accepted dev EULA), затем stages 1→2. Integrated fixture должен иметь отдельный root и новый storage, готовый мир в saves/check-world и options с завершённым accessibility onboarding. Подготовка и launcher не принимают EULA за пользователя.

## Ограничения и результат

Dev Fabric runtime и реальные integrated/dedicated server paths проверены. Отдельный installed production JAR с nested JDBC не запускался; его состав проверяется отдельно. Не проверены visual quality, gameplay commands/UI/permissions, два подключённых игрока, natural chunk travel, forced crash в середине физического commit, OS disk-full/ACL, полная compatibility/performance matrix. Synthetic player IDs в relationships не доказывают authentication/permissions. P1.4 отвечает за управление/права/snapshots и свой двухклиентский сценарий; вся P1 ещё не завершена.

**P1.3 завершена. Следующая незавершённая задача — P1.4.**
