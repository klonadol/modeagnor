# Отчёт009: P5.1 Lorebook и интерфейс записей

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P5.1 Verified
- main; HEAD до/после `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`; без commit/push
- ADR: [0025](../decisions/ADR-0025-lorebook-storage-and-sources.md)

## Требование / исходное состояние

После завершённой P4 выполнена P5.1: CRUD, Always/exact/keyword, roles, scopes, priority, pack/local overrides. P3/P4 working diff сохранён. До изменения Lorebook не существовал, DB schema3, ContextBuilder помечал lorebook NOT_YET_ENABLED. P5.2 до приёмки этой задачи не начиналась.

## Изменения

`lore/{LoreEntry,LoreJson,LoreRecord,PackLore}.java`, `storage/LoreRepository.java`, `WorldStorage.java`: schema4/pre-backup/atomic migration, scoped revisions/tombstones/limits и deterministic literal activation. `content/PackJson.java` и `PackGeneration.java`: общий strict parser, typed `agnor:lorebook` JSON assets и atomic resolved sources. `NpcRuntime.java`: bounded IO facade и immutable effective reads. `network/LoreCommands.java`, `Agnor.java`: private command/chat CRUD/20-row pages с trusted actor/nearby и repository permission checks.

Документация: ADR0025 и `docs/MEMORY.md`. Проверки: `src/test/java/dev/agnor/content/LoreBookTest.java` (5), исправленная старая v2 downgrade fixture `HistoryRepositoryTest`, opt-in `src/p5Check/` + `scripts/{p5-check.gradle,prepare-p5-check.py}`/build flag.

## Проверки / ошибки

`scripts/dev.ps1 test`:110total/107PASS/3existing opt-in skipped. Новые cases: create/update/repeat/conflict/delete/tombstone/reopen, world/NPC/player scope, shared owner/admin policy, Unicode exact/keyword и constraints, typed pack source/local disabled override, v3→v4 migration/pre-backup и rollback при conflicting table. Старый migration test сначала FAIL: fixture опускала текущую DB доv2, но оставляла новую lore_entry; исправлено удаление post-v2 таблицы и backup expectation до current version. Production silent reset не добавлялся.

Actual `runP5Server/runP5A/runP5B -PmemoryCheck`: final logs `build/p5-check/{server,clientA,clientB}-crud-final.log`, все wrapper exits0/fresh host+clients PASS. A через реальные команды создал private keyword/local override pack fact, обновил доrevision2/COBALT-732, создал shared fact, создал/удалил temp (tombstone2), stale create отклонён. B не смог написать shared evil, создал только свой private secret; owner scope его не видит. Server проверил фактические repository rows/revisions/permissions, не только presence UI markers. NPC UUID `514a8939-551b-445c-862f-a2a999f8a59b` сохранился между двумя запусками. Command UI PNG сохранены, private данные только synthetic.

Первая runtime попытка завершилась semantic FAIL: fixture отправила unquoted кириллический trigger, Brigadier корректно отклонил его до storage. Другие команды сработали, но это не выдано за PASS. Архив `evidence/crud-unquoted-trigger`. Fixture исправлена с кавычками, Gradle doLast теперь проверяет fresh PASS и делает wrapper FAIL при semantic failure. Повторный запуск PASS без удаления мира/обхода tombstones.

## Границы / продолжение

Это записывающий/управляющий слой P5.1. LLM/context consumer ещё не подключён и реальный knowledge-guided reply не заявлен; следующая P5.2. Полная P5/integrated/final backup/retention приёмка впереди. UI — команды/чат, не graphical editor. Semantic search/embeddings и остальные memory writers не создавались. Физический disk-full/ACL не проверен; транзакционные conflicts/migration rollback проверены. Никакие pack/private знания не логируются production logger и не передаются чужому player UI.
