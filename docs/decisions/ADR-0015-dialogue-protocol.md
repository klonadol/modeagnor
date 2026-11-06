# ADR-0015: server dialogue commands, private protocol и reconciliation

- **Статус:** Accepted / P2.3.
- **Дата:** 2026-11-05, Asia/Vladivostok.
- **Область:** Runtime / Network / UI / Privacy.

## Решение
`DialogueService` принадлежит Minecraft server session. Команды `/agnor say <text>`, `/agnor retry <request UUID>`, `/agnor history`, `/agnor cancel` и отдельный DialogueProtocol C2S входят в один handler. Команды используют существующую connection selection NpcManagement; новый графический редактор не вводится. Играющий пользователь получает thinking/request ID и адресный текст в обычном chat UI. DialoguePresentationIntent представлен typed enum Presentation LISTENING/THINKING/SPEAKING/ERROR в wire response; это текстовая семантика без motion/TTS.

Server берёт sender UUID из connection, WorldId из runtime, проверяет world/version/size/text/NPC/tombstone/dimension/distance ≤64 blocks. Никаких player/conversation/provider/credential fields от клиента. Разрешение говорить с nearby NPC соответствует существующей public select policy; private History отдельна для каждого игрока. После async inspect и до final commit повторяются connection/nearby/lifecycle проверки. Сервер никогда не broadcast-ит private DTO.

Wire v1 ограничен 4200 bytes C2S и 16600 S2C. Request содержит version, sequence, action, WorldId, NpcId, request UUID и text; SAY ≤1024 Java chars. Response содержит NPC/request/status, MessageId/message sequence/role/text и безопасный enum error. Decode проверяет UTF-8, размер, enum, trailing bytes. Failure DTO не содержит text, model metadata, prompt или exception text. Output literal Text не исполняется как команда.

На connection допускается одна выполняющаяся операция и минимум 10 ticks между admitted requests. Cancel активной операции допускается сразу. Same-ID in-flight ответ — PENDING, другой — BUSY. DB additionally сериализует conversation. Accepted inbound+event фиксируются до inference, один claim, затем единственный ContextBuilder → ProviderGateway. Final response+event фиксируются до доставки. RETRY читает прежний turn, не создаёт сообщение или второй inference; terminal failures/restart требуют нового явного SAY/request UUID, а не uncertain automatic retry.

NPC Runtime предоставляет typed History work на существующей bounded IO queue; нового параллельного хранилища или DB pool нет. Context assembly/SQL/HTTP отсутствуют на server tick. Disconnect/stop отменяют gateway call и терминализируют accepted job. Late result не отправляется другому connection; удаление NPC проверяется и SQL finalize. Если commit уже состоялся до disconnect, сохранённый финал можно получить reconciliation. Terminal storage failure логируется безопасно и не выдаётся за успешную доставку.

Client DialogueClient показывает только полученные addressed DTO. MessageId cache (1024 последних финала на connection) предотвращает повторное отображение final при retransmit; disconnect очищает cache. Явный history view повторно отображает запрошенные записи. После reconnect/eviction старое сообщение может быть показано по явному retry, но durable запись не дублируется. Это bounded presentation dedupe, не обещание exactly-once UI на произвольном интервале. Minecraft chat локально сохраняет собственный видимый текст в штатном клиентском chat log; серверный AGNOR diagnostics не содержит prompt/reply/ключи. Диагностические bundle с client chat log нельзя считать безопасными для автоматической публикации.

HistoryRecentN/contextBudgetBytes читаются из world main-dialogue.properties на startup; default 10/12000. History и reconciliation готового финала доступны даже при отключённом provider; новый SAY в таком состоянии отклоняется до accepted write. Invalid context policy отключает Dialogue, не NPC lifecycle. Stop отменяет Dialogue до provider и до drain существующей storage queue.

## Альтернативы и проверки
Отдельный command-only LLM path и broadcast chat отвергнуты. [Отчёт 010](../reports/2026-11-05-010-dialogue-path.md): реальные dedicated два игрока/restart, integrated/restart, private UI, request dedupe и atomic History/event counts. Управляемые timeout/disconnect/stale/cloud — P2.4. Normal JAR не содержит dev harness.
