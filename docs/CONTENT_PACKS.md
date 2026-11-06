# Content Packs v1

Формат декларативный, не Addon API. См. ADR-0022. Пакет — каталог или ZIP с `manifest.json` в корне. Все поля примера обязательны; пустые model/rig/prompt допустимы. Все payload-файлы объявляются в assets с SHA-256. ID ассета имеет namespace pack ID. Definition ID допускает override при явной зависимости и повышенной priority.

```json
{
  "schemaVersion": 1,
  "apiVersion": 1,
  "id": "example",
  "version": "1.0.0",
  "priority": 0,
  "dependencies": {},
  "overrides": [],
  "provenance": {
    "source": "local:my-original-character",
    "author": "Your name",
    "license": "Private local use",
    "rights": "LOCAL_ONLY"
  },
  "assets": [],
  "definitions": [{
    "id": "example:companion",
    "revision": 1,
    "displayName": "Companion",
    "prompt": "Reply warmly and briefly in the player's language.",
    "model": "",
    "rig": ""
  }]
}
```

Asset: `{"id":"example:body","path":"models/body.glb","format":"glb","sha256":"64 lowercase hex digits"}`. Formats: `glb`, `png`, `json`, `text`; schema v1 не принимает произвольные executable файлы. Для PNG textures GLB `images[].uri` — относительный путь к объявленному PNG в каталоге GLB. Embedded images, внешние buffers, URL/data URI и traversal запрещены; required extensions допускают только `KHR_materials_unlit`. Предварительно экспортируйте embedded textures в объявленные PNG, как в принятом P3 adapter. Это сохраняет единый предел decoded texture budget. GLB geometry/materials должны проходить capability stage перед rendering. Rig JSON использует проверенный P3 metadata adapter, его наличие не является обещанием поддержки каждого рига.

Зависимость: `"dependencies":{"base":"1.0.0"}`, override: `"overrides":["base"]`, `priority` больше base. Конфликт definition без такого override отвергает весь candidate набор. ZIP и каталог с одинаковыми файлами имеют одинаковый inventory digest. Изменение даже manifest меняет digest независимо от заявленной версии.

Limits: 1024 entries, 64MiB/file, 256MiB/pack, 256KiB manifest, ratio 200, PNG ≤8192 на сторону. Paths lowercase ASCII, без symlinks, reserved Windows names и `..`. Не изменяйте исходный каталог во время загрузки. Секреты и provider credentials в packs не помещать.

## Установка и управление

Положите пакет в `<gameDir>/mods/agnor/packs/<id>/manifest.json` либо `<id>.zip`. На dedicated это gameDir сервера, на клиенте — каталог выбранного instance. Built-in JAR не меняется. После установки на server администратор (permission level2) использует:

```text
/agnor packs enable example
/agnor packs
/agnor packs reload example
/agnor packs disable example
/agnor packs rollback
```

Enable/reload принимает ID каталога или `example.zip`; при аргументе `example` сначала ищется каталог/файл с таким именем, затем `example.zip`. Весь candidate набор проходит validation/dependency/rig stage, snapshot commit и только затем owner-thread Registry publication. Ошибка сохраняет активную версию. Повтор той же inventory — no-op, rollback slot не стирается. Rollback переключает current/previous generation. Server перезапускается из `<save>/agnor/content` без повторной зависимости от исходного source.

`/agnor characters` показывает первые32 definitions с clickable `[use]` и capability; все IDs доступны в command suggestions. Выберите существующую NPC через `/agnor select <UUID>`, затем `/agnor character example:companion`. Разрешены owner/admin и nearby scope; чужой NPC не меняется. UUID, position, отношения и History сохраняются. Новый definition revision выбирается явно той же командой. При отключении definition остаётся сохраняемый NPC с placeholder; повторная активация восстанавливает presentation. Смена контента отменяет текущие dialogue jobs.

Prompt уже используется Main Dialogue как bounded style/personality внутри host policy (≤2048 UTF-8 bytes). Voice/behavior JSON/text assets пассивны до P6/P7/P8. Для motion: `model` указывает `models/<name>/part-000.glb`, `rig` — P3 metadata JSON; все `part-NNN.glb` и PNG должны быть объявлены. Произвольный humanoid rig/VRM не получает автоматически все P3 возможности. Capability report различает MOTION, STATIC и PLACEHOLDER; missing body/face channels не блокируют независимые ресурсы.

Клиентский `/agnorpack` — чат-панель с состоянием MATCH/MISSING/MISMATCH, текущей версией/digest и явной кнопкой download. `/agnorpack reload` повторно читает локальные файлы и выполняет resource reload. Во время reload отображается fallback; GPU backend отсутствует — используется прежний same-entity fallback. Resource handles имеют стабильные пути; GemRender reload освобождает старые GPU resources. Повреждённый local source при reload не заменяет текущую generation; после перезапуска client может потребоваться исправить локальный источник (server last-known-good сохраняется отдельно).

## Политика multiplayer

Join и reconnect передают только public catalog: ID/version/inventory digest и флаг доступности. Pack prompts/private History/provider credentials не входят в catalog. Отображение требует точного inventory digest, а не совпадения строки версии; при mismatch NPC работает с placeholder. Клиент не обязан получать ресурсы.

Передача запрещена по умолчанию. Для ALLOWED-пакета с явной provenance/license администратор разрешает конкретный digest:

```text
/agnor packs share example true
```

Opt-in сохраняется в `<save>/agnor/content-share.properties`; после изменения inventory требуется новое разрешение. `... false` отзывает разрешение и отменяет transfers. LOCAL_ONLY/UNKNOWN не могут быть разрешены этой командой и не передаются даже при ручной записи opt-in. Для них — своя разрешённая локальная копия и `/agnorpack reload`.

Клиент сам запускает `/agnorpack fetch example` или нажимает `[download]`; catalog/автозагрузка NPC никогда не означают согласие. Лимиты: ≤16MiB STORED ZIP, chunks≤28KiB, ≤4 transfers/server, один/connection, worker1/queue4, ≥2server ticks/chunk, expiry600ticks. Нет URL download, произвольных remote paths или server-provided executable code. NEXT привязан к token/id/digest/offset; version/stale/replay/mixed archive отвергаются. Disconnect/cancel/content generation или policy change инвалидируют transfer.

Полученное содержимое проверяется повторно: archive SHA-256, manifest, per-file hashes, provenance ALLOWED, exact requested ID/version/digest, dependencies и capabilities. Только потом атомарно сохраняется `<client gameDir>/mods/agnor/cache/received-packs/<id>.zip`, выполняется normal resource reload. Пользовательский `packs/<id>` не перезаписывается; при его наличии нужно обновить локальную копию вручную. `/agnorpack cancel` отменяет текущий запрос. Разные игроки принимают решение независимо.

Ingress дополнительно ограничивает GLB JSON4MiB, collections4096, accessor count2million/total16million, checked byte spans/stride, hierarchy depth128/no cycles; PNG total decoded≤128MiB RGBA/pack. Generation≤32packs/512MiB payload. Это проверенный adapter pipeline, не обещание совместимости любого glTF extension/shader. Исторические server cache objects можно очищать администратору при остановленном server, сохранив current/previous pointers и referenced objects.
