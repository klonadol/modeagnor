# Реестр архитектурных решений

Этот документ — краткий указатель. Полное обоснование, альтернативы, последствия и история изменений находятся в отдельных ADR.

| ADR | Тема | Статус | Краткое содержание |
|---|---|---|---|
| [ADR-0001](decisions/ADR-0001-platform.md) | Платформа | Accepted | Minecraft 1.21.1, Fabric, Java 21 |
| [ADR-0002](decisions/ADR-0002-host-authoritative-runtime.md) | Модель исполнения | Accepted | Хост — авторитетная сторона для ИИ, памяти и игровых действий |
| [ADR-0003](decisions/ADR-0003-content-packs.md) | Внешний контент | Accepted with validation | Пакеты вне JAR; точный путь и синхронизация требуют технической проверки |
| [ADR-0004](decisions/ADR-0004-memory-pipeline.md) | Память и гномики | Accepted with open details | Шесть хранилищ; Memorizer анализирует историю и мысли |
| [ADR-0005](decisions/ADR-0005-optional-integrations.md) | Интеграции | Accepted | Create и другие интеграции остаются необязательными |
| [ADR-0006](decisions/ADR-0006-character-visual-quality-and-physics.md) | Качество 3D и физика | Accepted target; renderer pending | Цель — stylized anime action-RPG и secondary physics уровня ZZZ; конкретный renderer выбирается после прототипа |
| [ADR-0007](decisions/ADR-0007-procedural-motion-engine.md) | Процедурное движение | Accepted direction; solvers pending | Семантические задачи, Whole Body Controller, IK, процедурная локомоция, host-side physics; клипы остаются строительным материалом |

## Как регистрировать решение

Каждое решение, меняющее платформу, границы модулей, хранение, сетевой протокол, безопасность, формат данных или пользовательский контракт, получает отдельный ADR.

ADR не заменяется удалением. При пересмотре создайте новый ADR, укажите связь с предыдущим и обновите здесь статус старого документа.

См. [шаблон решения](templates/DECISION_TEMPLATE.md) и [индекс документации](INDEX.md).