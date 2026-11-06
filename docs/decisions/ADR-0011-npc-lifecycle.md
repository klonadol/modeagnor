# ADR-0011: стабильный NPC UUID, SQLite lifecycle и временный placeholder

- **Статус:** Accepted — реализовано и проверено в P1.3
- **Дата:** 2026-11-05
- **Заменяет / заменено на:** нет
- **Область:** Runtime / Persistence
- **Основание:** ROADMAP P1.3, Blueprint §3, [ADR-0010](ADR-0010-world-scoped-storage.md)

## Решение

`DefinitionRegistry` хранит immutable versioned definitions, пока только встроенный `agnor:dev`. `RuntimeSnapshot` v1 хранит server-generated UUID, owner UUID, definition ID/revision, dimension, position/yaw/pitch, revision и tombstone. Одна NPC имеет один и тот же UUID в persistence и игровой entity; transient integer entity ID не используется как durable key.

SQLite schema v2 добавляет `npc`, `relationship` и `npc_operation`. При переходе с v1 сначала создаётся snapshot `state-v1-before-v2-<UUID>.db`, затем DDL и user_version меняются одной транзакцией. Отказ migration сохраняет v1/WorldId/старые данные. До появления нового потребителя другие таблицы не создаются.

`NpcRepository` — typed граница SQL. Низкоуровневый JDBC callback `WorldStorage.transaction` имеет package visibility. Создание NPC и запись operation ID атомарны; replay возвращает существующую запись, включая tombstone. Повтор operation ID с другим payload отклоняется. Delete атомарно ставит tombstone и записывает operation ID; повторное удаление безопасно. Snapshot save использует ожидаемую revision. Relationship v1 — минимальное числовое affinity -100..100, отдельный ключ `(NpcId, PlayerUUID)`; это ещё не модель когнитивной памяти или affect. Default отсутствующего отношения — 0. После delete private relationship writer/reader отклоняются; данные остаются для явной будущей политики удаления/retention.

`NpcRuntime` принадлежит одной server session. Все операции с entity, desired state и callbacks выполняются на server thread; SQL — одним IO worker, очередь ограничена 64 задачами, переполнение возвращает failed future. Bounded queue появилась вместе с текущим lifecycle consumer. Startup открывает/мигрирует storage до NPC, после SERVER_STARTED асинхронно читает active snapshots и активирует доступные definitions в загруженных chunks. Config `enabled` управляет запуском runtime, `autosaveSeconds` — сохранением активных snapshots.

Временное представление — наследник vanilla `ArmorStandEntity`, с тем же UUID, no gravity/invulnerable, явно помеченным именем и `shouldSave() == false`. Его entity NBT не является вторым writer. Это позволяет проверять server lifecycle без renderer-библиотеки/моделей; будущий presentation adapter сохранит identity. Пользовательское создание/выбор/удаление и проверки прав подключаются в P1.4.

Unload фиксирует immutable pose, убирает временную entity, сохраняет состояние и не ставит tombstone. Явный unload приостанавливает активацию до load. При удалённой engine entity runtime сохраняет последний доступный pose и повторно активирует её, когда chunk доступен. Load восстанавливает из DB, повтор не создаёт вторую активную entity. Unload/restart восстанавливают данные, а delete запрещает replay-resurrection.

При stop admission закрывается, принятый IO выполняется перед финальным сохранением snapshots; ожидание ограничено 15 секундами. Late callbacks проверяют закрытие session и не активируют entity в следующем мире. Ошибка сохранения явна; SQLite transaction не обещает атомарность с игровым миром. Commit create/delete предшествует соответствующему spawn/discard, поэтому следующий load восстанавливает committed состояние после незавершённого визуального эффекта.

## Альтернативы и последствия

Vanilla entity NBT вместе с БД создал бы двух независимых writers. Перенос всей идентичности в transient entity ID не пережил бы restart. Оба варианта отклонены. Цена выбранного подхода — отдельный lifecycle adapter и очередь IO; живое восстановление из backup должно выполняться только после остановки runtime, поскольку in-memory desired state может быть старым.

Backup/restore API принимает только текущую schema и тот же WorldId. Автоматический pre-migration snapshot v1 нужен для offline recovery прежней версией; текущий restore v2 не выполняет downgrade. Политики очистки operation/tombstone records без текущего потребителя не вводятся.

## Свидетельства и ограничения

[Отчёт 005](../reports/2026-11-05-005-npc-lifecycle.md): 17 unit tests PASS; dedicated и integrated create→unload→load→stop→restart→delete/replays PASS в изолированных dev fixtures. Проверены migration success/failure, stable IDs, отдельные relationship records и world isolation. Полный пользовательский путь с командами/permissions/двумя подключёнными игроками — P1.4. Визуальное качество, natural chunk travel, forced process death во время SQL commit, физический disk-full/ACL и installed release-JAR запуск не объявляются проверенными.

Дополнение после P1.4: команды/permissions/двухклиентский сценарий и integrated user commands подтверждены [отчётом 006](../reports/2026-11-05-006-phase1-management.md), [ADR-0012](ADR-0012-npc-management-protocol.md). Первоначальные ограничения P1.3 не переписаны задним числом; остальные перечисленные непроверенные случаи сохраняются.
