# ADR-0003: Внешние Content Packs вне JAR

Уточнение 2026-11-06: путь/manifest/activation/sync реализованы и проверены в P4 — [ADR-0022](ADR-0022-content-pack-schema-and-ingestion.md), [0023](ADR-0023-content-generations-and-presentation.md), [0024](ADR-0024-content-controls-and-opt-in-transfer.md), [итог008](../reports/2026-11-06-008-phase4-acceptance.md). Нижний список открытых проверок — исходный исторический срез; физический ACL-denial остаётся отдельным непроверенным случаем.

> Фазовые предписания пересмотрены 2026-11-05 в [ADR-0009](ADR-0009-dependency-driven-plan.md); функциональное решение сохраняется.

- **Статус:** Accepted; детали пути и синхронизации требуют валидации
- **Дата:** 2026-11-02
- **Область:** Content / Models / Configuration

## Контекст

Пользователь хочет хранить `agnor.jar` и другие моды в `mods/`, а модели и расширяемое содержимое — в отдельной папке, которую можно изменять без пересборки JAR. Нужно поддержать множество персонажей и пользовательских пакетов.

## Решение

Агнор будет иметь собственную систему Content Packs, не зависящую от загрузчика модов. Предлагаемая структура:

```text
.minecraft/
└── mods/
    ├── agnor.jar
    ├── other-mods.jar
    └── agnor/
        ├── packs/
        ├── presets/
        ├── cache/
        └── logs/
```

Для dedicated server каталог расположен относительно установки сервера. Это собственный каталог Агнора, а не каталог модов, который Fabric автоматически обрабатывает как отдельный mod source.

Пакеты могут хранить определения персонажей, промты, модели, текстуры, анимации, эмоции, описания голосов и декларативные настройки. Пакет содержит манифест, ID и версию.

## Версионирование Content Pack и Addon API

Версия схемы/совместимости Content Pack относится только к декларативному формату пакета: manifest, Character Definitions, assets и declarative settings. Она не является версией executable Addon API.

Content Pack Loader (P4) не загружает исполняемый код. Публичный Addon API для runtime actions/events/goals стабилизируется отдельно после появления реальных Action/Goal/Event contracts (P7) и проходит compatibility freeze перед первым addon (P9). Реальные addon-модули появляются после этого (P10).

Это предотвращает зависимость Content Pack Manager от API, которого ещё нет, и не позволяет декларативному пакету стать механизмом обхода запрета на произвольный исполняемый код.

## Пассивные данные и поэтапное подключение consumers

Content Pack Loader валидирует и регистрирует prompts, voice descriptors, facial profiles и declarative behavior settings как данные. Он не вызывает Provider Gateway, не запускает LLM/TTS и не исполняет поведение при scan/activation пакета.

- Main Dialogue реализуется в P2, а внешние pack prompt templates разрешает после подключения Pack Loader в P4.
- Cognitive gnomes и Embedding начинают использовать соответствующие preset/prompt sources в P6.
- Bot planning data/preset подключается в P7.
- Voice descriptors/TTS assets и voice preset подключаются в P8.

Если соответствующего consumer/provider capability ещё нет, ресурс остаётся зарегистрированным с состоянием NOT_YET_ENABLED; это не блокирует загрузку независимых assets/definitions и не выдаёт ложный fallback за выполненную функцию. Host secrets и реальные credentials остаются вне Content Pack.

## Ограничения безопасности

- Не исполнять произвольный Java-код из пакетов.
- Проверять манифесты, пути, размеры, типы файлов, версии и checksum.
- Не включать API-ключи, данные миров и приватную память в пакеты.
- Не синхронизировать секретные серверные конфигурации клиентам.
- Для новых исполняемых интеграций использовать отдельный addon-мод с явным API.

## Открытые проверки

- Реальный ACL-denial и pack access policy: обычное создание каталогов уже подтверждено.
- Loader/reload в нестандартном gameDir: DataRoots custom gameDir уже покрыт unit-тестом, загрузчик пакетов ещё отсутствует.
- Формат ZIP-пакетов и защита от path traversal / чрезмерного распакованного размера.
- Политика передачи ресурсов клиентам: trusted download, ручная установка или отказ при несовпадении.
- Поддерживаемый pipeline моделей/анимаций и hot reload.

Создание `<gameDir>/mods/agnor/` на клиенте и сервере подтверждено bootstrap evidence 2026-11-04. Загрузка/валидация/sync пакетов и world-scoped private storage этим не подтверждены.
