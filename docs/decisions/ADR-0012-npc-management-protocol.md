# ADR-0012: управление NPC через команды и ограниченный адресный протокол

- **Статус:** Accepted — реализация P1.4
- **Дата:** 2026-11-05
- **Заменяет / заменено на:** нет
- **Область:** Network / Permissions / UI
- **Основание:** ROADMAP P1.4; [ADR-0011](ADR-0011-npc-lifecycle.md)

## Решение

Минимальная пользовательская поверхность — `/agnor spawn`, `/agnor select [UUID]`, `/agnor delete [UUID]`, `/agnor relationship <UUID> <-100..100>`. Spawn создаёт встроенный dev definition у игрока и выбирает его. Select показывает публичное состояние NPC и собственное отношение игрока. Последний успешный spawn/select/relationship сохраняет selection только в текущем server connection; select/delete без UUID используют её. Disconnect/server stop очищает selection. После удаления выбранная NPC сбрасывается; выбор уже удалённой NPC не оживляет её.

Все команды и custom C2S запросы проходят `NpcManagement`, server-thread обработчик. UUID игрока и administrator level берутся из `ServerPlayerEntity`/соединения, WorldId — из session runtime; wire не содержит player/owner UUID. Permission context — это trusted sender + текущий runtime + текущие права, а не клиентская декларация. Перед действием после async inspect проверяется живое соединение/тот же runtime и заново проверяется право.

- Spawn разрешён игрокам; owner — фактический sender. Начальная политика ограничения — 8 active NPC на owner и 64 на save. Проверка в той же SQL transaction после проверки create replay; существующие записи не удаляются ради лимита.
- Delete разрешён owner или administrator с permission level ≥2, включая повтор. Расстояние для management delete владельца/admin не ограничивает доступ к своим данным.
- Select/relationship требуют той же dimension и расстояния ≤64 блоков. Каждый игрок читает/меняет только своё relationship record; administrator тоже не получает отношения другого игрока через этот API.
- Relationship v1 пока является явно простым состоянием для проверки взаимодействия/изоляции; оно не запускает cognition, LLM или affect.

Commands используют существующий Minecraft command transport; ответы модифицированному клиенту — тот же адресный snapshot, что у custom request. Vanilla client получает текстовое сообщение. UI — feedback с UUID/definition/own relationship/own flag; error text не содержит SQL, paths, содержимого config или exception text. Log обработчика — только фиксированные action/status codes. Обычные Minecraft login/command/chat logs среды этим ADR не заменяются.

## Wire v1

Каналы `agnor:npc_request` и `agnor:npc_snapshot` не связаны с bootstrap probe. Integer fields big-endian, UUID — два int64, no compression.

Request **ровно 65 bytes**: protocol int32, sequence int64, action uint8, WorldId UUID, NpcId UUID, operation ID UUID, affinity int32. Actions: 0 spawn, 1 select, 2 delete, 3 own relationship, 4 info. INFO read-only сообщает текущий WorldId и не требует предварительного WorldId. Другие действия отвергают несовпадение scope до SQL. Неизвестная версия/действие, отрицательный sequence, отсутствующий operation ID для create/delete и affinity вне -100..100 отклоняются до effect.

Snapshot **не более 256 bytes** (v1 при максимальных identifiers — 224): protocol, sequence/status, WorldId/NpcId, isOwner boolean, own affinity, definition ID и dimension ID (ASCII identifier, каждый ≤64 bytes), pose, runtime revision, deleted flag. Status codes: OK=0, VERSION_MISMATCH=1, INVALID=2, WORLD_MISMATCH=3, DENIED=4, BUSY=6, UNAVAILABLE=7, DELETED=8; 5 зарезервирован, сейчас не отправляется. Failure snapshots пустые, без private payload; INFO содержит только scope. Ответ отправляется только requester, broadcast не используется.

Один admitted request в полёте на connection, минимум 10 server ticks между admitted requests; BUSY не ставится в очередь SQL. Эти ограничения не являются измерением performance или полной защитой от произвольного packet flood. Общий IO queue bound остаётся из P1.3. Декодер request проверяет точный размер до allocation, response ограничивает размер/identifier lengths и отвергает truncated/trailing/invalid fields. Snapshot не принимает owner UUID или другие private relationship records.

Command operation IDs генерирует сервер. Custom clients передают UUID для безопасного replay create/delete по контракту P1.3. Create fingerprint включает owner/definition/initial pose; replay с изменённым payload отклоняется. Sequence — корреляция ответа, не authorization/dedupe key. Права authorise принятую операцию при серверной проверке; последующая смена роли не отменяет уже принятую SQL operation. Late callbacks проверяют connection/runtime, не доставляют данные в другое соединение/мир.

## Альтернативы и последствия

Отдельный графический редактор отложен: текущего потребителя обслуживают команды. Один общий relationship record был бы потерей player scope; player UUID из C2S позволил бы impersonation; broadcast snapshots раскрыли бы private state. Эти варианты отклонены. Числовые affinity и фиксированные caps — ограниченная политика P1, настраиваемая продуктовая конфигурация появится с потребителем, без введения будущих API заранее.

## Свидетельства

Unit tests codec bounds/semantics, owner/admin/range policy и owner cap/replay; игровые проверки двух игроков/двух миров и integrated restart — [отчёт 006](../reports/2026-11-05-006-phase1-management.md). Итоговый PASS фазы определяется фактическими результатами отчёта. Online identity, raw malicious packet injection, другие моды и installed release-JAR compatibility остаются отдельными проверками P9, без утверждения об их прохождении.
