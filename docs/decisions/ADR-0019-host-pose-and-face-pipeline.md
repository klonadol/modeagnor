# ADR-0019: Host pose, task arbitration и независимые facial channels

- Дата: 2026-11-06
- Статус: Accepted для P3.3; gameplay/pose transport integration остаётся P3.5
- Связь: ADR-0007/0017/0018, [отчёт P3.3](../reports/2026-11-06-001-pose-face-pipeline.md)

## Решение

`dev.agnor.motion` содержит renderer-independent CPU pipeline. JOML используется только для математики; common classes не импортируют Minecraft client, GemRender или LWJGL.

- `RigDescriptor` — immutable names/parent/rest TRS + caller-supplied rig revision; проверяет bounds, unique names и циклы, составляет порядок FK. Все targets/контакты/длины решаются в одном model-space, не телепортируют gameplay entity.
- `JointTransform` — конечный immutable TRS, quaternion XYZW, положительные scale. Преобразование матрицы с shear/reflection отклоняется явно.
- `AnimationClip` — immutable STEP/LINEAR tracks, clamp/loop clock; непокрытые joints берутся из rest. Quaternion interpolation — shortest-path slerp. CUBICSPLINE/general external clip importer в текущем срезе не заявлены.
- `PoseMixer` — ordered override/crossfade или additive относительно явной reference pose; маски — проверенные rig slots. LLM не передаёт bone arrays.
- `TwoBoneIk`/`IkPoseSolver` — аналитическое решение одной direct двухсегментной цепи, явный pole, ограничения сгиба, сохранение длин, finite fallback при вырождении. Uniform chain scale обязателен; unsupported binding даёт диагностируемый отказ. Плечевой twist solver и глобальная оптимизация тела не заявлены.
- `WholeBodyController` — до 32 host semantic tasks на экземпляр, priority/UUID tie-break, expiry и idempotent cancel. Hierarchy-aware claims предотвращают конфликт вложенных цепей. LOOK ограничен cone, REACH/FOOT используют IK; стопа на плоском контакте сохраняет world/model orientation базовой позы. Outcomes APPLIED/CONSTRAINED/PREEMPTED/EXPIRED/UNSUPPORTED/FAILED, failure откатывает текущую задачу. Error измеряется в radians для LOOK и model units для limbs. Pelvis/weight/gait/terrain collisions — P3.4/P7, не побочный эффект текущего solver.
- `FaceController` разделяет expression, blink L/R, mouth и gaze. Expression eyelid contributions подавляются во время blink, чтобы не складывать два закрытия век; brows/mouth corners сохраняются. Mouth-open не стирает emotion. Gaze ограничен eye cone и изменяет bone pose, missing channels сообщаются явно. Exponential smoothing получает явный dt; автоматическое аудио/случайное движение рта отсутствует.

## Asset boundary и проверка

Один исходный rig может быть разбит на несколько GemRender palettes. Host вычисляет оригинальную hierarchy, затем mapping `sourceJointIndices` и inverse bind формирует palette каждой части; клиент делает skinning/morph rendering готового состояния. `partition-local-model.py` теперь сохраняет morph attributes/names и compact buffers, извлекает textures в локальные ресурсы. Это bounded dev preparation, не Content Pack loader P4.

Первый body proof использовал Furina; facial proof — VRM Firefly с70morphs. Для проверки общей модели повторены body/IK/arbitration на Firefly через source humanoid mapping. Core не содержит model-specific names/indices: они остаются в opt-in test adapter. Expression profile подобран по реальным source keys и Blender previews, а не только по именам. Морфы blink используют VRM `blink_l`/`blink_r` (13/14), не неработающие `vrc.blink_*` (0/1).

## Граница доказательств

Dedicated fixture создаёт immutable pose/morph frames, client fixture лишь читает и рисует. Файловый fixture transport атомарен и проверяет stage/asset revision/partition count; stale файл после server failure не принимается. Это не multiplayer PoseSnapshot protocol и не замена его sequence/tick/rig hash/reconnect требований P3.5. Обычная NPC пока использует P3.2 static adapter; реальные dialogue/body/face consumers подключаются в P3.5 после P3.4.

Restricted models/подготовленные sidecars/Blender QA остаются в ignored local assets, не в JAR или Git. Полная P3 не завершается по этим controlled сценам; нужны gait/secondary physics, runtime sync/dialogue и измерения.
