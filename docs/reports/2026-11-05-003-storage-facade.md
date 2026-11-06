# Отчёт 003: P1.2 — минимальный world Storage Facade

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10)
- **Статус:** Done with limitations — criteria P1.2 covered by code/unit checks; OS/game runtime errors remain open
- **Ветка / HEAD до работы:** `main` / `338b491517a23720b7ecacfe32fc8c7d13ef8ad6`
- **HEAD после работы:** не создавался, код и документация остаются в рабочем дереве
- **ADR:** [ADR-0010](../decisions/ADR-0010-world-scoped-storage.md)
- **Этап:** P1.2

## Цель и исходное состояние

Roadmap P1.2 требует minimal Storage Facade, versioned schema, scope transaction, backup/restore одной версии, сохранение предыдущего состояния при неуспешной операции и save/reload config. P1.1 уже выбрала SQLite, `<server-save>/agnor/state.db`, WorldId и запрет на общий `DataRoots.serverData`.

До изменения существовали Fabric bootstrap/probe и тесты каталогов, production database/Java driver отсутствовали. Рабочее дерево включало незакоммиченные уточнения P1.1 в DEVELOPMENT_LOG, PROJECT_MAP и отчёте 002.

## Реализация

- Добавлены `WorldStorage` и `WorldConfig` в common/server source set. `WorldStorage.open` принимает только server save root, создаёт `<save>/agnor/state.db`, задаёт DELETE journal/FULL synchronous/foreign keys, инициализирует schema version 1 и persistent WorldId транзакционно. Неизвестная более новая версия отклоняется.
- Scoped transaction проверяет ожидаемый WorldId, выполняет callback в SQL transaction, commit-ит при успехе и rollback-ит при исключении. Storage handle закрывается при server stopping.
- Backup использует SQLite `VACUUM INTO`, валидирует integrity/schema и не перезаписывает существующую цель. Restore валидирует integrity, текущую schema и тот же WorldId, staged-копию заменяет atomic move. Ошибка проверки/копирования до замены оставляет активный файл прежним.
- `WorldConfig` хранит только минимальные `enabled` и `autosaveSeconds` в `config.properties` под этим world save; валидирует версию/поля, пишет temp и atomic replace, не загружает молча повреждённое значение.
- Зафиксирован `org.xerial:sqlite-jdbc:3.51.0.0` и вложен в JAR через Loom include. `Agnor` открывает DB и читает config на `SERVER_STARTING`; storage handle закрывается на `SERVER_STOPPING`. Runtime values конфигурации пока только логируются.
- Добавлены четыре контрактных теста. Ни один NPC/entity/registry, settings UI, permission system, relationship table или другие будущие stores не добавлены.

## Файлы

| Путь | Результат |
|---|---|
| `build.gradle` | Закреплён и включён SQLite JDBC driver |
| `src/main/java/dev/agnor/storage/WorldStorage.java` | Schema metadata, WorldId, scoped transactions, backup/restore |
| `src/main/java/dev/agnor/storage/WorldConfig.java` | World settings validation/save/reload |
| `src/main/java/dev/agnor/Agnor.java` | Server save lifecycle open/close |
| `src/test/java/dev/agnor/storage/WorldStorageTest.java` | Четыре тестовых сценария P1.2 |
| `docs/` | P1.2 status, project map, architecture, ADR, roadmap, журнал и этот отчёт |

## Проверки

Выполнена команда:

```powershell
.\gradlew.bat build --console=plain
```

Итоговый запуск: `BUILD SUCCESSFUL in 8s`, exit 0; 10 tasks, 7 executed, 3 up-to-date. В `TEST-dev.agnor.storage.WorldStorageTest.xml`: 4 tests, 0 failures/errors/skipped. Проверены schema/WorldId/scope rejection, rollback и reopen, backup→изменение→restore, отказ для чужого мира, config save/reload и отказ для недопустимой версии/значения. Ранее в том же изменении также был успешный build с теми же 4 тестами.

В `build/libs/agnor-0.1.0-dev.jar` выполнен `jar tf`: присутствуют `dev/agnor/storage/WorldStorage.class` и `META-INF/jars/sqlite-jdbc-3.51.0.0.jar`. Начальный Gradle resolution напечатал Loom предупреждение, что четырёхсегментная версия не соответствует его semver-строке; разрешение/сборка прошли и артефакт включён. Следует проверить переносимый Fabric runtime packaging при фактическом запуске.

`git diff --check` и итоговая проверка ссылок выполняются после документирования; dedicated/integrated server не запускались.

## Пределы результата

- **НЕ ПРОВЕРЕНА:** реальная ошибка ОС при записи/disk-full/ACL и подтверждение предыдущего состояния через Java driver. Тест исключения в transaction проверяет rollback; это не симуляция disk failure.
- **НЕ ПРОВЕРЕНА:** запуск integrated/dedicated server с driver из remapped mod JAR и restart Minecraft save.
- **НЕ РЕАЛИЗОВАНЫ:** backup UI/команда, retention/rotation, transactional application-level NPC migration, typed NPC operations и synchronisation/reconciliation с Minecraft entity/NBT. Restore требует остановки мира/закрытого внешнего доступа к DB; Facade не делает live restore coordination.
- Config — маленький Properties файл, не общая settings system. Network, UI, permissions и продуктовые настройки не добавлены.

**Итог:** P1.2 закрывает минимальный storage/config slice. Следующая roadmap-задача — P1.3, Definition Registry и entity UUID/lifecycle. Интеграционные ограничения выше должны учитываться в следующих затронутых критериях; они не превращены в выполненные проверки.
