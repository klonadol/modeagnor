# Отчёт 013: P3.1 — рабочий client palette/morph и dedicated host proof

- Дата: 2026-11-05
- Статус: Done P3.1 в ограниченных fixture/API границах; P3.2 не реализована
- Ветка/HEAD: main / 85c5f850a0d23ef9942a3c1cfef880f0d01431fd; изменения в рабочем дереве
- ADR: ADR-0017 (дополнение evidence); предыдущий отчёт 012 сохраняется как история первой попытки

## Цель и исходное состояние

Продолжить незавершённый adapter proof до следующей задачи. Пользователь разрешил последовательную работу до конца фазы, с обязательным завершением каждой задачи перед следующей. Модели пользователя обнаружены в `C:/Users/User/Documents/Codex/AGNOR/assets/models/разное` (GLB/VRM/ZIP); путь сохранён в указателе и README ассетов. Их импорт ещё не выполнялся.

## Реализация

Добавлены `scripts/p3-toolchain.gradle`, `prepare-p3-toolchain.ps1`, клиентский `PaletteScreenCheck`, server-only `HostPaletteCheck` и neutral `rig-probe.json`. Стенд генерируется в ignored `build/p3-toolchain`, не меняет production toolchain и не входит в мод.

Изолированный target: Minecraft 1.21.1, Yarn 1.21.1+build.3, Java 21.0.7, Gradle 9.7.1, Loom 1.16.3, Loader 0.16.10, API 0.116.15+1.21.1, GemRender 0.1.20, Flywheel 1.0.6 из вложенного JAR закреплённого GemRender. SHA-256 GemRender — отчёт 012; бинарник не модифицировался.

Host-only build полностью исключает зависимости GemRender/Flywheel и клиентские source roots. Настоящий dedicated server стартует на loopback:25581 в отдельном synthetic save, на SERVER_STARTED читает neutral descriptor (translation-only fixture subset), проверяет topology/finite values, вычисляет global и inverse-bind skin matrices JOML и сохраняет две позы/веса. После этого server штатно останавливается. Это не универсальный GLB importer и не production pose protocol.

Клиент читает результат host из файла, проверяет matrix shape/finite values и передаёт palette/morph в `DirectRenderer.submit`. Слева rest/weight 0, справа tip Y+0.4/weight 1. Файл служит transport fixture; игровая синхронизация остаётся P3.5. Общий GLB rig/clip reader отдельно проверен bare JVM в отчёте 012; dedicated proof проверяет независимый neutral host путь, не загрузку GemRender как server mod.

## Найденные ошибки и исправления

1. Gradle 9.7.1/Loom 1.16.3 работают с установленным JDK 21; первая remap/source подготовка заняла 7m14s. Предупреждения optional mixin targets сохранены в логах.
2. Dev Loom не подключил вложенный Flywheel автоматически. В эксперименте явно извлечён оригинальный вложенный JAR и добавлен как mod dependency.
3. Flywheel требует Fabric API >=0.105.0. Target с API 0.102.1 отклонён настоящим Fabric Loader; для эксперимента применена upstream API 0.116.15. Dependency guards не обходились.
4. Windows PowerShell требует `Add-Type System.IO.Compression.FileSystem` перед распаковкой — исправлено.
5. GUI-only scene сначала отображала skeletal override, но игнорировала morph, несмотря на корректный CPU block `[0,1,4,3,1]`. Причина по source и повторному runtime: DirectRenderer привязывает MorphBuffer, а его первоначальную загрузку обычно выполняет world pass. Добавлен явный `MorphBuffer.uploadAndBind()` перед GUI flush; morph появился. Это обязательная часть GUI adapter, не успех исходного вызова без неё.

## Проверки

| Проверка | Результат |
|---|---|
| Isolated Gradle 9.7.1 `--version` на JDK21 | PASS |
| `compileJava`, API 0.102.1 | PASS compile; runtime с Flywheel FAIL по minimum API |
| `runClient`, API 0.116.15 | PASS, GPU skinning, morph после upload fix, штатный exit |
| `runServer -PhostOnly` | PASS, SERVER_STARTED → две palette → output → штатный shutdown |
| Повтор server proof с чтением neutral descriptor | PASS; `toolchain-host2.log` |
| Host palette → client image | PASS, `host-palette-proof.png`: справа виден отдельный skeletal + morph override |
| Resource reload | PASS, generation 2 и `P3_RELOAD_COMPLETE` |
| Reset после reload | PASS, `palette-proof.png`: обе модели вернулись к одинаковой rest форме |

Логи в `build/p3-research`: `toolchain-compile.log`, `toolchain-client[2..7].log`, `toolchain-host.log`, `toolchain-host2.log`. Кадры до исправления сохранены: `pose-proof.png`, `morph-before-upload.png`. Итоговые изображения в `build/p3-toolchain/run/host-palette-proof.png` и `palette-proof.png` просмотрены визуально. Логи сохранены также для неудачных запусков; первые два окна dependency errors закрыты только у собственных p3-toolchain процессов.

## Решение и пределы

P3.1 bounded proof завершён. Выбран путь **GemRender client adapter + независимый GPU-free neutral host rig metadata/evaluator**. Для production подключения в P3.2 потребуется оформленная миграция Gradle/Loom/API и регрессия AGNOR; main build пока использует прежние версии. Не надо форсировать client-only GemRender на dedicated server.

Не проверены: реальная anime-модель/материалы/rig mapping, общий production importer, мир вместо GUI, snapshots/два клиента, Sodium/Iris, performance, IK/gait/physics. Они относятся к последующим частям P3; fixture не доказывает завершение фазы. Sodium/Iris exact runtime matrix и shader fallback проверять до приёмки полной P3. Не объявляется поддержка произвольного glTF по translation-only neutral descriptor.

## Воспроизведение

После `prepare-p3-check.ps1` и наличия upstream snapshot отчёта 012:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/prepare-p3-toolchain.ps1
# JAVA_HOME = JDK21, GRADLE_USER_HOME = repo/.gradle
./build/p3-toolchain/gradlew.bat -p build/p3-toolchain runServer -PhostOnly --console=plain
./build/p3-toolchain/gradlew.bat -p build/p3-toolchain runClient --console=plain
```

Клиент сохраняет кадр до reload и после reset и сам закрывается. Fixture source roots и candidate JAR остаются вне обычного AGNOR artifact.
