# ADR-0017: P3.1 renderer capability и граница адаптации

**Дополнение 2026-11-05:** adapter proof выполнен — [отчёт 013](../reports/2026-11-05-013-renderer-adapter-proof.md). Принят путь GemRender client adapter + независимый neutral host descriptor/evaluator. На изолированной матрице Gradle 9.7.1/Loom 1.16.3/API 0.116.15/Loader 0.16.10 подтверждены dedicated host output, client GPU palette/morph, reload/reset; GUI требует явного morph upload. Production dependency/toolchain migration и реальная модель — P3.2. Ниже сохранён контекст исходного отрицательного drop-in результата.

- **Статус:** Accepted — результат ограниченного spike; production renderer не выбран
- **Дата:** 2026-11-05
- **Область:** P3.1, import/headless pose/client rendering
- **Основание:** [отчёт 012](../reports/2026-11-05-012-renderer-capability.md), ADR-0006/0007/0009

## Контекст

Нужны glTF skin/morph, вычисление pose на хосте без GPU и передача готовой palette/morph state клиентскому renderer. Критерий P3.1 — ответ о доступности API и точной совместимости, а не готовая NPC, gait или physics. Отрицательный результат допускает ограниченный adapter/custom proof по ROADMAP.

## Решение

1. Сохранить glTF 2.0/GLB как предпочтительный interchange. Малый оригинальный fixture содержит mesh, два skin joints, inverse bind, POSITION morph и LINEAR translation/weight clip. Он не является моделью персонажа.
2. GemRender 0.1.20 — **кандидат для адаптации, не готовая production dependency**. Опубликованный Fabric 1.21.1 JAR имеет внешние pose/palette/morph API, но отклоняется нашим Loom 1.7.4: artifact собран Loom 1.16.3. В metadata `environment=client`; dedicated server не получает библиотеку через обычный Fabric mod loading. Успех bare-JVM расчёта сам по себе это не исправляет.
3. BlendLib approved v1 не подходит как единственный renderer: loader явно отвергает morph targets/weights. X9 — только экспериментальная валидация; её нельзя считать runtime morph support.
4. Собственный renderer остаётся резервом без статуса PASS. Его не реализуем под видом проверки библиотеки. Production build, Loader/API/Loom, NPC identity и persistence в P3.1 не меняются.
5. Spike вынесен в opt-in source set; сторонний JAR хранится только в ignored build directory, проверяется SHA-256 и не включается в AGNOR JAR. Исходники библиотек не копируются в production.

## Следующий ограниченный proof (до P3.2)

Предпочтителен GemRender adapter/backport, если его цена меньше собственного renderer:

1. В отдельном эксперименте собрать pinned GemRender target под согласованный toolchain либо обосновать миграцию toolchain отдельным изменением; не удалять Loom manifest check для имитации совместимости.
2. Выделить GPU-free host rig/clip reader/evaluator либо независимый neutral descriptor reader. GemRender-specific layout остаётся за adapter boundary; client-only mod не становится server dependency. Проверить реальный dedicated classloading, включая resource resolver; bare JVM с embedded buffer недостаточна.
3. На том же fixture подать две разные host palettes и morph weights 0/1 в клиентский `DirectRenderer.submit`/BoneBuffer; подтвердить реальное изображение, отсутствие общей mutable pose между экземплярами, reset и resource reload. Нельзя заменить это проверкой массива CPU.
4. Сначала baseline MC 1.21.1; затем exact Sodium/Iris пары. Для GemRender upstream target записаны Sodium mc1.21.1-0.6.9-fabric и Iris 1.8.8+1.21.1-fabric; это кандидаты, не наша проверенная совместимость.

**Условие остановки:** после первого рабочего host→client palette+morph fixture и classloading evidence выбрать adapter; при конкретной непреодолимой capability зафиксировать её и перейти к столь же малому custom GPU skin+morph proof. Не начинать anime import, IK/gait/physics, network pose protocol или Content Packs до результата. Требования качества ADR-0006 не снижаются.

## Лицензии и последствия

По metadata/source GemRender — LGPL-3.0-only (GPL text + LGPL additional terms), BlendLib — Apache-2.0; вложенные зависимости имеют собственные notices. Для будущего распространения adapter/fork потребуется сохранить соответствующие notices/source obligations и определить способ упаковки. Сейчас сторонние artifacts не распространяются. Оригинальный fixture не использует restricted assets.

P3.1 имеет отрицательный ответ для готового drop-in backend на текущей матрице. Это не завершение P3 и не разрешение считать renderer проверенным по unit-тесту.
