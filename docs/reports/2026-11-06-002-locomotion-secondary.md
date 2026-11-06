# Отчёт 002: P3.4 — locomotion и secondary physics

- **Дата:** 2026-11-06
- **Статус:** Done в controlled P3.4 границах
- **Ветка:** main
- **HEAD до/после:** 85c5f850a0d23ef9942a3c1cfef880f0d01431fd; изменения не коммитились
- **Связанные этапы:** P3.4, после завершённой P3.3; следующая P3.5
- **ADR:** [ADR-0020](../decisions/ADR-0020-scripted-gait-and-secondary-constraints.md)

## Требование и последовательность

Пользователь поручил сначала найти качественную Hoshimi Miyabi, желательно со сменой одежды, затем продолжать roadmap последовательно. [Поиск](../assets/MIYABI_MODEL_SEARCH.md) выполнен: первоисточники Crabnuts/BreamVR/VRoid, китайские MMD-площадки и зеркала. Новых assets не скачивали и не покупали; переключение нескольких комплектов не подтверждено. Проверенная Firefly остаётся моделью controlled P3.4.

## Реализация и evidence

`ProceduralLocomotion` — common host-only gait от scripted root observations в стабильном local simulation origin. Alternating stance/swing, world anchor, Two-Bone IK, общая коррекция pelvis до обеих ног, weight/sway, arm counter-swing, start/stop/replant/turn/run. Root не управляет Minecraft entity и не создаёт навигацию. Только flat ground; до +/-1024m в локальном origin, rebase требует reset.

Gait проверена в dedicated→GPU fixture stage `e`:161ticks, maxFootError2.7499104e-7, stanceSlide0, activity после stop1.0788008e-10. `build/p3-research/p34-gait-verified-server.log`, front log `p34-gait-front.log`, side `p34-gait-side-verified.log`, просмотрены `build/p33-check/gait-front.png`, `gait-side.png`. Scripted yaw нормализован в body-space presentation, а world anchors проверены численно. Вторая клиентская команда сначала не выполнилась из-за PowerShell split decimal property; исправлено кавычками `'-PmotionView=1.5707963'`.

`SecondaryPhysics` — bounded fixed1/120s Verlet tips; максимум32chains/256segments/32sphere proxies, 12substeps/call, finite input, length/cone/proxy constraints, artistic inertia carry, pause/resume/teleport/reset, per-chain conflict/invalid fallback. Gameplay/network integration ещё отсутствует. Профиль для Firefly и secondary stage `f` находятся в `src/p33Check/.../P34Scenes.java`; это ручная конфигурация конкретной модели, не importer VRM physics и не general cloth solver.

## Диагностика, не принятая за PASS

Первые gait runs обнаружили недосягаемую опору и snap при смене run→walk, затем stop replant/pelvis raise. Исправлены independent swing progress и совместная pelvis reach correction. Логи `p34-gait-*` сохранены. Первые семь secondary dedicated attempts обнаружили proxy/cone conflicts. Широкие сферы и узкие cones не соответствовали thin hair/skirt chains; calibrated core thigh spheres .045m, head .065m/chest .10m и explicit chain limits. Добавлена .25inertia для vertical root shocks. Alternating collision/cone projections давали near-tangent stalls; заменены bounded candidates на unit length sphere: desired direction, boundary circles, pair intersections; конечный результат проверяется по всем constraints.

Unit coverage: stance position/yaw/replay/turn/stop/teleport/bounds; fixed timestep/length/collision/chain-local failure/pause/resume/teleport/degenerate recovery/inertia. Regression для нескольких пересекающихся proxies проверяет выбор правильного пересечения вместо колебаний. **70tests total:67PASS,3opt-in skipped,0failures/errors.**

## Итоговые проверки

| Команда/evidence | Результат |
|---|---|
| `gradlew build runP33Server -PmotionCheck -PmotionCharacter=firefly -PmotionStage=e` | PASS37s, `p34-gait-final-server.log`; после исправления stance yaw, та же maxFootError2.7499104e-7 |
| `runP33Server ... -PmotionStage=f` | PASS25s, `p34-secondary-final-server.log`:10chains/91segments,161ticks,52contacts, maxPenetration0, maxAngle.90000314, mean physics.868ms в этом fixture |
| `runP33Client ... -PmotionStage=f` / motionView1.5707963 / .7853982 | PASS38/37/38s, `p34-secondary-final-{front,side,three-quarter}.log`; все три PNG просмотрены |
| Pause/resume/teleport | Base сохраняется; velocity сбрасывается; каждый outcome после teleport RESET_TELEPORT |
| JAR/source boundaries |49motion classes, нет GLB/VRM/GemRender/Flywheel/fixture classes в production JAR; common motion не импортирует client/GPU |
| `git diff --check` | PASS; только обычные LF→CRLF warnings |

Итоговые PNG: `build/p33-check/secondary-final-front.png`, `secondary-final-side.png`, `secondary-final-three-quarter.png`; свежий screenshot обязателен Gradle gate. Dedicated output тоже требует fresh stage/revision/partition match. CPU mean выше — только диагностический timing одного synthetic run, не P3.5 multi-NPC/per-client budget.

После восьмой попытки remained true cone/proxy incompatibility при vertical root shocks волос; волосы теперь наследуют anchor translation(inertia0), одежда.25. Голова продолжает поворачиваться, hair tips имеют spring/gravity response. Девятая и итоговая попытки PASS. Эксперимент28main chains дал несовместимый Qz_0_17 contact(tick6), лог `p34-secondary-full-profile.log` сохранён; профиль не активирован. [Реестр визуальных ограничений](../assets/FIREFLY_VISUAL_LIMITS.md) описывает partial/static strips, hard folds/overlap и tip-only proxies. Не заявляется отсутствие всех mesh/cloth пересечений.

## Следующий шаг

P3.4 завершена в заданных limited/controlled границах: gait и secondary на том же source rig, contacts/reset и визуальные сцены проверены. Следующая задача P3.5 — versioned pose streaming, two clients/reconnect, реальный dialogue presentation adapter, budgets и exact Sodium/Iris tests. Основной игровой renderer пока static P3.2. Полная P3 не завершена; motion integration ещё не выполнена.
