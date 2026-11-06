# Отчёт 012: P3.1 — renderer API и точная матрица

- **Дата:** 2026-11-05
- **Статус:** Done — ограниченное исследование с отрицательным drop-in результатом; production renderer не выбран
- **Ветка:** main
- **HEAD до работы:** `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`
- **HEAD после работы:** тот же; изменения в рабочем дереве, commit/push не выполнялись
- **Связанные ADR:** ADR-0006/0007/0009, [ADR-0017](../decisions/ADR-0017-renderer-capability-spike.md)
- **Связанные этапы:** P3.1; P3.2 не начата

## 1. Цель и исходное требование

По запросу пользователя начать P3.1 согласно документации: сопоставить GemRender/BlendLib/custom, проверить малый rig+morph fixture и headless pose, выяснить возможность client palette/morph override на exact Fabric matrix. Условие остановки — конкретный ответ по API/совместимости; gait/physics не входят в работу. ROADMAP допускает ограниченный adapter/custom план, если ни один готовый backend не проходит.

## 2. Состояние до изменений

Чистое рабочее дерево main; P0–P2 Verified, rendering отсутствует. Minecraft 1.21.1, Yarn 1.21.1+build.3, Loader 0.16.10, Fabric API 0.102.1+1.21.1, Java 21, Loom 1.7.4, Gradle 8.8. Существующая NPC остаётся vanilla placeholder.

## 3. Выполненные действия

- Прочитаны проектный skill, ROADMAP, Blueprint, ADR-0006/0007 и текущий код.
- Получены актуальные upstream source snapshots в ignored `build/p3-research/`; проверены лицензии, исходники API, target properties и published JAR metadata.
- Создан оригинальный fixture: три vertices, два skin joints, inverse bind matrices, POSITION morph и LINEAR translation/weights clip. Детерминированный генератор сохранён в репозитории.
- Добавлен opt-in bare-JVM harness с pinned binary SHA-256, проверены import/evaluate/override/reset. Для точной клиентской матрицы отдельно вызван Loom с candidate dependency.
- Зафиксированы ограниченный отрицательный результат и следующий adapter proof. Production зависимости/версии и игровое поведение не изменены.

## 4. Изменённые файлы

| Путь | Изменение | Причина |
|---|---|---|
| `build.gradle`, `scripts/p3-check.gradle` | Опциональные p3Headless и p3Client probe | Изолировать эксперимент от release |
| `scripts/prepare-p3-check.ps1` | Download pinned binary + SHA-256 guard | Воспроизводимость |
| `scripts/create-p3-fixture.py`, `src/p3Check/` | Генератор, fixture, API assertions, инструкции | Минимальный rig+morph proof |
| ADR-0017, этот отчёт, журнал/индекс/карта/ROADMAP/README | Фактический статус и следующий ограниченный шаг | Не смешивать API evidence с runtime acceptance |

## 5. Сравнение и решение

| Кандидат | Source/license | Headless и override | Exact matrix / решение |
|---|---|---|---|
| GemRender 0.1.20 | main `e74c296cf3faa03eaf59984296a2f159c13f256b`; release tag `9feff65a6d0d42230f84d44ce4c8ec28d20631e6`; LGPL-3.0-only | `GltfSkeletonReader`, `GltfPose`, `NodeTable`, `GltfMorphLayout`; `DirectRenderer.submit(model, Matrix4f[], float[], ...)`, `BoneBuffer.addPalette/addMorphBlock` существуют | Published JAR не проходит Loom 1.7.4; metadata client-only. Кандидат для adapter, не production PASS |
| BlendLib | main `62f82c9b2cf24a8c569ba66058ff8821f046e74b`; Apache-2.0 | Approved v1 `ModelAssetLoader` отвергает mesh/primitive/node morph weights/targets; X9 только validation | Не закрывает обязательный morph runtime; README требует Loader >=0.19.5. Не запускался как renderer |
| Custom | Реализации нет; можно писать свой код без копирования библиотек | Возможность спроектировать neutral rig/host pose/client skin+morph, но evidence отсутствует | Резервный bounded proof; нельзя объявить готовым по формуле skinning |

GemRender tag и main имеют одинаковые исследованные `GltfSkeletonReader`/`GltfPose`; DirectRenderer после tag менялся в water/translucency flush. Исполняемые результаты относятся строго к скачанному release binary, не ко всему main. LGPL/GPL тексты и GemRender metadata прочитаны совместно: наличие файла GPL не означает, что надо игнорировать `LICENSE.LESSER`. Вложенный Flywheel имеет отдельные notices. Сторонние source/JAR не добавлены в Git или продукт.

Published artifact: `gemrender-0.1.20+1.21.1-fabric.jar`, SHA-256 `5909d533036864a1a7bc3eb3cb65cf738899d855d21b97834702e9cd8efabe4f`.

## 6. Проверки и результаты

| Проверка | Результат | Что подтверждено |
|---|---|---|
| `gradlew.bat p3Headless -Pp3Check --console=plain` | PASS | Без Minecraft/Fabric/LWJGL: GltfSkeletonReader, LINEAR clip t=.5 → skin translation .5; embedded buffer import, rig hierarchy, inverse bind cancellation; external pose и morph .75; reset к rest |
| `gradlew.bat compileClientJava -Pp3Check -Pp3Client --console=plain` | FAIL, диагностированный результат | Loom: `Mod was built with a newer version of Loom (1.16.3), you are using Loom (1.7.4)`; до compile/runtime не дошло |
| Published `fabric.mod.json` | Source/metadata evidence | `environment=client`, depends Loader >=0.16, MC >=1.21.1 <1.22, Java >=21, Flywheel >=1.0 <2.0; bundled Flywheel 1.0.6 |
| GemRender target properties | Source evidence, не runtime PASS | Upstream собирает 1.21.1 на Loader 0.19.3/API 0.116.15+1.21.1; Sodium mc1.21.1-0.6.9-fabric, Iris 1.8.8+1.21.1-fabric |
| `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 build` | PASS | Обычная сборка без candidate dependency; compile/test UP-TO-DATE, remapJar/remapSourcesJar выполнены. Нового запуска ordinary tests не заявляем |
| Содержимое `build/libs/agnor-0.1.0-dev.jar` и `git diff --check` | PASS | Нет GemRender/RendererApiCheck/rig-morph в release JAR; whitespace ошибок нет |

Локальные полные логи: `build/p3-research/headless.log`, `client-matrix.log`, `build.log`. Проверенный headless runtime classpath задаётся только candidate JAR, JOML 1.10.5 и Gson 2.10.1. CPU morph block — не GPU morph render.

**НЕ ПРОВЕРЕНО:** игровая client palette/morph картинка, GPU skinning, dedicated Fabric loading candidate, Sodium/Iris runtime, reload, две NPC, performance. Клиентский запуск остановлен на доказанном toolchain mismatch; это не графический PASS. Обычный Fabric dedicated loader исключит client-only mod: для server pipeline требуется отдельная упаковка/reader adapter, а не надежда на bare-JVM результат.

## 7. Ошибки и диагностика

- Modrinth endpoint по project id из upstream вернул 404; binary получен из официального GitHub release и закреплён hash.
- Первый Gradle harness использовал `sourceSets { p3Check }`, что не создало конфигурацию (имя совпало с project property). Исправлено на явный `create('p3Check')`; последующий запуск PASS.
- После намеренно неуспешного candidate remap Loom восстановил свой cache и повторно remap-нул обычные зависимости; последующий headless завершился успешно.
- Windows PowerShell запуск `-File scripts/dev.ps1` остановлен локальной ExecutionPolicy. Повтор для данного процесса с `-ExecutionPolicy Bypass`; системная policy не менялась.

## 8. Риски и ограничения

Fixture минимальный и не доказывает художественное качество, большие rigs, прозрачность, normals deformation или материал реальной anime-модели. Не выполнялись performance замеры — performance PASS не заявлен. Прямой SkeletonReader испытан на embedded buffers с null resolver; external resource resolver и dedicated classloading требуют отдельной проверки. Ни готовая библиотека, ни custom renderer не объявлены выбранными.

## 9. Итог

Ограниченный P3.1 spike завершён с отрицательным drop-in результатом: API GemRender подходит по форме и CPU проверке, но published artifact не подходит текущему build/side layout; BlendLib v1 не закрывает morph. План adapter/custom proof закреплён в ADR-0017 согласно fallback ROADMAP. P3 в целом не завершена. До P3.2 нужен малый GemRender adapter/backport + GPU-free host reader proof и реальный client palette/morph fixture; полноценный renderer с нуля, походка и физика не писались.

## 10. Источники

- [GemRender release v0.1.20](https://github.com/Warfactory-Official/GemRender/releases/tag/v0.1.20).
- [GemRender headless reader на исследованном main](https://github.com/Warfactory-Official/GemRender/blob/e74c296cf3faa03eaf59984296a2f159c13f256b/src/main/java/com/wf/gemrender/gltf/GltfSkeletonReader.java).
- [GemRender DirectRenderer API](https://github.com/Warfactory-Official/GemRender/blob/e74c296cf3faa03eaf59984296a2f159c13f256b/src/main/java/com/wf/gemrender/direct/DirectRenderer.java).
- [GemRender exact target](https://github.com/Warfactory-Official/GemRender/blob/e74c296cf3faa03eaf59984296a2f159c13f256b/versions/1.21.1-fabric/gradle.properties).
- [BlendLib approved loader](https://github.com/LIy-hub/BlendLib-Public/blob/62f82c9b2cf24a8c569ba66058ff8821f046e74b/blendlib-core/src/main/java/com/liy/blendlib/core/loader/ModelAssetLoader.java).
- [BlendLib X9 boundary](https://github.com/LIy-hub/BlendLib-Public/blob/62f82c9b2cf24a8c569ba66058ff8821f046e74b/docs/expansion/x9/README.md).
