# ADR-0024: Управление Content Packs и согласованная передача

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Accepted / Implemented / Verified P4.3
- Уточняет ADR-0003/0022/0023; evidence — отчёт007 и итог008.

Server control panel реализован в существующем command/chat UI: `/agnor packs` (admin level2), enable/reload/disable/rollback, share; `/agnor characters` и owner/admin nearby `/agnor character <namespace:id>` для выбранной существующей NPC. Чат содержит clickable controls, suggestions и bounded первые32 definitions. Полноценный редактор assets/rig не вводится. Filename не является произвольным server path: только pack ID либо ID.zip под DataRoots.packs.

Client `/agnorpack` показывает MATCH/MISSING/MISMATCH, digest/version и local-only/download controls. Join/reconnect синхронизирует только public bounded catalog (≤32): pack ID/version/inventory digest и флаг передачи. Отсутствующий/несовпадающий ресурс оставляет gameplay NPC и private dialogue работающими с placeholder. Наличие local copy не даёт права получить её с сервера.

Передача требует ALLOWED provenance/license, явного admin opt-in **точного inventory digest**, client command/click и соблюдения отдельного более строгого бюджета. LOCAL_ONLY/UNKNOWN запрещены даже при подложенном opt-in. Policy хранится отдельно в `<save>/agnor/content-share.properties`, ≤32 pinned entries. Смена версии сама разрешение не наследует; revoke/generation change отменяет текущие sessions. Информация catalog не содержит prompts, paths, keys или History. Разрешённый полный declarative pack после явного fetch содержит только заявленные pack data/assets, не host presets/config/storage.

PackWire v1 (отдельно от schema/API manifest): LIST/FETCH/NEXT/CANCEL, UUID token, exact ID/digest/offset. C2S≤256bytes, S2C≤31KiB, chunk≤28KiB, STORED ZIP≤16MiB. Нет URL/file upload/server path. Один transfer/connection, ≤4/server, worker1/queue4, request cooldown10ticks, ≥2server ticks/chunk, expiry600ticks; bounded client retries3. World/connection identity берётся из trusted server callback. Queue reject/disconnect/stale/policy revoke имеют конечный результат. Hash/ZIP/filesystem работы выполняются вне tick/network callback; tick обрабатывает bounded chunk и state.

Client создаёт consent-scoped PackDownload только после fetch. Unsolicited/replay/reorder/mixed token/id/digest/total/hash/version отвергаются. Archive SHA-256 проверяется installation worker, затем повторно loader/hash/rights/dependencies/capabilities. Сохранение в `mods/agnor/cache/received-packs/<id>.zip` атомарно; local `packs/<id>`/`<id>.zip` никогда не перезаписывается загрузкой. Cache snapshot не является gameplay authority. Client session epoch проверяется перед установкой/публикацией; отмена во время reload приводит к восстановительному resource reload. Уже полностью принятый валидный архив может кратковременно оставаться пассивным cache при disconnect до UI callback; он не позволяет обойти exact host digest.

Начальный local/received scan выполняется перед первым resource reload; последующие reload — worker→resource reload→publish. Recovery failure оставляет neutral/placeholder, не GPU/native exception в server runtime. Server snapshot current/previous остаётся независимым от исходного каталога.

Финальное ужесточение adapter schema v1: только declared external PNG textures (embedded images не обходят decoded pixel budget), required extension allowlist `KHR_materials_unlit`; неизвестный required extension отвергается до importer. Универсальная поддержка glTF/VRM/всех shaders не заявляется.

Проверки включают два реальных клиента: deny версии/прав/отсутствия opt-in, A consented download/B opt-out, B mismatched local copy/reconnect/manual recovery, сохранённый UUID и приватный реальный LLM dialogue с pack personality. Controlled public CC0 fixture отделён от restricted Firefly local pack; последнему отправлено0payload bytes. Integrated использует общие local assets без искусственного сетевого копирования.
