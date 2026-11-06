# Отчёт 004: исправление storage-зависимости и начало P1.3

- Дата: 2026-11-05 (Asia/Vladivostok).
- Статус: **Partial P1.3**; исправление connection settings P1.2 выполнено.
- Ветка / HEAD: main / `338b491517a23720b7ecacfe32fc8c7d13ef8ad6`. Коммит не создавался.
- Исходное дерево: незакоммиченные файлы P1.2 и уточнения P1.1 сохранены.
- Основание: последовательная работа по ROADMAP и запись локального Ollama launcher в указатель.

## Изменения

`WorldStorage` создаёт новое JDBC connection на каждую операцию. Настройка foreign keys первоначально применялась только к initializer connection. Это подтверждённый дефект для будущих relationship FK: теперь foreign_keys/FULL synchronous/busy_timeout=1000 применяются к каждому transaction connection. Transaction/backup/restore/close сериализованы на handle. Это не межпроцессный lock и не координация разных handle одного save.

Добавлены две содержательные storage-проверки: FK violation отклоняется в новом transaction connection; SQLite max_page_count ограничивает БД, реальный engine возвращает error code 13 (SQLITE_FULL), а после повторного открытия сохранена прежняя запись. Это не проверка фактического disk-full/ACL на ОС.

P1.3 начата: добавлены immutable `CharacterDefinition` schema v1/revision и `DefinitionRegistry` с встроенным `agnor:dev`; проверяются ID, revision, display name, duplicate/unknown definitions. Registry пока является готовым компонентом без entity consumer. Создание/загрузка/unload/delete NPC, tombstone/idempotency, runtime snapshots и отношения ещё **не реализованы**; P1.3 не закрыта.

В `../УКАЗАТЕЛЬ.md` добавлена отдельная запись о `C:/Users/User/Desktop/Nova-Ternary-Bonsai-Vision.bat`. Наличие файла подтверждено (`6139` bytes), назначение Ollama/API test — по сообщению пользователя. Launcher не запускался, API/inference не проверены. Он будет нужен при provider-задачах P2.

## Проверки

Первая попытка `powershell -File scripts/dev.ps1 build` заблокирована Windows execution policy до запуска Gradle. Вместо изменения policy использован Wrapper напрямую с существующим JDK из ElyPrismLauncher и локальным GRADLE_USER_HOME.

`gradlew.bat build --console=plain` после storage fix: **PASS**. После добавления registry и его тестов та же команда: **PASS**, exit 0, BUILD SUCCESSFUL in 8s; 7 tasks executed / 3 up-to-date. Новые suite: storage — 6 тестов, registry — 2 теста. Игровые запуски, renderer/IK/physics, Ollama/API не выполнялись.

## Следующее продолжение

Продолжить P1.3 с сохраняемыми instance UUID/runtime/relationships и lifecycle, затем выполнить критерии задачи. Нельзя считать registry достаточным свидетельством сохранения/восстановления NPC. Интеграционные ограничения отчёта 003 остаются явными.
