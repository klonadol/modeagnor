# Отчёт003: P3.5 — pose sync и dialogue presentation

- Дата:2026-11-06
- Статус: P3.5 Verified в указанных границах; итоговый бюджетный gate PASS — отчёт004
- Ветка:main; HEAD85c5f850a0d23ef9942a3c1cfef880f0d01431fd без commit/push
- Предыдущая задача:P3.4 принята в controlled границах, отчёт002/ADR-0020

## Текущий срез

Созданы `network/PoseWire.java`, `PoseProtocol.java`, `motion/PoseBuffer.java`, `MotionRig.java`, `DialogueMotion.java`, `MotionService.java`. Подключены server lifecycle, существующие NPC UUID, dialogue accepted/final/cancel/failure/disconnect, client binding/interpolation/Gem renderer. Actor/Storage ownership не менялся. Только явный dev rig property включает motion; unconfigured production сохраняет P3.2 fallback. Static neutral preset вычисляется один раз при load, клиент не исполняет gameplay motion loop.

PoseWire v1 — S2C only,16KiB max,512sparse joints/128morphs, world/NPC/entity/dimension, sequence/tick, source rig SHA, paired double root transform/yaw и public semantic state. Sparse transforms абсолютные local TR относительно local rest defaults; каждый packet — самостоятельный keyframe. Quaternion4short нормализуется, morphuint8. Animated scale явно не поддерживается, берётся из local rig.

PoseBuffer хранит до4frames,100ms presentation delay, shortest quaternion/yaw interpolation и общий clock для root/pose/morph. Старые sequence/tick, wrong identity/rig/slot/morph layout отклоняются.750ms stale → empty sample/neutral fallback; reconnect вызывает clear. Кэш не вычисляет host motion.

MotionRig — bounded local dev metadata adapter из уже принятого `capabilities.json`, до4MiB, hierarchy/skin/partition/palette validation; не загружает geometry/textures и не распространяет restricted assets. Явный opt-in JVM property `agnor.motionAsset`, prefix `models/face` по умолчанию. P4 discovery/install/ContentPackSchema не реализуются здесь. Создаёт принятую gait/secondary/face конфигурацию, palette mapping; реальный Firefly metadata файл принят в dedicated и integrated runtime.

## Проверки и evidence

Полная ordinary suite `p35-final-build.log`: BUILD SUCCESSFUL27s,86tests,83PASS/3opt-in skipped,0errors/failures.16новых P3.5 tests: wire5,buffer5,rig3,dialogue3. Проверены bounds/version/truncation/quantization, stale/replay/rig invalidation и reconnect, semantic retry/expiry/concurrent-player scope. Архивы ниже находятся в ignored `build/p35-check/evidence/`, логи — `build/p3-research/`.

| Сценарий | Результат / файлы |
|---|---|
| Dedicated smoke | PASS,130keyframes/309346payload bytes,535render calls, rootTravel.8m, blink.592; `p35-smoke-{server,client}.log`, `evidence/smoke` |
| Dedicated +2clients | PASS,497shared authoritative frames, все4states; `p35-multi-server-second.log`, `p35-multi-client-{A,B}-second.log`, `evidence/multi-verified` |
| Private dialogue/retry | Реальный Ollama final: A3private responses(accepted/final/replay),B0. Retry не повторяет UI/gesture |
| Reconnect/mismatch/stale | B same-process reconnect: cache reset,41wrong-rig fallback,10stale rejects, восстановление правильным keyframe |
| Integrated | PASS1m23s,333authoritative frames,4states/real final/retry; `p35-integrated.log`, `evidence/integrated-verified`. Использовать только server/A: исторические B outputs не участвовали |
| Sodium | PASS:0.6.13+mc1.21.1,Iris отсутствует; `p35-compat-sodium-{server,client}.log`, `evidence/compat-sodium` |
| Iris без pack | PASS:1.8.8+mc1.21.1,Sodium0.6.13,shaderActive=false; `p35-compat-iris-off-{server,client}.log`, `evidence/compat-iris-off` |
| Iris с pack | PASS: те же версии,Complementary Reimagined r5.5.1,shaderActive=true;254authoritative frames,4262renders. `p35-compat-iris-on-{server,client}-final.log`, `evidence/compat-iris-on-verified` |

Baseline: MC1.21.1,Loader0.16.10,API0.116.15,GemRender0.1.20,Flywheel1.0.6. GPU GTX1080,1280×720. Последовательно просмотрены реальные PNG: smoke, multi A-FIVE/A-dialogue/B-fallback, Sodium/Iris-off A-FIVE, активный shader A-FIVE. Модель видна с материалами и целым скелетом; shader PASS не основан только на счётчиках. Полная MToon/outline fidelity не заявляется.

Primary pinned artifacts: [Sodium](https://modrinth.com/mod/sodium/version/u1OEbNKx), [Iris](https://modrinth.com/mod/iris/version/1.8.8%2B1.21.1-fabric), [Complementary](https://modrinth.com/shader/complementary-reimagined). SHA512 — в `scripts/prepare-p35-compat.ps1` и ignored manifest. Это выбранная точная матрица, а не гарантия любого shaderpack/версии.

## Диагностические замеры

Первый multi, два simultaneous clients,~7s measurement после100ticks warm-up:

| NPC | Host motion CPU mean/p95 ms | Payload KiB/s/viewer |
|---|---|---|
|0|.027/.043|0|
|1|1.520/3.014|23.36|
|5|5.269/8.693|116.76|

Client A whole-world GPU mean/p95:0NPC.253/.250ms,1NPC1.504/3.313ms,5NPC.885/1.850ms. Немонотонность отражает короткие окна/прогрев/два contexts; это не isolated skinning cost. Submission~.25–.36ms/call. Whole-NPC failures0;14chain-local fallbacks/2108steps.

Active shader: host mean/p95ms0=.020/.028,1=1.080/1.229,5=4.928/8.935. Whole-world GPU mean/p95ms0=2.851/2.983,1=3.522/3.730,5=3.865/4.090. Payload23.35/116.76KiB/s/viewer. Whole-NPC failures0,13chain-local fallbacks/1506steps. Это отдельный диагностический single-client run. Предварительно заданные пределы и новый контрольный multi run — [отчёт004](2026-11-06-004-phase3-acceptance.md); ретроспективный performance PASS для этих цифр не заявляется.

## Исправленные неудачные попытки

- Fixture SERVER_STARTED order: check до MotionService; перенесён на первый tick (`p35-multi-server-event-order-failed.log`).
- Windows AccessDenied atomic replace state.json: отложенный retry на следующем fixture tick, без blocking sleep. Production motion tick не делает file IO. Архив `evidence/multi-coord-failed` сохранён; последующий multi PASS.
- Active shader missing PreprocessorListener: Loom dev raw Iris file не включил nested dependencies. Helper извлекает три pinned libraries (ANTLR,glsl-transformer,jcpp); два nested Fabric API компонента не дублируются. `evidence/compat-iris-on-missing-deps` сохранён.
- Затем NoSuchMethodError ExtendedShader.method_60897: named GemRender был закэширован без Iris hierarchy. Iris переведён в opt-in modClientImplementation; только неверный generated cache JAR сохранён/удалён, raw upstream JAR неизменён. Helper prewarm до server запуска; javap подтверждает initializeUniforms. `evidence/compat-iris-on-remap-failed`, `p35-iris-remap-fix.log`, `p35-iris-aware-prepare.log`. Финальный native shader run PASS, fallback не подменяет compatibility.

## Границы и аудит

MotionRig — local dev adapter, не P4 loader. Без opt-in rig сохраняется static P3.2 adapter; при отсутствии backend/resources — same-entity vanilla fallback. Public state не содержит private text/credentials/player token. Mouth.15 — semantic cue, не phoneme/audio lipsync(P8); stationary gaze/turn не navigation(P7). Secondary10chains/91segments/4core spheres: chain-local conflicts сбрасываются, thin cloth folds/overlaps и несмоделированные strips остаются; [полные ограничения](../assets/FIREFLY_VISUAL_LIMITS.md).

JAR audit `p35-jar-audit.json`:63motion classes,0restricted models/textures,0Gem/Flywheel/Iris/ANTLR/fixture classes; nested только sqlite-jdbc. Main motion/network не импортируют client/GPU packages. `git diff --check` PASS. Assets остаются вне Git/JAR. [ADR-0021](../decisions/ADR-0021-pose-keyframes-and-dialogue-presentation.md); воспроизводимые команды — `src/p35Check/README.md`.
