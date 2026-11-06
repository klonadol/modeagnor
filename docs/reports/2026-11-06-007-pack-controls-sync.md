# Отчёт007: P4.3 UI, выбор и opt-in sync

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P4.3 Verified
- Ветка main; HEAD до/после `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`; без commit/push
- Решение: [ADR-0024](../decisions/ADR-0024-content-controls-and-opt-in-transfer.md)

## Требование и исходное состояние

P4.3 начата после отдельной приёмки P4.2 (отчёт006). Требовались local dirs, UI выбора/настройки, version mismatch/reconnect и воспроизводимая политика отказа/получения разрешённых assets с лимитами. В P4.2 существовали API активации и client binding, но не management commands или transfer.

## Изменения

`network/{PackCommands,PackWire,PackProtocol,PackSync}.java`: clickable bounded chat panel и command suggestions, admin checks, owner/nearby character selection, public catalog и адресный pull transfer, persistent exact-digest admin opt-in. `content/{PackTransfer,PackDownload}.java`: deny-by-default права/лимиты, bounded archive и consent-scoped assembler. `client/{PackClient,ContentClient}.java`: explicit fetch/cancel, status panel, double validation/atomic received cache, safe local precedence и resource reload. Lifecycle подключён в `Agnor`/`AgnorClient`.

`PackLoader` в финальном review закрыл bypass decoded texture budget через embedded GLB images: schema v1 требует declared PNG и required-extension allowlist. `PackLoaderTest` дополнен этими случаями; final runner проверил реальные prepared packs: Firefly24assets/23render resources/MOTION, public2assets/1render resource/STATIC. Прежние источники моделей не менялись.

`PackTransferTest`: 5 сценариев — отсутствие opt-in/LOCAL_ONLY/UNKNOWN, отдельный transfer size limit, multi-chunk roundtrip с повторным loader, unsolicited/replay/reorder/mixed/corrupt input, wire enums/lengths/version/catalog bounds. Protocol request wrong version возвращает bounded VERSION_MISMATCH; malformed structural wire отвергается codec.

`src/p4Check/java/dev/agnor/check/{P4SyncHost,P4SyncClient}.java`, helper `prepare-p4-check.py --sync`, README: actual player command/network/renderer/provider acceptance. PUBLIC CC0 triangle+notes — оригинальный transfer fixture, не замена целевой anime-модели. Private synthetic evidence и local-only Firefly остаются под ignored build.

## Результаты

Final dedicated2clients: `build/p4-check/{server,clientA,clientB}-sync-final.log`, все wrapper exits0. Fresh results и PNG: `build/p4-check/evidence/sync-verified`.

| Сценарий | Итог |
|---|---|
| B local manifest имеет ту же строку версии, иной digest | Placeholder; gameplay entity сохранена |
| Wrong protocol version, FETCH Firefly, public FETCH до opt-in | VERSION_MISMATCH/DENIED, payload0 |
| B без operator пытается disable Firefly | Command permission denied; active generation не меняется |
| Admin разрешает exact public digest; A явно fetches | 63040bytes/3chunks, archive/per-file hashes/rights повторно проверены, cache atomic, reload и оригинальная GLB видимы |
| B не соглашается получать public | receivedBytes0/completed0; placeholder |
| A select→character public:companion | Тот же UUID, внешний STATIC appearance и personality |
| Actual P2 request→Ollama→History/private UI | A ACCEPTED+FINAL (2private packets), B0; ответ представился Луменом и упомянул звёзды из pack prompt |
| B disconnect/reconnect | Catalog обновлён, download не запущен, missing asset fallback |
| B вручную исправляет local Firefly manifest→reload | Exact digest match, модель/texture/authoritative frames восстановлены, server payload Firefly0 |
| A выбирает Firefly обратно | Та же NPC/owner/relationship23 и прежняя History |

Host accounting final: Firefly0bytes; public63040bytes. Диалоговый текст проходил прежнюю P2 валидацию и не менял server policy. Transfer policy/выбор/персональные сообщения использовали реальные C2S/commands/S2C, не только вызовы repository.

Integrated `runP4Integrated -PpackCheck -PpackStage=syncIntegrated`: wrapper exit0, A/host PASS; local shared packs поэтому download0bytes, соответствующие UI/rights/deny/character/prompt/real dialogue/recover проходят. NPC UUID `d33323c9-309b-4092-8fc8-28e9bb83e326`, отдельный WorldId `c68aa6eb-12e7-40ab-9941-ec80cfcf6703`. Просмотрены PNG public STATIC и recovered Firefly. Dedicated public PNG после overlay fix, B local mismatch и B recovery также просмотрены.

Финальный обычный `scripts/dev.ps1 build`: PASS14s,105tests total/102PASS/3existing opt-in skipped/0failures/errors. Final local pack runner после последнего loader hardening PASS; повторять неизменённый gameplay только ради отказа embedded textures не требовалось, новые отрицательные fixtures включены в обычную suite.

## Ошибки и исправления

Первый sync runtime выявил Brigadier `StringArgumentType.word()` для namespaced definition — trailing data на `:`. Заменён на Minecraft IdentifierArgumentType; fixture ждёт asynchronous select перед change. Failed launch остановлен только для созданных synthetic Java processes; logs/partial downloaded fixture сохранены в `evidence/sync-command-parser-failure`.

Следующий gameplay/protocol run PASS, но A-download PNG застал Mojang reload overlay. Это не засчитано visual proof: evidence `sync-overlay-capture` сохранено, fixture усилен `getOverlay()==null` и actual model-cache readiness. Final dedicated повтор PASS с реальным triangle PNG, а не loading screen. Production renderer не обходился.

Финальная сверка также исправила несовместимость schema ID с точкой и MotionRig resource prefix; существующий MotionRigTest расширен assert для `example.pack`, file/byte prefix validators унифицированы. Повторная ordinary build/suite и JAR audit прошли, число tests осталось105.

Финальный audit credential-prefix сначала сработал на неизменённые baseline `HostSecrets` redaction prefix и fake token unit fixture. Сравнение с HEAD подтвердило отсутствие изменений этих файлов; полная OpenRouter-key pattern не найдена. JAR200entries, без geometry/textures/DB/check fixtures, nested только SQLite; main без client/GPU imports. `git diff --check` PASS.

## Границы

UI — clickable command/chat panel, не графический rig/editor. Model pipeline — проверенный P3 adapter; full cloth/MToon/all extensions/all shaderpacks не заявлены. Transfer≤16MiB; более крупные/ограниченные модели устанавливаются локально по правам. Правовой аудит конкретного публичного пакета остаётся ответственностью администратора, manifest assertion не доказывает правообладание. Arbitrary filesystem ACL/disk-full/native GPU driver recovery, raw malformed-packet gameplay fuzz и public online-auth release не проверены; P9 claims не делаются. CPU/GPU performance budgets P3 этим report не перемеряются и не выдаются за новый benchmark.

P4.3 завершена; общая приёмка P4 — [отчёт008](2026-11-06-008-phase4-acceptance.md). P5 не начиналась.
