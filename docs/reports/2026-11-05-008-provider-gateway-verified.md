# Отчёт 008: P2.1 — Gateway проверен на реальной текстовой Ollama

- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Статус:** Done — P2.1. P2 целиком ещё не завершена.
- **Ветка / HEAD до:** main / `2d24d2b`; HEAD после — коммит этого отчёта.
- **ADR:** [0013](../decisions/ADR-0013-main-dialogue-gateway.md).

## 1. Цель и исходное требование
Продолжить P2.1 после восстановления установленной Ollama, затем последовательно идти по Roadmap. Подтвердить production Gateway, а не только launcher/API доступность.

## 2. Исходное состояние
Чистое рабочее дерево, прежняя реализация Gateway и controlled tests сохранены. Проверены актуальные INDEX/ROADMAP/PROJECT_MAP, Blueprint provider/history/context/outbox, архитектура, действующие ADR/реестр и проектный skill. P1 code/evidence подтверждены ранее, её дорогая игровая матрица не повторялась. Внешнее восстановление Ollama закончено отдельной работой: полная 0.40.14 и текстовый GGUF с SHA-256 `1f884aa56aacd2bbbb2e4b7abaead2b1102b74a9c75a23fc33dab916813a384d`, без projector. Ремонтный отчёт: `C:/Users/User/.ollama/repair-backups/2026-11-05/repair-report.md`.

## 3. Выполненные действия
Default disabled preset и opt-in live check переведены на `ternary-bonsai-text-20k:latest`. Старый ignored FAIL файл сохранён отдельно как `build/p2-evidence/ollama-gateway-blocked-007.txt`. Прочитаны актуальные metadata: completion/thinking/tools, без vision; template явно поддерживает enable_thinking=false. Реальный production ProviderGateway отправил system+user chat с think:false/stream:false и получил валидный финальный ответ.

## 4. Изменённые файлы
`provider/DialoguePreset.java`, `provider/ProviderLiveCheck.java` (test), README/default model example, ADR-0013 и status docs. HTTP контракт и архитектура не менялись.

## 5. Решения
Новое ADR не требуется: действующий контракт сохранён. Активен только Main Dialogue. Автоматических inference retries нет; health/circuit/queue/deadline/cancellation остаются по ADR-0013.

## 6. Проверки и результаты
`gradlew.bat test providerLive --console=plain` с JDK 21 и repo-local GRADLE_USER_HOME: **BUILD SUCCESSFUL**, 20s. Обычная suite: 30 tests PASS, 1 opt-in skipped. Отдельный providerLive: 1 PASS. Production Gateway: error=NONE, elapsedMillis=5468, реальный ответ `Yes, I can hear you perfectly!`. Ответ без reasoning, capability/schema validation пройдены. Источник веса — единственный текстовый GGUF, без vision adapter.

Controlled tests текущего Gateway проверяют HTTP contract/secret resolver, auth/rate/server failures, malformed/size/partial/thinking, bounded queue/deadline/cancel/close, unsupported capability/config/network и circuit breaker. Реальный внешний сценарий P2.1 — Gateway → preflight → inference → normalized final reply — подтверждён.

## 7. Ошибки и диагностика
Прежний missing-runner blocker устранён восстановлением среды; исторический отчёт 007 не переписан как PASS. Новых дефектов HTTP-контракта в этом прогоне не обнаружено.

## 8. Ограничения
**НЕ ПРОВЕРЕНО в этой задаче:** игровой dialogue UI/History/context/privacy/restart и cloud, поскольку их consumers ещё не реализованы. GUI launcher и direct API сами по себе не использованы как замена production Gateway test. Streaming не заявлен.

## 9. Итог
P2.1 завершена в границах Roadmap; можно переходить к P2.2. P3 не начинать. Следующий срез — typed History и единый Context Builder; history commit обязан быть атомарным с минимальным event record с первой записи, даже до UI P2.3.

## 10. Evidence
`build/p2-evidence/ollama-gateway.txt`, `build/test-results/providerLive/TEST-dev.agnor.provider.ProviderLiveCheck.xml`, `build/test-results/test/TEST-*.xml`. Только synthetic test prompt/reply; secrets не использовались и не сохранялись.
