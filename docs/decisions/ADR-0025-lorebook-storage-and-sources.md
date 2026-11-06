# ADR-0025: Lorebook, scope и pack/local sources

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Accepted / Implemented; проверка P5.1 — отчёт009
- Дополняет ADR-0004/0010/0014/0022/0023.

Lorebook — второй фактический memory writer после History. SQLite schema v4 добавляет только `lore_entry`, не создаёт будущие Diary/Thoughts/ActionMemory/Summary/cursors. Migration использует существующий atomic transaction/pre-backup; conflicting table/schema не сбрасываются. Entry хранит typed JSON, schema1, stable ID, revision, created/updated UTC epoch millis, tombstone и scope `(WorldId,NpcId,PlayerUUID)`. PlayerUUID=NIL обозначает shared NPC knowledge.

Репозиторий проверяет world/active NPC и actor внутри транзакции. Private scope доступен только самому actor (даже admin не читает чужой private scope через этот API). Shared читается посетителями NPC, изменяется owner/admin. Command ingress дополнительно требует trusted connection, selected NPC и nearby/dimension. Scope нельзя расширить через JSON/LLM. Ограничения:≤256rows/scope,≤4096/world, включая tombstones; content4096chars, title128, triggers16×128, priority[-1000,1000].

CRUD имеет optimistic expectedRevision. Create expected0; update текущей revision; одинаковый повтор payload к expected+1 идемпотентен. Delete повышает revision и оставляет tombstone; stale create не воскрешает запись. Явное восстановление использует текущую tombstone revision. Неактивная запись (enabled=false) — осмысленное local override, подавляющее нижележащий источник. Удаление override освобождает нижний pack/shared источник; само pack содержимое не переписывается.

Pack knowledge задаётся объявленным JSON asset `{type:"agnor:lorebook",schemaVersion:1,entries:[...]}`. Schema manifest не меняется. Asset≤1MiB/256entries; generation≤2048entries. ID исходной записи имеет pack namespace, конфликт разрешён только declared override с большей priority/прямой dependency. Ошибка Lore asset отменяет весь stage, прежняя generation остаётся. Scope pack source — активный world + definition/dimension constraints; private player/NPC UUID в pack entry не разрешаются.

Precedence: pack→NPC shared local→текущий player local, затем deterministic priority desc/ID. Все source IDs версионированы: pack digest или local row revision. Pack generation immutable, local reads идут через тот же bounded runtime IO worker. Ничего не синхронизирует local knowledge обратно в pack или другим клиентам.

Activation: ALWAYS; EXACT — NFKC/casefold/whitespace-normalized полное текущее сообщение; KEYWORD — такое же сравнение с Unicode letter/digit boundaries. Triggers — literal strings, произвольные regex запрещены. Definition/dimension constraints проверяются до matching. Roles system/user/assistant — authored metadata, не grant permissions; безопасная интерпретация в единственном ContextBuilder вводится отдельно P5.2. Semantic activation до embeddings не принимается за реализованный consumer.

UI — bounded private command/chat: list20/page, get, add, put JSON, delete с revision. Unicode/multiword triggers заключаются в кавычки по Brigadier StringArgumentType.string. Полноценный graphical editor не вводится. Consumer не подключается до приёмки P5.1; History/provider не меняются этой задачей.
