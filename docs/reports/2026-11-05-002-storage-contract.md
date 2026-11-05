# Отчёт 002: P1.1 — backend и world-scoped storage contract

- **Дата:** 2026-11-05, Asia/Vladivostok (UTC+10)
- **Статус:** Done — только ограниченное исследование P1.1
- **Ветка:** main
- **HEAD до/после:** `5d06367178325fcb6b41c1075af1ae0c5cc91613`; коммит не создавался
- **ADR:** [ADR-0010](../decisions/ADR-0010-world-scoped-storage.md)
- **Этап:** P1.1; P1.2 не реализуется

## 1. Цель и исходное состояние

Продолжить новый roadmap после завершённой P0: одна контрольная сборка с существующими тестами, затем ограниченный выбор SQLite/JSON и world-scoped contract. Workspace AGNOR не является Git root; репозиторий — вложенный `modeagnor`.

Рабочее дерево изначально dirty: изменены README, архитектура, Blueprint, roadmap, журнал, индекс, реестр решений, ADR-0001–0008, реестр ассетов; build/wrapper/src/scripts/skill, документация аудита и отчёты 2026-11-04/05 не отслеживались. Полный первоначальный status сохранён в `build/p1.1-evidence/status-before.txt`. Эти изменения не сброшены. Применимого AGENTS.md не обнаружено; использован `skills/agnor-development/SKILL.md`.

Существуют только bootstrap, resource directories, probe и три DataRoots tests. `serverData` не имеет приватного writer и указывает на общий legacy каталог. Предыдущие отчёты и аудит прочитаны как исторические свидетельства; проверки будущих подсистем не повторялись.

## 2. Выполненные действия и решение

Контрольная сборка закончилась успешно до создания fixture. Подтверждённого блокирующего дефекта нет, production исходники не менялись. Затем создан ограниченный Python fixture вне source sets и JAR, выполнен один сравнительный прогон и принят ADR-0010. Выбран SQLite на save; JSON также прошёл минимальные сценарии, но требует собственного snapshot/replace протокола для совместной записи.

Contract задаёт `<server-save>/agnor/state.db`, persistent WorldId, NPC/player scope, границы transaction/lifecycle/thread ownership, ошибки и migration policy. `DataRoots.serverData` явно исключён. Источники по SQLite, Java atomic move и точному Yarn API приведены непосредственно в ADR. JDBC/native Fabric integration остаётся проверкой следующей реализации, а не результатом этого исследования.

## 3. Изменённые файлы

| Файлы | Изменение |
|---|---|
| `scripts/storage_spike.py` | Изолированный воспроизводимый fixture, без сторонних Python dependencies |
| `docs/decisions/ADR-0010-world-scoped-storage.md` | Выбор, альтернативы, contract и границы evidence |
| Этот отчёт, `docs/DEVELOPMENT_LOG.md` | Результаты и статус |
| `README.md`, `docs/ROADMAP.md`, `docs/PROJECT_MAP.md` | P1.1 завершена; следующий этап P1.2, без реализации |
| `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION_BLUEPRINT.md` | Уточнение backend для P1 и ссылка на обязательный contract |
| `docs/DECISIONS.md`, `docs/INDEX.md` | Регистрация ADR и навигация |

## 4. Команды и фактические результаты

Windows PowerShell, cwd — repository. Для Wrapper выставлены `GRADLE_USER_HOME=<repo>/.gradle` и `JAVA_HOME=%APPDATA%/ElyPrismLauncher/java/java-runtime-delta` (существующий JDK из dev.ps1). Выполнена ровно одна контрольная сборка:

```powershell
.\gradlew.bat build --rerun-tasks --console=plain
python scripts/storage_spike.py
```

| Проверка | Результат / свидетельство |
|---|---|
| Wrapper build | Exit 0, BUILD SUCCESSFUL in 46s; 10 actionable tasks: 10 executed |
| Существующие DataRoots tests | 3 tests, 0 failures/errors/skipped; test task выполнена, не UP-TO-DATE |
| Fixture | Exit 0; Python 3.14.7 / SQLite 3.50.4 / Windows-11-10.0.26200-SP0 |
| Начальное состояние | Для каждого backend ровно один NPC и две разные relationship values |
| Явный rollback | Полное совпадение с предыдущим snapshot при новом открытии |
| Прерывание до commit | Child exit 73; новое чтение в отдельном процессе видит только старое состояние |
| Update + restart | Новая позиция и обе relationship values восстановлены вместе в новом процессе |
| Failed migration | Явный rollback и exit 73 оставили старые данные и schema version |
| Migration v1→v2 | Добавлена fixture revision=0, schema=2; данные и отношения сохранены после нового открытия |
| Два мира | Изменение/миграция A не изменили B при одинаковых NPC/player IDs |

Все семь групп fixture прошли для **обоих** backend. SQLite использовал DELETE journal/FULL synchronous/foreign keys; чтения выполняли integrity/FK checks. JSON использовал один документ, flush/fsync temp и os.replace. После прерывания JSON temp может остаться; источником истины является только canonical файл. Fixture не реализует recovery service.

Локальные evidence (ignored build directory):

- `build/p1.1-evidence/bootstrap-build.log`;
- `build/p1.1-evidence/TEST-dev.agnor.DataRootsTest.xml` (timestamp XML: `2026-11-05T03:33:43` UTC);
- `build/p1.1-evidence/storage-d964cfow/results.json`, рядом синтетические save A/B обоих backend;
- `build/p1.1-evidence/source-hashes-before.json` — для финального контроля неизменности src.

Краткое evidence остаётся в этом отчёте, воспроизводимый fixture — в scripts; локальные БД и build logs не добавляются в Git.

## 5. Ошибки, предел проверки и итог

Ошибок контрольной сборки/fixture не было; bootstrap fix не потребовался. Первоначальная попытка git status из workspace вернула «not a git repository»; правильный вложенный repository найден до сборки.

Restart здесь означает новый Python процесс и повторное открытие файла, **не перезапуск Minecraft с NPC**. Вызов Yarn API проверен по документации, не запуском мира. Нет проверки JDBC/Java/native packaging, реального disk-full/ACL, потери питания, ошибки в середине физического commit, backup/restore, concurrent writers и performance. Эти ограничения не скрыты за PASS fixture. Не создавались Facade, settings, entity, public API, очереди, memory stores или будущие таблицы.

Renderer, IK, модели, физика, provider, повторная сеть и игровые запуски не проверялись. P0 принята по прежним отчётам в новом scope плюс фактическая контрольная сборка. **P1.1 завершена как решение категории Б; вся P1 не завершена.** Следующий шаг — P1.2 по roadmap, только по следующему заданию.
