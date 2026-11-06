# ADR-0010: SQLite и граница сохранения мира для первой NPC

- **Статус:** Accepted — production handle/config реализованы с ограничениями P1.2
- **Дата:** 2026-11-05
- **Заменяет:** нет; уточняет предварительный backend в архитектуре/Blueprint
- **Заменено на:** нет
- **Область:** Persistence / Runtime / Isolation

## Контекст и предел исследования

P1.1 выбирает backend для одной NPC и двух отдельных relationship records. Требуются совместный commit/rollback, восстановление после открытия новым процессом, миграция версии и отсутствие влияния на второй save с теми же NPC/player UUID. Проверки не требуют создания entity, Storage Facade, config, памяти или worker infrastructure.

Критерий выбора: корректность всех перечисленных сценариев, затем количество собственного кода для транзакции и миграции нескольких записей. Условие остановки: один ограниченный fixture и документированный world contract дают достаточное основание для решения. Скорость и масштаб не являются предметом этого выбора; performance PASS не заявляется.

## Рассмотренные альтернативы

| Критерий | SQLite, одна БД на save | JSON, один полный snapshot на save |
|---|---|---|
| Один NPC + две записи отношений | Отдельные строки, ключи и FK | Один документ, словарь отношений по PlayerUUID |
| Совместное изменение | SQL transaction | Копия всего документа, temp + flush + replace |
| Явный откат / выход процесса до commit | PASS в fixture | PASS в fixture, canonical snapshot не заменён |
| Restart новым процессом после commit | PASS | PASS |
| Миграция v1→v2 / откат миграции | DDL и user_version в одной транзакции, PASS | Перезапись полного snapshot с schema, PASS |
| Два мира с одинаковыми NPC/player IDs | Два файла БД, PASS | Два файла JSON, PASS |
| Стоимость поддержки | Нужны JDBC/native packaging и управление connection | Не нужен SQL driver; собственные validation, locking и протокол замены файла |

JSON не отвергнут как неработающий: на данном объёме оба варианта прошли. Выбран **SQLite** за встроенную общую транзакцию состояния NPC и независимых отношений, ограничения целостности и транзакционную миграцию. JSON остаётся направлением для конфигураций и переносимых определений; их формат здесь не проектируется. Разделённые JSON-файлы на NPC/игрока не выбраны: обычная замена каждого файла не даёт общей транзакции.

Документация SQLite описывает atomic commit и его зависимость от filesystem/flush; это основание выбора, а не наш тест потери питания: [Atomic Commit](https://www.sqlite.org/atomiccommit.html). `user_version` управляется приложением: [PRAGMA](https://www.sqlite.org/pragma.html#pragma_user_version). Для Java JSON-варианта `ATOMIC_MOVE` может быть недоступен, замена существующей цели зависит от реализации: [Java 21 Files.move](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)). Нельзя незаметно заменять atomic move на truncate/write.

## Решение: world-scoped contract

Это обязательства следующей реализации, **не существующий API**.

1. **Источник пути:** только `MinecraftServer.getSavePath(WorldSavePath.ROOT)` → `agnor/state.db`. Оба серверных режима используют этот server API. Нельзя строить путь из client gameDir, имени мира, адреса сервера или packet input. Наличие API сверено по [Yarn MinecraftServer 1.21.1+build.3](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/server/MinecraftServer.html#getSavePath(net.minecraft.util.WorldSavePath)) и [WorldSavePath.ROOT](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/util/WorldSavePath.html#ROOT). Вызов в игровом lifecycle пока не проверен.
2. **Layout:** `<server-save>/agnor/state.db`; rollback journal живёт рядом и принадлежит SQLite. Это весь save, включая Overworld/Nether/End: dimension — поле позиции, не отдельный WorldId/файл. Примеры: integrated `<gameDir>/saves/<save>/agnor/state.db`; dedicated `<resolved level save>/agnor/state.db`. Примеры не заменяют разрешение пути через сервер.
3. **Identity:** WorldId — случайный UUID в metadata той же БД, создаваемый вместе с начальной схемой одной транзакцией. Он стабилен после restart/переименования save. Пустая новая БД получает новый ID; существующая повреждённая БД не пересоздаётся. Копия save сохраняет WorldId как ту же линию сохранения; физические handle и файлы остаются раздельными. Global cache по одному WorldId запрещён. Отдельный импорт/клонирование с выдачей нового ID здесь не создаётся.
4. **Scope:** handle привязан к конкретному server/save и ожидаемому WorldId; несовпадение scope отклоняется до записи. NpcId — стабильный server-generated UUID, не transient entity integer ID. Runtime keyed by `(WorldId, NpcId)`; relationship keyed by `(WorldId, NpcId, PlayerUUID)`. В отдельной БД WorldId может храниться единожды в metadata, NpcId — PK, `(NpcId, PlayerUUID)` — составной PK/FK. Владелец NPC и отношения разных игроков — разные данные. UUID игрока берётся из доверенного server context; права проверяет consumer, SQLite сам не является permissions-системой.
5. **Минимальные данные:** schema version, WorldId; NPC UUID, definition ID/revision, owner UUID, versioned runtime snapshot с dimension/position и revision; отношения отдельно по игроку. Fixture использует только definition/x/affinity, не объявляет это production schema. Точные typed поля и constraints вводятся вместе с потребителем P1.2–P1.3. Memory stores, outbox, embeddings и очереди не создаются.
6. **Транзакция:** одна операция в одном save, согласованное изменение NPC и нужных relationships, commit целиком или rollback целиком; без cross-world/attached DB transactions. При stale revision операция отклоняется. Operation ID и permission context проходят вместе с командой consumer; таблица dedupe без consumer сейчас не создаётся. SQLite commit не охватывает Minecraft entity/NBT: порядок применения и reconciliation относятся к P1.3, не обещают общей атомарности с миром игры.
7. **Lifecycle/threads:** handle принадлежит server session, открывается/проверяет схему до активации NPC, закрывается после завершения принятых операций при stop. Entity unload не закрывает БД и не означает delete. Server thread владеет игровым состоянием и делает immutable snapshot; сериализованный storage IO владеет connection вне tick/network callback, результат возвращается в живую server session с проверкой scope/revision. Нельзя переносить connection или результат в следующий открытый мир. Worker/queue и public API в P1.1 не реализуются.
8. **Ошибки/версии:** начальная политика — один writer, `foreign_keys=ON`, rollback journal `DELETE`, `synchronous=FULL`; WAL сейчас не нужен. Schema version меняется в той же транзакции, что и DDL/data migration. Более новая неизвестная схема блокирует активацию storage без downgrade/перезаписи. I/O, busy, constraint и corruption дают конечную ошибку и не подтверждают сохранение; нельзя fallback-ить в общий каталог или пустую БД. Timeout/backup/restore и тест реальной ошибки записи — P1.2. После неоднозначной ошибки commit требуется повторное чтение состояния перед повтором операции; автоматический blind retry запрещён.

`DataRoots.serverData` — только существующий legacy путь `<gameDir>/mods/agnor/server`. **Он не является world storage, никогда не передаётся Storage Facade и не принимается как fallback.** Приватных записей там сейчас нет; автоматического переноса/удаления legacy каталога не делаем. До первой приватной записи будущий код обязан использовать server-save путь выше. Исходник bootstrap сейчас не меняется.

## Проверка и последствия

Запущен [scripts/storage_spike.py](../../scripts/storage_spike.py): Python 3.14.7, SQLite 3.50.4, Windows 11. Это изолированный stdlib engine experiment, не Java/Fabric storage adapter. Для каждого backend прошли 7 групп проверок; процессный выход `os._exit(73)` моделирует прерывание до commit, а не потерю питания. SQLite дополнительно проверен `integrity_check`/`foreign_key_check` при чтении. Подробности и сохранённые результаты — [отчёт P1.1](../reports/2026-11-05-002-storage-contract.md).

**НЕ ПРОВЕРЕНО:** Java JDBC driver/native loading и упаковка в Fabric JAR, поддержка других ОС/filesystems, disk-full/ACL, crash во время физического commit, конкурирующие writers, backup/restore, integrated/dedicated NPC restart. Версия JDBC не выбрана и production dependency не добавлена: её закрепление и минимальная проверка входят в реализацию P1.2. Выбор SQLite engine принят; совместимость будущего Java adapter не объявляется доказанной результатом Python.

P1.1 принято как backend decision. P1.2 реализует minimal Storage Facade/config; [P1.3/ADR-0011](ADR-0011-npc-lifecycle.md) добавляет typed NPC records, schema v2 и migration v1→v2. Исторические ограничения Python-исследования выше относятся к моменту P1.1; Java engine, native loading в dev Fabric, integrated/dedicated lifecycle/restart теперь подтверждены [отчётом 005](../reports/2026-11-05-005-npc-lifecycle.md). Installed release-JAR, OS-level disk-full/ACL и forced power failure остаются непроверенными. Полная P1 требует P1.4.

P1.4 впоследствии завершена: [отчёт 006](../reports/2026-11-05-006-phase1-management.md), P1 принята в границах roadmap. Ограничения release-JAR/OS failure/power failure выше сохраняются.
