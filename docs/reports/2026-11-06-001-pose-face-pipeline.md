# Отчёт 001: P3.3 — host animation, IK, arbitration и лицо

- **Дата:** 2026-11-06 (работа начата вечером 2026-11-05, продолжена после выключения компьютера)
- **Статус:** Done / P3.3 Verified в controlled границах ROADMAP
- **Ветка:** main
- **HEAD до/после:** `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`, изменения остаются в рабочем дереве, commit/push не выполнялись
- **ADR:** ADR-0019; база — ADR-0018/отчёт014
- **Этап:** P3.3(a–d); P3.4/P3.5 не реализованы

## 1. Цель и порядок

Продолжить после P3.2 строго последовательно: (a) clips/crossfade/masks/additive; (b) FK/Two-Bone IK/joint limits; (c) whole-body arbitration/foot/look/reach; (d) eyes/face/independent expression lanes. Следующий этап начинается после проверки текущего. Изначально использована Furina, затем body и face проверены на общей Firefly-модели.

## 2. Реализация

Добавлены common CPU classes в `dev.agnor.motion`: RigDescriptor/JointTransform, AnimationClip/PoseMixer, TwoBoneIk/IkPoseSolver/LookAtSolver, MotionTask/WholeBodyController, FaceController. Контракты и ограничения — ADR-0019.19целевых unit tests проверяют математику, immutable state, invalid inputs, пределы/недостижимость, priority/cancel/expiry/capacity, независимость face lanes и smoothing.

`src/p33Check`, `scripts/p33-check.gradle`, prepare scripts — opt-in host/client scenes. Dedicated Minecraft server конструирует original rig и вычисляет poses, JSON frames содержат только уже рассчитанные matrices/morph weights. Клиент не выполняет clips/IK/physics. Это controlled transport fixture; постоянная игровая NPC ещё не анимируется этими test files.

`partition-local-model.py` расширен для70morphs и компактного вывода только используемых vertex/accessor данных, без повторного вложения исходного55MBbuffer в каждую часть. Firefly:18GLB, максимум221palette slots, около53MBсуммарной геометрии/морфов. Texture images вынесены в локальные ресурсы. Original hierarchy, inverse binds и source joint indices сохранены для host mapping. Исходные weights/вершины/морфы не упрощались.

## 3. Blender QA и ассеты

Применён проектный Blender Quality Workflow. Live Blender5.2 проверен через status/scene/addons; исходная сцена содержала только Camera/Light. Перед импортом и major QA steps созданы backups в `C:/Users/User/Documents/BlenderMCP/backups`.

VRM `190034762488033771.vrm` имеет metadata title Firefly, author maisan_twosaka. SHA256 `37c32b19702bd2d8b83e9837ecd98f1f167df0123b48bffa0679fe56cf5e21a2`; права не объявлены подтверждёнными, TEMPORARY_RESTRICTED в реестре. Body29462vertices/37673polygons,70shape keys + Basis, UV присутствуют, Armature modifier корректен, unweighted vertices0. Проверены neutral front/side/three-quarter, blink/smile/smile+mouth/surprise и gaze L/R. Изменения test poses восстановлены в finally; исходник не перезаписан. Геометрия не пересоздавалась.

Рендеры: `build/p33-check/blender/`. После выключения компьютера QA восстановлена из backup в отдельную сцену и сохранена с packed resources как `local-assets/firefly/firefly-face-qa.blend` (8663269bytes); пользовательская активная сцена восстановлена. Это QA checkpoint, не public character package.

Reach дополнительно проверен на исходной Blender-геометрии в front/side/three-quarter: `reach-verified-*.png`. Для переноса серверных globals в Blender использована rest-basis correction и desired parent transforms; контрольная ошибка wrist matrix =1.3657e-7. Первые `reach-*.png` были диагностическими с неверным parent-space переносом, не приняты; исходная pose восстановлена. После корректного переноса рукав/предплечье/кисть соединены, source geometry не менялась.

## 4. Приёмочные сценарии

| Срез | Фактическая проверка |
|---|---|
| a | Host clips и STEP/LINEAR sampling, shortest-path rotations; image Rest/Crossfade0.5/Masked arm/Additive0.5 на Furina и Firefly |
| b | Reach, bend limit0.8, unreachable goal; длины сохранены, root position не двигается; dedicated + image. Firefly reachable error порядка1.5e-7model units; residual limits/недостижимости наблюдаемы |
| c | Foot/look/reach совместно; конфликтующая low-priority задача PREEMPTED; cancel освобождает claim; expiry восстанавливает base; flat-foot orientation сохраняется. Dedicated outcomes и image |
| d | Neutral, blink, smile, smile+mouth, surprise, eyes L/R, neutral reset. Host вычисляет eye bones/morph weights; Minecraft GPU изображение просмотрено |

Первичные Furina images/logs сохранены в `build/p33-check/furina-baseline` и `build/p3-research/p33[a–c]*`. Общая Firefly-проверка — `p33-*-verified-server/client.log`; окончательные картинки `build/p33-check/pose-proof-a.png` … `pose-proof-d.png`.

## 5. Ошибки, исправления и восстановление

- При компиляции устранён конфликт `org.joml.Math` wildcard import. В одном тесте ошибочно требовалась неизменность root rotation при IK; исправлена проверка именно position/scale, вращение root необходимо для решения.
- Тестовый controller исходно был создан не в source root; файл перемещён в `src/main/java/dev/agnor/motion` до принятой сборки.
- У VRM native Joy оказался слабо читаемым. Проверены реальные deltas и подобран source-key profile smile: brows6, eyes11, mouth corners56. Mouth-open33 управляется отдельно. Подтверждено Blender-изображением.
- `vrc.blink_*` slots0/1 не давали закрытие на GPU. Исправлены profile bindings на VRM blink_l13/blink_r14; повторный кадр показывает закрытые веки.
- GUI direct path первоначально добавлял diffuse lighting к unlit-materials VRM. В face fixture используется явный emissive/unlit light path; MToon/outline/full shader compatibility этим не объявлена.
- При переносе reach-сценария на противоположно ориентированный rig смещение Furina было недостижимым. Goal/pole теперь вычисляются из actual chain и model forward. Это ошибка fixture target, не принятый solver PASS.
- Minecraft server может завершить аварийную test scene с exit0 JavaExec. Из-за этого клиент однажды отобразил старый Furina JSON после failure нового Firefly bake. Такой запуск не принят. Добавлены удаление предыдущего generated output перед server run, атомарная запись и проверки stage/revision/partition count в server doLast и client doFirst; последующие proofs используют свежие данные. Неудачные logs/crash report сохранены.
- В ранних diagnostic strings было hardcoded `parts=7`; заменено реальным count. Evidence о Firefly18parts берётся из validated JSON/model loads, не из старой строки.

После выключения: старых Java/Gradle процессов не обнаружено; два Python процесса были вновь запущенными services и не нашими model scripts, их не завершали. Логи a–c до выключения содержали BUILD SUCCESSFUL, исходники/изображения целы. В момент выключения face-код ещё не имел runtime приёмки; её выполнили после восстановления.18GLB headers/lengths проверены; synthetic world SQLite `PRAGMA integrity_check` =ok. Gradle самостоятельно запущен заново, recovery motion tests PASS.

## 6. Проверки и ограничения

Команды: обычный `gradlew.bat build`; targeted `test --tests dev.agnor.motion.*`; `runP33Server`/`runP33Client -PmotionCheck -PmotionStage=a|b|c|d`, с `-PmotionCharacter=firefly` для body на общей модели. Подготовка — `prepare-p33-check.ps1` и `prepare-p33-face.ps1`.

Итоговая ordinary build PASS (22s):60tests,0failures/0errors,3opt-in skipped —57прошли, включая19motion/face. Финальные8server/client запусков `p33-*-verified-*` завершились успешно; Gradle output guard также проверен отрицательным запуском Furina-client против Firefly-file — ожидаемый exit1 до запуска игры, `p33-negative-stale.log`. Исправленные Firefly b residual: reachable9.996e-8, limited0.07338, far2.56879; flat foot/contact и reach в c дают APPLIED с residual порядка1e-7. Для приятной и читаемой позы goal/pole измеряются относительно цепи и model forward, не направляются за корпус.

Проверены source-set границы и обычный JAR:33common motion classes присутствуют, GemRender/Flywheel/fixtures/restricted parts/textures отсутствуют. `git diff --check` PASS. После тестов игровых Java/fixture процессов нет, остаётся штатный Gradle daemon. Изменения только в рабочем дереве.

Не заявлены general Content Pack/clip importer, CUBICSPLINE, non-uniform-scale IK, full shoulder/twist/whole-body optimization, terrain collision или физический баланс. Facial support самой Furina отсутствует; controlled face/body target теперь Firefly. Рот в test scene задаётся явно, это не TTS/lip-sync. Основная игровая presentation пока static P3.2; runtime sync/реальный диалог подключаются в P3.5 после P3.4.

Sodium/Iris, CPU/GPU/network performance, несколько анимированных NPC/двухклиентская pose sync не проверены этим срезом и не объявлены PASS. Ограниченные common tests/controlled scene не завершают всю P3.

## 7. Итог

Все четыре части P3.3 реализованы и последовательно проверены, включая общую Firefly-модель тела и лица. Следующая задача — P3.4: сначала gait/start-stop-turn-run/pelvis/arms, затем secondary springs и collision proxies на той же модели. P3.4 не начиналась, полная P3 не завершена. Публичные артефакты не должны содержать restricted source/derivatives.
