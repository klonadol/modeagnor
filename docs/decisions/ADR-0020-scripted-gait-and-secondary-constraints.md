# ADR-0020: Scripted-root gait и ограниченные secondary chains

- Дата:2026-11-06
- Статус: Accepted implementation; model acceptance/evidence — [отчёт002](../reports/2026-11-06-002-locomotion-secondary.md)
- Этап:P3.4, продолжение ADR-0019; gameplay sync остаётся P3.5

## Решение

`ProceduralLocomotion` получает trusted root observations, velocity, yaw и flat-ground offset. Он не меняет entity/root, не ищет path и не генерирует цели. Профиль связывает hips и четыре direct limbs с source rig. Цикл stance/swing удерживает позицию и yaw опорной стопы в стабильном simulation origin; перенос имеет собственный progress, сохраняющийся при run/walk transition. После stop ноги перепоставляются в neutral. Общая pelvis correction выполняется до обоих Two-Bone IK, затем сохраняется flat-foot orientation; плечи получают counter-swing. Reach остаток возвращается явно, unsupported terrain/contact observations не имитируются.

`SecondaryPhysics` владеет состоянием tips/previous/anchor и фиксированным1/120s clock. Максимум32chains,256segments,32proxies и12substeps/call. Только uniform-scaled direct chains, непересекающиеся ownership и независимые от них bone-sphere proxies. Rendering/GPU отсутствуют в common. Physics применяется после base gait/pose; base остаётся immutable.

Length и cone — hard constraints. Если tip пересекает sphere proxy, constraints сводятся к half-spaces для unit direction: рассматриваются desired direction, nearest boundary-circle points и pair intersections. Выбирается ближайший допустимый кандидат; все ограничения проверяются повторно. Это исключает near-tangent stalls alternating projections. Несовместимая система восстанавливает только свою chain в base и выдаёт CONSTRAINT_CONFLICT; вырожденная/nonfinite chain — RESET_INVALID. Остальные продолжают работать. Защита касается **tips**, не всех triangles/segments/cloth поверхности.

Artistic inertia[0,1] частично переносит tips вместе с перемещением chain anchor. Это явная настройка устойчивости к root shocks, не импорт коэффициентов VRM. В Firefly волосы наследуют anchor translation(inertia0), но реагируют на поворот head, gravity/springs; одежда использует.25. Pause отбрасывает velocities и возвращает base, resume начинается заново. Teleport>2m или yaw jump>1.5rad сбрасывает state. Unload/rebase вызывает reset. Структурно неверный input отклоняется до изменения состояния.

Координаты — локальный стабильный origin +/-1024m; Minecraft world coordinates нельзя непосредственно сужать до float. P3.5 producer должен держать simulation origin/rebase/reset. Здесь нет нового world/NBT persistence owner.

## Границы и последствия

P3.4 проверяет тот же source Firefly rig, что P3.3. Controlled stage e — gait, f — secondary; dedicated host рассчитывает poses/palettes, клиент только рисует frozen output. Fixture JSON не является игровым network protocol. Профиль вручную связывает10chains/91segments и4core spheres, это не P4 importer и не полная secondaryAnimation extension.

Эксперимент28chains сохраняет дефект incompatible Qz_0_17 contact; не активируется и не объявляется PASS. Тонкие промежуточные поверхности, соседние static strips, full self-collision, capsules/triangle collisions и все декоративные цепи не покрыты текущими sphere tips. Полная визуальная P3 требует явной регистрации оставшихся дефектов и P3.5 integration/performance/renderer checks. Нельзя объявлять all-cloth clipping устранённым по нулю tip penetration.
