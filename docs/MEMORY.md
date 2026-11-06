# Lorebook и управление памятью

Разработка P5 по ROADMAP: сначала записи/CRUD (P5.1), затем единый context consumer (P5.2), затем History/backup/retention (P5.3). Реализованный статус и ограничения фиксируются отчётами, а не этим описанием формата.

## Lorebook

Выберите NPC через `/agnor select <UUID>`. `private` — только ваш NPC↔player scope; `shared` — общие знания NPC, изменяет только owner/admin. Список включает effective pack/local sources,20/page:

```text
/agnor lore list
/agnor lore list 1
/agnor lore add private keyword facts:gate "ворота" Код ворот COBALT-732.
/agnor lore add shared always facts:custom - Это общее знание.
/agnor lore get private facts:gate
/agnor lore delete private facts:gate 1
```

Для ALWAYS trigger `-` игнорируется. EXACT сравнивает всё текущее сообщение после NFKC/case/whitespace normalization. KEYWORD использует границы Unicode letters/digits ("ворота" не совпадает с "воротами"). Кириллицу и многословный trigger заключайте в кавычки. Команды `get/list` отправляют ответ только инициатору. Чужой private UUID нельзя задать аргументом команды.

Изменение полной записи: `/agnor lore put private facts:gate <expectedRevision> <JSON>`. JSON имеет ровно следующие поля:

```json
{"title":"Ворота","content":"Код ворот COBALT-732.","mode":"KEYWORD","triggers":["ворота"],"priority":10,"role":"user","enabled":true,"definition":"*","dimension":"*"}
```

Create revision0, update — текущая revision из get/list. Delete сохраняет tombstone с повышенной revision: повтор stale create не восстанавливает данные; для явного восстановления прочитайте tombstone через get и используйте его revision. Отключение `enabled:false` подавляет нижний источник; удаление local override снова открывает pack/shared запись. JSON не принимает scope, credentials, исполняемый код или права. Role system/user/assistant — metadata записи, не источник server permissions. Лимиты:content4096chars,title128,triggers16×128,priority±1000;256rows/scope,4096/world с tombstones. Обычный Minecraft chat input ограничивает длину ручной команды; длинные публичные знания удобнее задавать pack assets. Значения JSON могут содержать кавычки, их не нужно заключать в дополнительную внешнюю строку после greedy argument.

Pack source — JSON asset, объявленный в обычном Content Pack assets с hash/format=json:

```json
{"type":"agnor:lorebook","schemaVersion":1,"entries":[{"id":"example:gate","title":"Ворота","content":"Код ворот PACK-AMBER.","mode":"KEYWORD","triggers":["ворота"],"priority":5,"role":"user","enabled":true,"definition":"agnor:dev","dimension":"*"}]}
```

Pack→shared local→your private local — порядок overrides по ID. Constraints definition/dimension допускают `*` или точный Identifier. Pack conflicts требуют прежних explicit dependency/override/priority. Source IDs включают pack digest/local revision, local записи не входят в sync пакетов. Disabled override не удаляет оригинальный pack asset. Приватные знания не помещайте в распространяемый Content Pack.
