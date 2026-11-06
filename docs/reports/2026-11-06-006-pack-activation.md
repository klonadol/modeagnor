# Отчёт006: P4.2 Registry, activation и внешняя модель

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P4.2 Verified в описанных границах
- Ветка main, HEAD до/после `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`; без commit/push
- Решение: [ADR-0023](../decisions/ADR-0023-content-generations-and-presentation.md)

## Требование и исходное состояние

После приёмки P4.1 (отчёт005) реализованы stage/enable/disable/reload/rollback, capability и внешний definition через существующий Registry с тем же NPC identity. P3 diff сохранён. Перед этой работой loader был пассивным, renderer использовал только local dev properties/resources.

## Изменения

- `content/{PackGeneration,PackStore,ContentService,PackResources}.java`: validated candidate, bounded worker, atomic snapshot pointers, previous rollback, no-op identical inventories, безопасное render resource binding.
- `character/{CharacterDefinition,DefinitionRegistry,NpcRuntime,NpcEntity}.java`, `storage/NpcRepository.java`, `Agnor.java`: prompt, single Registry publication, stable redefine, missing-definition fallback, lifecycle restore, public tracked descriptor.
- `dialogue/{ContextBuilder,DialogueService}.java`: prompt inside existing host context budget/policy; cancellation on content changes.
- `motion/{MotionRig,MotionService}.java`: byte metadata, per-NPC rig, monotonic sequence through static/motion changes.
- `client/{ContentClient,ContentResources,GemNpcRenderer,NpcMotionClient}.java`, `client/mixin/ContentProviderAccess.java`, client mixin config и `fabric.mod.json`: exact local inventory, contained provider, resource reload/rollback, same entity fallback/cache invalidation.
- `PackLoader.java`: semantic memory bounds GLB/PNG; tests `content/PackActivationTest.java` (6).
- `src/p4Check/`, `scripts/{p4-check.gradle,prepare-p4-check.py}`, opt-in build flag: отдельный ignored runtime стенд, не normal JAR.

## Проверки

`scripts/dev.ps1 test`: 100 tests total, 97 PASS, 3 existing opt-in skipped, 0failures. Новые сценарии: enable→failed reload→disable→rollback→restart; busy/dependency/path; deterministic filesystem commit failure; redefine с сохранением History/relationship/UUID; stripping untrusted resource hooks; malformed rig/accessor bomb/node cycle rejection. Начальная ошибка test вызова accept signature исправлена; assertion comparing records с byte[] заменён сравнением inventory digest.

Dedicated `runP4Server` + `runP4A -PpackCheck`, final logs `build/p4-check/{server,client}-activation-final.log`: wrapper exits0, host/client PASS. NPC `364608a1-00e9-48e6-92b2-473871811858` остаётся той же entity/owner/relationship23. Источник pack испорчен fixture; bad reload rejected, активная версия сохранилась. Disable показал armor-stand fallback; rollback вернул Firefly/материалы/authoritative poses. Последний результат 148motion steps/74frames/174400bytes/0failures/0chain resets. Client acceptance требует >20 новых accepted frames после каждого enable/rollback, а не только render старого buffer.

Restart `runP4Server/runP4A -PpackCheck -PpackStage=restart`: exits0, тот же UUID/owner/relationship/definition восстановлены из world cache при всё ещё corrupt исходном manifest; 71steps/36frames/84342bytes/0failures. Evidence `build/p4-check/evidence/restart`, logs `*-restart.log`.

Integrated `runP4Integrated -PpackCheck`: после исправления startup reload conflict exit0, host/client PASS, enable/bad reload/disable/rollback и fresh pose frames. WorldId отдельный от dedicated. Логи `integrated-activation-fixed.log`, evidence `evidence/integrated-activation`. Все три PNG enabled/disabled/rollback dedicated просмотрены; integrated rollback PNG просмотрен. Ограничения геометрии/cloth остаются P3 visual limits.

Стендовый первый integrated запуск завис на пересечении CLIENT_STARTED resource reload и Quick Play startup. Render/server diagnostic stack сохранён в `evidence/integrated-startup-conflict`. Остановлен только созданный fixture Java process; synthetic мир сохранён и повторно успешно открыт. Начальный scan перенесён перед первым reload. При review также исправлена возможность старого pose buffer пережить disable→rollback и сохранена monotonic host sequence. Финальный dedicated повтор после исправлений PASS.

## Границы и следующий шаг

Local Firefly pack ~56.96MB, LOCAL_ONLY, копии только в ignored build/private synthetic world cache. Модели/текстуры не входят в Git/JAR. Source asset не изменялся: fixture намеренно портит только копию manifest. Проверена поддерживаемая P3 rig adapter форма; универсальный importer всех glTF/VRM/материалов не заявлен. OS ACL/disk-full и GPU driver failure не имитировались; filesystem commit failure проверен детерминированно. Исторические cache objects пока хранятся до admin cleanup при остановке.

P4.2 завершена. Пользовательские команды/выбор/согласованная передача/двухклиентский policy сценарий — следующая P4.3; она до этой приёмки не начиналась. Полная P4 ещё не завершена.
