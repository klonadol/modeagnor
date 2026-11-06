# ADR-0021: Bounded pose keyframes и public dialogue presentation

- Дата:2026-11-06
- Статус: Accepted / P3 Verified; evidence в [отчёте003](../reports/2026-11-06-003-pose-sync-dialogue.md)
- Предпосылки: ADR-0019/0020, завершённая P3.4 controlled

## Producer и локальный rig

`MotionService` — session-owned server producer для тех же `NpcRuntime.activeEntities()`/UUID. Storage остаётся единственным durable owner. Pose/physics/semantic state не создают второй NBT writer/DB identity. Один bounded local dev metadata file (`agnor.motionAsset`, <=4MiB) описывает уже адаптированный rig/skin/parts; geometry/textures не загружаются и не синхронизируются этим API. Это временный явный adapter до P4, а не Content Pack loader. Без property сохраняется P3.2 rendering fallback.

Host считает gait→head/validated gesture→face/eyes→secondary. Максимум16active motion NPC/session; без tracking viewers state засыпает и возобновляется через reset. Float simulation origin ребазируется в пределах32m, world root остаётся double. Teleport/large yaw reset сбрасывает motion; sequence продолжается в той же entity incarnation. Обычная перемена root position принадлежит NPC/gameplay producers. Валидированный dialogue intent может плавно поворачивать **стоящую** NPC, максимум4degrees/tick; движущимся root владеет его producer. Это не navigation/follow P7.

## Public semantic adapter

После committed accepted/claim `DialogueService` создаёт ephemeral presentation token `(trusted player UUID, request UUID)` в NPC/world-scoped controller. Первые6ticks — LISTENING, затем THINKING; finalized reply — SPEAKING на40ticks, фиксированный NOD/SMILE. Повтор finalized/retry не перезапускает gesture или duration. Cancellation/failure/disconnect/stale scope удаляет token. Job expiry1200ticks, <=16active tokens и128completed dedupe keys/controller; availability проверяется каждый tick. Shared avatar выбирает один focus детерминированно: speaking>thinking>listening, start tick, token UUID. Private conversation остаётся scoped каждому игроку.

Allowlist содержит NONE/NOD и NEUTRAL/SMILE, bounded gain. Нет LLM bone arrays, affect engine или произвольных клиентских жестов. Blink host-owned, gaze ограничен head/eyes cones. Mouth opening.15 — постоянный semantic cue во время text speaking; phoneme/audio lipsync не заявляется до P8. Ни текст, ни player/request token, ни credentials/preset не входят в pose wire.

## Wire и presentation

`PoseWire` v1: только S2C, max16KiB, <=512sorted unique joint overrides/128morphs. Поля world/NPC UUID, entity ID/dimension, sequence, server tick, source SHA256 rig/content revision, paired root doubleXYZ/yaw, public semantic state. Local TR абсолютны относительно rest defaults; scale берётся из local rig, animated scale отклоняется. Quaternion4short нормализуется, morphuint8. Каждый packet — самостоятельный sparse keyframe, не delta относительно предыдущего; публикация10Hz и только tracking receivers с channel support. Payload bytes измеряются отдельно от Minecraft framing.

Client binder требует реальную `NpcEntity` с совпадающими entity ID/UUID/dimension и локальный rig hash/channel layout. `PoseBuffer`:4snapshots,100ms arrival-based interpolation delay, один clock для root/joints/morphs, shortest quaternion/yaw. Sequence/tick/identity проверяются до content revision; stale не может инвалидировать новую presentation. Unknown rig инвалидирует drawable frames, сохраняя anti-replay guard; следующий правильный keyframe восстанавливает состояние.750ms stale → static neutral. Teleport>2m/large yaw snap; reconnect/world/entity-incarnation change очищает buffer и guard.

Client не исполняет gameplay gait/physics: только pose interpolation, FK palette mapping, skinning/morph. Static neutral preset готовится один раз при load для fallback. Renderer корректирует vanilla entity interpolation paired snapshot root, чтобы root и тело имели один presentation clock. При missing resources/backend/schema — fallback той же entity; restricted models не входят в JAR/packet.

## Verification boundary

Pure tests покрывают malformed/truncated/oversize/version, quaternion/morph quantization, sparse-loss recovery, stale/identity/rig mismatch, reconnect/teleport и semantic dedupe/expiry/concurrent-player isolation. Реальные smoke, dedicated two-client/reconnect/mismatch/stale, integrated, Ollama dialogue/retry и exact Sodium/Iris с активным Complementary r5.5.1 подтверждены отчётом003. Итоговый предварительно заданный budget gate PASS — отчёт004. Diagnostic CPU submission не выдаётся за GPU cost; fixture GPU timers измеряют whole-world pass относительно0/1/5NPC baseline, а не только skinning.
