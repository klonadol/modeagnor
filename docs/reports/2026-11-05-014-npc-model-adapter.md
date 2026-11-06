# Отчёт 014: P3.2 — реальная модель на сохраняемой NPC

- Дата: 2026-11-05
- Статус: Done / P3.2 Verified в перечисленных границах
- Ветка/HEAD: main / 85c5f850a0d23ef9942a3c1cfef880f0d01431fd, рабочее дерево без commit/push
- Этап: P3.2; ADR-0018, предшествующий proof — отчёт 013

## 1. Цель и исходное состояние

Подключить готовую rigged anime-модель к существующей NPC, сохранив gameplay identity/persistence, fallback и server/client границы. P3.1 ранее проверила API и upgraded isolated matrix; production ещё использовал старый toolchain и armor stand placeholder.

## 2. Изменения

Wrapper обновлён до Gradle9.7.1, Loom1.16.3, FabricAPI0.116.15+1.21.1. Minecraft/Yarn/Java21/Loader0.16.10 сохранены. GemRender0.1.20/Flywheel1.0.6 — client compile-only; runtime подключается локальным fixture флагом либо установкой optional JAR пользователем. Hash GemRender проверяется build script, `prepare-renderer.ps1` загружает закреплённый artifact и извлекает его Flywheel. Ничего из сторонних JAR не bundled в AGNOR.

`NpcEntity` регистрирует `agnor:npc`, основанный на ArmorStandEntity. NpcRuntime создаёт его с тем же UUID из Storage, позиция/owner/lifecycle не дублируются, `shouldSave=false`. `NpcPresentation` регистрирует GemRender adapter при наличии мода, иначе vanilla fallback. Неполная/неудачная загрузка ресурса также оставляет fallback того же entity. Client renderer привязывает семь моделей к текущему entity transform.

## 3. Модель, адаптация и capability

Из коллекции пользователя выбран `genshin_impact_-_furina.glb`, SHA256 `8065c134cc6d6e8e03a0a073a3953f388e8ae6857b7a5d967e4aa7226a8a1f45`. Права/автор/исходная страница не предоставлены; TEMPORARY_RESTRICTED в реестре. Исходник и производные только в ignored local assets и fixture resource pack; Git/JAR их не содержат.

Read-only inventory `inspect-models.py`: исходник252 nodes,241 skin joints; все модели коллекции превышают лимит GemRender256palette slots. Он суммирует nodes и skin slots. Скелет/веса не упрощались: `partition-local-model.py` разбивает индексированные triangles по используемым joints (до110/часть), копирует исходные vertex attributes/weights/IBMs/materials, remap-ит joint indices и сохраняет global bind transforms. Результат7частей, максимум221slots. Исходная hierarchy и source joint indices сохранены в локальном `capabilities.json` для будущего host mapping. Модель около1.7блока, исходные axes/units корректны, дополнительный scale не понадобился.

Части имеют flattened bind nodes: дальнейший solver должен вычислять исходную hierarchy и применять per-part mapping, а не считать их независимыми персонажами. Это конкретный dev adapter, не универсальный внешний Content Pack loader. Формат дополнительных моделей пока не заявлен. В выбранном исходнике нет morph targets; текущая capability — static bind pose и skeletal skinning, без clips/face/physics. P3.3 должна выбрать/адаптировать facial-capable model прежде полной проверки лица. Нельзя объявлять лицо готовым по этому кадру.

## 4. Ошибки и исправления

- После splitEnvironment source sets client dependency configurations создаются только после вызова Loom: dependencies перенесены ниже блока Loom.
- EntityType generic inference исправлен явным `<NpcEntity>`.
- Первый игровой кадр был без текстур: GemRender требует `images.extras.resourceLocation`. Встроенные изображения извлечены без перекодирования и связаны с локальными resource identifiers.
- Следующий кадр был красным: нулевой overlay UV — damage overlay. Заменён на `OverlayTexture.DEFAULT_UV`, после чего изображение имеет исходные синие/белые цвета.
- Финальная проверка preparation script выявила чтение кириллицы как ANSI в Windows PowerShell5.1. Default path теперь собирается из Unicode codepoints; повторный полный prepare PASS. Системная encoding/policy не менялась.
- `No data fixer registered for agnor:npc` — диагностический warning при регистрации нового entity type; entity не сохраняется в NBT, данные восстанавливаются Storage. Startup/restart завершились штатно. Новую NBT migration не заявляем.
- Upstream optional integration mixin warnings, Gradle deprecations и старый sqlite version-semver warning сохранены в логах; native crash в этих запусках не наблюдался.

## 5. Проверки и evidence

| Проверка | Результат / граница |
|---|---|
| Production toolchain upgrade `build` | PASS, полная recompilation и ordinary tests |
| `build p32CheckClasses -PnpcRenderCheck` | PASS, 41tests:0failures/0errors/3opt-in skipped |
| Dedicated server, настоящие AGNOR lifecycle/SQL | PASS, нет client/GPU classloading errors |
| Создание NPC → реальный игровой client | PASS,7 частей/GPU skinning/materials/orientation/scale |
| Resource reload | PASS,7моделей disposed/reloaded, кадры до/после |
| Dedicated restart | PASS: CREATED и RESTORED `f68f26b3-c9a3-455b-8ee6-689677df5171`; нового UUID/дубликата нет |
| `runP32Server` и `runP32Client` с `-PnpcFallback` | PASS без GemRender/Flywheel на runtime classpath; NPC отображается vanilla armor stand, screenshot просмотрен, оба процесса штатно завершились |
| Финальная обычная `scripts/dev.ps1 build` | PASS (11s), tests выполнены; production JAR не содержит GemRender/Flywheel/restricted parts/textures/harness; main source audit без client/GPU imports; `git diff --check` PASS |

Логи: `build/p3-research/production-upgrade.log`, `p32-build.log`, `p32-server[2,3].log`, `p32-client[2,3].log`. Неудачные untextured/overlay кадры сохранены отдельно в `build/p32-check`. Исправленный кадр — `npc-textured.png`, повторная загрузка — `npc-textured-reload.png`; оба просмотрены визуально. Модель стоит в rest pose, части совпадают, ступни на площадке; body animation/face/physics по этому кадру не приняты.

Fixture `src/p32Check` работает только по opt-in флагу, использует отдельный synthetic world и loopback25582. `prepare-p32-check.ps1` воспроизводимо готовит локальные ресурсы, проверяя SHA выбранной модели; команды в `src/p32Check/README.md`.

## 6. Пределы и следующий этап

Нет заявления о performance PASS (замеров CPU/GPU ещё нет), полной shader/Sodium/Iris совместимости, shared pose protocol или finished P3. Missing renderer/resources оставляют fallback; модель не синхронизируется другим игрокам. Restricted models не включены в публичные artifacts. Online authentication/provider повторно не тестировались: изменение их поведения не вводилось.

Следующая задача после приёмки P3.2 — последовательные части P3.3: clips/blending, FK/IK, arbitration, eye/face. Нельзя перескочить на gait/physics P3.4 по статичной модели.
