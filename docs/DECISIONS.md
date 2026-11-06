[ADR0026](decisions/ADR-0026-lore-context-admission.md): Accepted/Implemented/Verified P5.2 — единственный scoped budgeted context consumer.

[ADR0025](decisions/ADR-0025-lorebook-storage-and-sources.md): Accepted/Implemented/Verified P5.1 — scoped Lorebook schema4 и immutable pack/local sources.

[ADR-0024](decisions/ADR-0024-content-controls-and-opt-in-transfer.md): Accepted/Implemented/Verified P4.3 — command/chat control panel, owner/admin selection, exact-digest opt-in и bounded consented transfer. P4 Verified: [итог008](reports/2026-11-06-008-phase4-acceptance.md).

[ADR-0023](decisions/ADR-0023-content-generations-and-presentation.md): Accepted/Implemented/Verified P4.2 — atomic generations, stable NPC identity, contained client resources и pose invalidation.

[ADR-0022](decisions/ADR-0022-content-pack-schema-and-ingestion.md): Accepted/Implemented/Verified P4.1 — Content Pack schema v1, bounded ingestion, exact dependencies/explicit overrides и права.

# Реестр архитектурных решений

Этот документ — краткий указатель. Полное обоснование, альтернативы, последствия и история изменений находятся в отдельных ADR.

[ADR-0021: Pose keyframes/dialogue presentation](decisions/ADR-0021-pose-keyframes-and-dialogue-presentation.md) — Accepted / P3 Verified (отчёты003/004, budget gate PASS): bounded S2C sparse keyframes, client buffer/guard/fallback, same-UUID host producer, public allowlisted dialogue cues.

[ADR-0020: Scripted gait и secondary constraints](decisions/ADR-0020-scripted-gait-and-secondary-constraints.md) — Accepted implementation P3.4: stable-origin gait, pelvis/flat feet, fixed-step bounded spring tips, sphere/cone feasibility и chain-local reset; controlled acceptance — отчёт002.

[ADR-0019: Host pose/face pipeline](decisions/ADR-0019-host-pose-and-face-pipeline.md) — **Accepted / P3.3 Verified**: renderer-independent clips/mixer/FK/IK, bounded semantic task arbitration, независимые expression/blink/mouth/gaze, validated controlled fixture transport. Gameplay PoseSnapshot protocol остаётся P3.5.

[ADR-0018: NPC presentation adapter](decisions/ADR-0018-npc-presentation-adapter.md) — **Accepted / P3.2 Verified**: toolchain/API migration, `agnor:npc` с прежним UUID/Storage ownership, optional client renderer/fallback, lossless palette partition локальной модели.

[ADR-0017: Renderer capability spike](decisions/ADR-0017-renderer-capability-spike.md) — **Accepted / P3.1 negative drop-in result**: headless GemRender fixture PASS, exact Loom mismatch и client-only packaging; BlendLib v1 без morph runtime. Production renderer pending; следующий bounded adapter proof определён.

| ADR | Тема | Статус | Краткое содержание |
|---|---|---|---|
| [ADR-0001](decisions/ADR-0001-platform.md) | Платформа | Accepted | Minecraft 1.21.1, Fabric, Java 21 |
| [ADR-0002](decisions/ADR-0002-host-authoritative-runtime.md) | Модель исполнения | Accepted | Хост — авторитетная сторона для ИИ, памяти и игровых действий |
| [ADR-0003](decisions/ADR-0003-content-packs.md) | Внешний контент | Accepted with validation | Пакеты вне JAR; общий каталог проверен, loader/sync ещё не реализованы |
| [ADR-0004](decisions/ADR-0004-memory-pipeline.md) | Память и гномики | Accepted with open details | Шесть хранилищ; Memorizer анализирует историю и мысли |
| [ADR-0005](decisions/ADR-0005-optional-integrations.md) | Интеграции | Accepted | Create и другие интеграции остаются необязательными |
| [ADR-0006](decisions/ADR-0006-character-visual-quality-and-physics.md) | Качество 3D и физика | Accepted target; renderer pending | Цель — stylized anime action-RPG и secondary physics уровня ZZZ; конкретный renderer выбирается после прототипа |
| [ADR-0007](decisions/ADR-0007-procedural-motion-engine.md) | Процедурное движение | Accepted direction; solvers pending | Семантические задачи, Whole Body Controller, IK, процедурная локомоция, host-side physics; клипы остаются строительным материалом |
| [ADR-0008](decisions/ADR-0008-reuse-external-character-assets.md) | Внешние модели | Accepted | Готовые модели из интернета и пользовательских библиотек — приоритет; Blender используется для адаптации; права и синхронизация учитываются отдельно |
| [ADR-0009](decisions/ADR-0009-dependency-driven-plan.md) | Зависимости и критерии фаз | Accepted | P0 bootstrap; решения just-in-time; последовательные вертикальные срезы; частично пересматривает фазовые предписания ADR-0003–0008 |

ADR-0003–0008 сохраняют функциональные решения; прежние фазовые предписания частично заменены ADR-0009.

[ADR-0010: World-scoped storage](decisions/ADR-0010-world-scoped-storage.md) — **Accepted, P1.1–P1.2**: SQLite для первой NPC, одна БД на save, общий legacy serverData запрещён для приватных данных. Minimal Java adapter/config реализованы и проверены.

[ADR-0011: NPC lifecycle](decisions/ADR-0011-npc-lifecycle.md) — **Accepted, P1.3**: stable UUID, typed snapshots/relationships, schema v2, operation dedupe и tombstone; временный vanilla placeholder без NBT writer. Dedicated/integrated restart проверены.

[ADR-0012: Управление и private snapshots](decisions/ADR-0012-npc-management-protocol.md) — **Accepted, P1.4**: команды и общий C2S service, owner/admin policy, connection selection, bounded wire и адресные own relationship snapshots. Итоговые двухклиентские/двухмировые и integrated restart scenarios PASS; фаза P1 принята.

[ADR-0013: Main Dialogue Gateway](decisions/ADR-0013-main-dialogue-gateway.md) — **Accepted / P2.1 Verified** после восстановления Ollama, production text-only Gateway PASS (отчёт 008). Исторический blocker — отчёт 007. Cloud/file secret extension — ADR-0016.

[ADR-0014: Conversation History/Context](decisions/ADR-0014-conversation-history-context.md) — Accepted P2.2, schema v3, atomic History/event, canonical count и единый scoped budgeted context.

[ADR-0015: Dialogue ingress/private protocol](decisions/ADR-0015-dialogue-protocol.md) — Accepted P2.3, один server handler, commands/C2S, addressed chat, commit-before-delivery и reconciliation.

[ADR-0016: Cloud и отказные сценарии](decisions/ADR-0016-cloud-mode-failure-policy.md) — Accepted P2.4; OpenRouter, безопасная ссылка на host secret file, реальные local/cloud и failure tests, завершение P2.

## Как регистрировать решение

Каждое решение, меняющее платформу, границы модулей, хранение, сетевой протокол, безопасность, формат данных или пользовательский контракт, получает отдельный ADR.

ADR не заменяется удалением. При пересмотре создайте новый ADR, укажите связь с предыдущим и обновите здесь статус старого документа.

См. [шаблон решения](templates/DECISION_TEMPLATE.md) и [индекс документации](INDEX.md).
