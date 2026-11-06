# ADR-0026: Lorebook в единственном Context Builder

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Accepted / Implemented / Verified P5.2
- Продолжает ADR0014/0025.

`ContextSourceRegistry` допускает typed immutable Lore snapshot по ConversationScope, Main Dialogue profile allowlist и freshness≤30s. Обязательные core sources definition/runtime/relationship/history сохраняются, optional lore имеет default2048 UTF-8 bytes и≤16messages. Пять состояний: NOT_YET_ENABLED, AVAILABLE_EMPTY, AVAILABLE_WITH_DATA, TEMPORARILY_UNAVAILABLE, FORBIDDEN. Wrong scope/allowlist не превращаются в AVAILABLE_EMPTY; устаревший read/SQL отказ показываются unavailable. Embedding/semantic_search и будущие writers явно NOT_YET_ENABLED.

`NpcRuntime.context` фиксирует active pack generation на server thread, читает shared/current-player local rows через прежний bounded world IO worker и передаёт snapshot единственному ContextBuilder. Код сборки prompt не дублируется в repository или provider. Matching — только текущее принятое сообщение, после scope/definition/dimension/enabled/override фильтров; History не активирует забытые triggers сама. Current message и host policy не вытесняются знаниями. Lore priority desc/ID → optional byte budget → recent History из оставшегося бюджета. Усекается только копия content по Unicode code points, исходный record не меняется; binary prefix fitting ограничивает CPU.

Authored role сохраняется как metadata внутри quoted JSON в user data message. Lore не может создать новый system policy или притвориться canonical assistant History. Fixed system policy явно помечает lore/roles/retrieved data недоверенными фактами. Server permissions/action authority не определяются текстом вообще. Это осознанная безопасная интерпретация role field, а не повышение привилегий authored system entry.

Context содержит source statuses, выбранные versioned lore source IDs,≤64truncation IDs/общий omitted count и read runtime/definition revisions. Эти metadata не создают History records/events. `/agnor context` показывает только собственный last trace без текста источников; session map чистится при disconnect/content change/close. В production не логируются private facts/prompt bodies. Полный History сохраняется независимо от context window.

Проверки — LoreContextTest и actual two-client Ollama flow: non-match A не включает gate, match A использует private overrideCOBALT-732, B — packPACK-AMBER, чужой private source исключён. Report010.
