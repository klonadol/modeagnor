# Отчёт008: итоговая приёмка P4

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P4 Verified в границах ROADMAP
- main; HEAD до/после `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`; без commit/push

## Последовательная приёмка

| Задача | Завершённый результат и evidence |
|---|---|
| P4.1 | Strict schema/API1, directory/ZIP limits/paths/signatures/SHA-256/provenance/rights, exact dependencies/cycles/explicit overrides — [005](2026-11-06-005-pack-ingestion.md), ADR0022 |
| P4.2 | Stage→snapshot→atomic Registry publication, enable/disable/reload/rollback, stable NPC identity, CPU rig capability/static/placeholder fallback, client resource binding/reload — [006](2026-11-06-006-pack-activation.md), ADR0023 |
| P4.3 | Local dirs/command-chat UI/выбор, exact version+digest, reconnect, explicit admin/client opt-in, bounded permitted transfer, local-only denial — [007](2026-11-06-007-pack-controls-sync.md), ADR0024 |

Каждая задача закрывалась до начала следующей. Существующий незакоммиченный P3 diff сохранён; никакие предыдущие изменения не откатывались. Новые зависимости не добавлялись. Addon executable API не вводился.

## Интеграционный результат

Внешний definition меняет отображение/имя/personality существующей NPC без JAR rebuild, а переключение обратно сохраняет UUID/owner/position/relationships/History. Действующий Main Dialogue использует pack prompt: реальная Ollama представилась новым именем в private response. Недоступная клиенту модель не блокирует server activation и диалог.

Corrupt source reload и simulated filesystem commit failure не уничтожают предыдущую generation. Dedicated restart с испорченным исходным manifest восстанавливает cached last-known-good. Disable→placeholder→rollback восстанавливает model/texture и новые authoritative pose frames. Integrated и dedicated имеют разные WorldId.

Policy воспроизводима двумя клиентами: A получил ровно63040bytes public original CC0 fixture, B отказался и получил0bytes; restricted Firefly не передавалась вообще (0). Wrong version/нет opt-in/нет прав отклонены. B same-version/different-digest сначала fallback, reconnect без auto-download, затем local manual copy/reload вернул Firefly. Actual PNG пройдены после ожидания конца overlay/model readiness.

## Итоговые проверки

- `powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 build`: PASS,105tests,102PASS/3opt-in skipped,0failures/errors.
- Dedicated P4.2 activation/restart + P4.3 two clients, integrated P4.2/P4.3: PASS; ограничения/failed attempts сохранены в006/007.
- Последний loader/resource/capability runner: Firefly LOCAL_ONLY/MOTION24assets и public ALLOWED/STATIC2assets PASS.
- JAR `build/libs/agnor-0.1.0-dev.jar`:200entries; нет моделей/PNG/DB/test harness, nested только sqlite-jdbc. Main не импортирует client/GPU. Это dev build, не public release.
- DB integrity/FK: оба synthetic мира PASS. Dedicated:1NPC,1relationship affinity23,2final dialogue turns/4messages/4events; integrated:1NPC,1relationship23,1turn/2messages/2events. Dedicated UUID `364608a1-00e9-48e6-92b2-473871811858` совпадает со стартом P4.2. Evidence `build/p4-check/final-{audit,storage-audit}.json`.
- `git diff --check`: PASS. Credential-prefix false positives проверены как неизменённые baseline redaction/fake fixture; полная OpenRouter key signature не найдена. Credentials/личные worlds не копировались.

## Ограничения и продолжение

Поддерживается manifest v1 и проверенный P3 GLB/PNG/CPU metadata adapter. Embedded textures предварительно экспортируются в declared PNG, неизвестные required extensions запрещены; renderer остаётся optional. Full cloth/MToon/все риги/все shaders не обещаются. Restricted assets/производные только локально и в ignored synthetic evidence; JAR их не содержит. Voice/behavior descriptors остаются пассивными до своих consumers. UI — bounded clickable command/chat panel. Sync≤16MiB, отдельный более строгий предел, чем local ingestion. Server current/previous snapshots сохраняются; исторические cache objects требуют admin maintenance при остановке. Повреждение private cache на диске не приводит к молчаливому reset — startup отказывает, нужен restore валидного snapshot.

Релизная матрица/online authentication/soak и правовой аудит публичных ассетов относятся к P9/финальному asset cleanup; не входят в PASS этой фазы. Итоговый путь и команды — [CONTENT_PACKS.md](../CONTENT_PACKS.md), стенд — [p4Check README](../../src/p4Check/README.md).

**P4 завершена. Следующая P5 (Lorebook/управляемая память) не начиналась.**
