# ADR-0022: Content Pack v1 и безопасный вход

- Статус: Accepted / Implemented / Verified (P4.1)
- Дата: 2026-11-06, Asia/Vladivostok
- Уточняет ADR-0003; executable Addon API не вводится.

`PackLoader` принимает каталог или ZIP, ничего не распаковывает на диск и создаёт неизменяемый снимок проверенных байтов. Manifest строго JSON UTF-8 без duplicate keys, глубина ≤32, неизвестные поля запрещены. Schema/API=1, точные числовые версии `major.minor.patch`, dependencies по pack ID/точной версии. Override требует прямой зависимости, явного имени пакета и строго большей priority; неявные конфликты и циклы запрещены. Порядок не зависит от filesystem.

По умолчанию ≤1024 записей (включая каталоги), ≤64MiB/file, ≤256MiB total/archive, ≤256KiB manifest, compression ratio ≤200 (исключение ≤1KiB). Пути — lowercase relative ASCII, без traversal/absolute/backslash/Windows reserved names/case collisions. Symlinks и специальные файлы каталога отвергаются; source считается локальным admin-owned каталогом, одновременная враждебная замена его ancestors вне threat model. ZIP links не извлекаются и могут стать только объявленным валидируемым payload.

Все файлы кроме manifest должны быть объявлены ровно один раз; SHA-256 обязателен. Поддерживаются JSON, UTF-8 text, PNG и GLB v2. PNG имеет ограничение dimensions ≤8192; GLB проверяет chunks и JSON, внешние buffers запрещены, image URI ссылается только на объявленный относительный PNG. Это ingress/signature validation, не полная GPU/deformation validation: capability stage P4.2 отвечает за importer/rig/materials и безопасный resource binding. В частности GemRender `extras.resourceLocation` не является доверенным разрешением доступа к resource manager.

Provenance содержит source/author/license/rights ALLOWED|LOCAL_ONLY|UNKNOWN. ALLOWED требует https source и явную лицензию; утверждение автора manifest не заменяет аудит прав человеком. Передача требует также opt-in администратора, согласие клиента и лимиты P4.3. LOCAL_ONLY/UNKNOWN никогда не передаются. Содержимое и prompt пассивны; secrets/provider settings/code не являются полями manifest.

`PackResolver` сначала проверяет всю candidate generation; loader/resolver не мутируют runtime/Registry и не обращаются к LLM. Atomic stage/activate/rollback — P4.2; правила sync — P4.3.

Проверки: `PackLoaderTest`, 8 сценариев: directory/ZIP/custom path, immutable inventory, unsafe paths, schema/strict JSON, checksums/signatures/executable undeclared, limits/compression, rights/references, GLB external resources, dependency/cycle/override.
