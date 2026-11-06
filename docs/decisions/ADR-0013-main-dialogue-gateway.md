# ADR-0013: server-owned Main Dialogue Gateway v1

- **Статус:** Accepted, P2.1 Verified — [отчёт 008](../reports/2026-11-05-008-provider-gateway-verified.md). Первоначальный FAIL сохранён в отчёте 007.
- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Заменяет / заменено на:** нет.
- **Область:** Runtime / Security / Configuration / Provider contract.

## Контекст

Обновление P2.4: [ADR-0016](ADR-0016-cloud-mode-failure-policy.md) расширяет первоначальный Ollama-only/env-only контракт adapter OPENROUTER и безопасной ссылкой на внешний host file. Первоначальная P2.1 ниже сохраняется как исторический срез.

P2.1 вводит первый внешний LLM-вызов. P1 ownership/storage/lifecycle сохраняются. Нужны единая typed граница, server-only настройки и конечные ошибки без зависимости от будущих History/UI/gnomes.

## Решение

`ProviderGateway` принадлежит server session; единственное реализованное назначение — Main Dialogue. Нейтральные `ProviderRequest`/`ProviderResult` содержат correlation ID, ограниченные role/text messages и terminal error/text/elapsedMillis. Gateway не сохраняет History и не исполняет ответ модели. Дедупликация пользовательских запросов относится к будущему Dialogue Orchestrator P2.3.

`<server-save>/agnor/main-dialogue.properties` — version 1 immutable preset snapshot на startup; default disabled. Invalid file отключает Main Dialogue с безопасным сообщением, не останавливая NPC runtime. Изменения применяются после restart. Данные preset не передаются клиентам. Credentials разрешаются только на хосте из переменной окружения, имя которой указано в `secretEnv`; ключ не входит в preset. Resolver вызывается перед transport, значение не сохраняется в полях Gateway. HTTP request держит credential только на время transport. JVM не гарантирует физическое обнуление immutable strings после освобождения ссылок.

В P2.1 реализован только Ollama native adapter `/api/show` → `/api/chat`. Preflight не получает приватный prompt. Chat capability требует `completion`. Для thinking-моделей отключение разрешается лишь при `thinking.values` с boolean false либо, для старой версии metadata, при явной ветке `enable_thinking is false` в template. Иначе `UNSUPPORTED_CAPABILITY`, без inference. На chat отправляется top-level boolean `think:false`, когда поддержка установлена. Непустой thinking в ответе отклоняется. Режим non-streaming; streaming/tools/structured output/embedding/TTS не заявлены. В cloud P2.4 потребуется соответствующий adapter и реальный endpoint, не предположение о совместимости URL.

Bounded workers 1..4, queue 1..64; default 1/8. Admission не ждёт worker. Connect timeout 3s, request + общий deadline 0.1..180s (default 90s), включая очередь, preflight и body. Body subscriber отменяет transport при превышении byte limit; default 65536. Output ≤4096 Java chars, maxOutputTokens 1..2048 (default 256). HTTP redirects запрещены, HTTPS использует штатную TLS validation; plaintext допустим только для loopback literal/localhost. URL credentials/query/fragment и нестандартный base path отвергаются. Input ≤128 messages/24000 Java chars; context budget/token policy появится в P2.2.

`Call.cancel()` отменяет queued/running FutureTask и HTTP future. Stop закрывает admission, отменяет активные calls и вызывает неблокирующий HttpClient.shutdownNow. Timeout/cancel имеют один terminal outcome; callback вызывается после cleanup вне монитора Call. Retry policy = **0 автоматических inference retries**: uncertain call не отправляется заново. На три transient failure (network/timeout/unavailable/rate-limit) circuit открывается на 30s; новый запрос не выполняет HTTP. Успех сбрасывает health. State относится только к одному server-owned Main Dialogue gateway.

Provider error payloads/headers/exception causes отбрасываются. Классификация: configuration/authentication/authorization/rate-limit/timeout/network/unavailable/unsupported-capability/malformed-response/schema-violation/cancellation/busy. Метаданные результата — elapsedMillis и enum error; toString запросов/сообщений/результатов не содержит текст. Полные prompts и credentials не логируются. Нельзя считать это защитой от произвольного вредоносного upstream: до P2.3 нет доставки текстов игроку.

## Альтернативы и последствия

Прямой HTTP из команд/тика нарушил бы server authority и блокировал игру. Автоматический повтор chat после timeout мог бы повторно расходовать квоту и создать другой ответ. Оба варианта отклонены. Preflight каждого запроса добавляет одну metadata round-trip; пока это ограниченная цена явной capability validation без устаревшего cache. Бюджеты являются policy bounds, не измеренным performance обещанием.

## Проверка / доказательства

См. [отчёт 007](../reports/2026-11-05-007-provider-gateway-blocked.md). Controlled HTTP contract tests и сборка PASS. Реальный gateway → Ollama `0.40.14` → выбранная модель возвращает HTTP 500 / UNAVAILABLE: отсутствует `llama-server.exe`. Поэтому live test FAIL, P2.1 и P2 не завершены. Игровая интеграция Gateway, реальный текст модели, фактическое отсутствие thinking в успешном ответе и cloud **НЕ ПРОВЕРЕНЫ**.

Первичные API sources: [Ollama chat](https://docs.ollama.com/api/chat), [thinking](https://docs.ollama.com/capabilities/thinking). Для установленной версии дополнительно проверены реальные `/api/version`, `/api/show` и template.
