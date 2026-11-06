# ADR-0016: OpenRouter adapter, host file secret reference и отказные сценарии P2

- **Статус:** Accepted / P2.4 Verified.
- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Дополняет:** ADR-0013; изменяет только прежнее ограничение Ollama-only/env-only.
- **Область:** Provider / Security / Configuration / Runtime.

## Решение
Main Dialogue preset v1 получает optional `adapter=OLLAMA|OPENROUTER`, `secretFile`, `secretSection`. Default OLLAMA сохраняет совместимость старых properties. Endpoint — origin без query/credentials/path; transport adapter добавляет собственный известный путь. Конструктор прежнего preset остаётся Ollama overload. Destination по-прежнему только Main Dialogue; нет других AI consumers.

OpenRouter adapter выполняет metadata-only GET `/api/v1/models/<model>/endpoints`, проверяет exact ID, text input/output и наличие endpoints. Явно mandatory reasoning отвергается. Далее POST `/api/v1/chat/completions` с messages, stream:false, max_tokens, reasoning.enabled:false. Не отправляются tools, player UUID, arbitrary URLs или параметры будущих подсистем. Ответ обязан иметь один assistant choice, finish_reason=stop, bounded text, без tools/reasoning. Length/partial/invalid shape отклоняются. Проверенная модель — `google/gemini-2.5-flash-lite`; совместимость всех OpenAI-compatible сервисов не заявлена. Ollama и OpenRouter используют общий bounded transport/deadline/cancel/error normalization, без автоматического retry.

Для исходного host credentials file используется только явно выбранная именованная секция OpenRouter. `HostSecrets` открывает исходный файл read-only на worker, ищет точный нормализованный label и читает следующий непустой ключ; не перебирает credentials других провайдеров для авторизации. File ≤64 KiB, absolute path, env/file взаимоисключаются. File credential разрешён только для HTTPS origin `openrouter.ai`. Resolver не кеширует keys; preset/toString/DTO содержат только безопасный status, не credential. Java/HTTP временно держат string в памяти запроса; физическое zeroization JVM не обещается. Gradle env/arguments и project fixtures не получают реальные ключи. Secret file не изменяется.

Redirects запрещены, TLS штатно валидируется, response bodies ограничены. Provider error payloads/exception causes не выходят наружу. Даже успешный payload, содержащий используемый credential, отвергается как SCHEMA_VIOLATION. Новые unit tests используют только искусственный ключ. Проверенный реальный ключ не записывается в отчёты, код, настройки, tests, Git или logs.

Streaming не заявлен ни одним adapter Gateway; underlying API capability не означает его активацию. Полный текст фиксируется только один раз после validation. Retry policy=0; uncertain failure остаётся terminal, новое сообщение требует явного нового request ID. Cancel/timeout закрывают transport; disconnect/stale recipient не получает late response. Accepted inbound остаётся canonical record/event независимо от исхода provider.

## Проверки и ограничения
[Отчёт 011](../reports/2026-11-05-011-phase2-complete.md): реальный OpenRouter Gateway и реальный игровой cloud dialogue; controlled API errors/shape/secret echo; dedicated timeout со server tick advance=40, stale/cancel/disconnect, History/event accounting. Local real Ollama и два игрока/restart — отчёты 008–010. Нельзя считать проверенными все модели/провайдеры, online authentication, forced process death во время commit, физические disk/ACL failures, длительную нагрузку и streaming.

Первичные источники: [OpenRouter Chat API](https://openrouter.ai/docs/api-reference/overview), [reasoning controls](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens). Реальная metadata selected model проверена отдельно перед inference. Endpoint fallback самого OpenRouter является поведением внешнего сервиса, не автоматическим повтором Gateway.
