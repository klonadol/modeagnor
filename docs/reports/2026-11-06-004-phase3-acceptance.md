# Отчёт004: итоговая приёмка P3

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P3 Verified в зафиксированных границах
- Ветка main, без commit/push

## Пределы перед контрольным замером

Предыдущие P3.5 timings — диагностические. Чтобы выполнить правило ROADMAP о бюджете до приёмочного замера, до нового запуска `p35-budget-*` фиксируются следующие пределы. Сцена: Minecraft1.21.1, GTX1080, 1280×720, два одновременных клиента, без Sodium/Iris, тот же Firefly rig/18parts/70morphs/10secondary chains, 100ticks warm-up и 140ticks measurement для0/1/5NPC. Геометрия и качество не уменьшаются.

| Метрика | 1 NPC | 5 NPC | Основание |
|---|---:|---:|---|
| Host motion CPU p95 / tick | ≤5ms | ≤12.5ms | 10% / 25% от50ms server tick |
| Pose payload / viewer | ≤32KiB/s | ≤160KiB/s | 10Hz независимые keyframes; без Minecraft framing |
| Whole-world GPU pass p95, каждый клиент | ≤16.67ms | ≤16.67ms | Бюджет одного world pass при60Hz; это не full-frame FPS и не isolated skinning |
| Whole-NPC motion failures | 0 | 0 | Ошибка одной chain учитывается отдельно |

Дополнительно требуется baseline GPU p95≤16.67ms, ≥100совпадающих authoritative frames, четыре dialogue states, private reply только A, retry без повторного gesture, reconnect/cache reset, rig mismatch fallback/recovery и stale rejection. Число chain-local resets фиксируется как ограничение профиля, а не скрывается в performance PASS. LLM budgets не меняются: используется уже принятый P2 provider path.

## Результат

Контрольный multi PASS: `p35-budget-server-final.log`(2m38s), `p35-budget-client-{A,B}-final.log`; архив `build/p35-check/evidence/budget-verified`, машинная сверка `budget-verdict.json` PASS. Предварительные пределы выше не изменялись после запуска.

| NPC | Host CPU mean/p95 ms | Payload KiB/s/viewer | World GPU p95 A/B ms |
|---|---|---|---|
|0|0.035/0.056|0.00|0.152/0.162|
|1|1.229/1.667|23.35|3.175/3.166|
|5|5.141/7.812|116.79|1.307/1.309|

479shared authoritative frames, оба клиента увидели NEUTRAL/LISTENING/THINKING/SPEAKING. Реальная Ollama final/retry: A3private responses,B0. B cacheCleared/reconnected/hashFallback/recovered=true,40wrong-rig rejects/10stale. Whole-NPC failures0;15chain-local resets/2606steps, включая11в измерительных окнах. Пределы CPU/GPU/payload соблюдены без уменьшения18parts/70morphs/10chains. GPU немонотонен из-за коротких окон/прогрева/двух contexts; это whole-world pass, не isolated skinning или full-frame FPS. Один A render call на границе BASELINE/ONE учтён как переход окна, не как отдельная baseline NPC.

Первая бюджетная попытка завершилась FAIL на dialogue UNAVAILABLE: compatibility helper ранее отключил dialogue. Архив `evidence/budget-dialogue-disabled` и исходные `p35-budget-*.log` сохранены. Перед финальной попыткой выполнен `prepare-p35-check.ps1 -Dialogue`; server Gradle preflight теперь требует enabled=true для multi. Производственный provider не менялся, отказ не засчитан как успешная проверка.

После успешного Java сценария B Gradle завершился exit1: `FileAlreadyExistsException` при одновременной записи общего `build/reports/problems/problems-report.html`. Server/A wrapper exits0; B fresh JSON/PNG и server authoritative trace подтверждают gameplay/budget PASS. Это отдельный сбой формирования Gradle HTML, а не успешный B wrapper exit. Для следующих concurrent запусков команды README используют `--no-problems-report`, наличие опции подтверждено CLI Gradle9.7.1. Повторять неизменённый gameplay только ради HTML не требуется.

## Последовательная приёмка фазы

| Задача | Итог и evidence |
|---|---|
|P3.1|Renderer сравнение, GPU palette/morph и GPU-free headless host proof: отчёты012/013, ADR0017|
|P3.2|Та же сохраняемая NPC UUID, model/material/partition/reload/restart/fallback: отчёт014, ADR0018|
|P3.3|Controlled clips→IK→whole-body→face/eyes, затем общий Firefly rig: отчёт2026-11-06-001, ADR0019|
|P3.4|Scripted-root gait, затем bounded10chains/91segments/4proxies, front/side/three-quarter: отчёт002, ADR0020|
|P3.5|Versioned host pose→2clients/integrated, real dialogue public cues, reconnect/mismatch/stale, measured budgets и selected Sodium/Iris/active shader: отчёт003, ADR0021 и этот контрольный запуск|

Выбранная compatibility matrix: MC1.21.1/Loader0.16.10/API0.116.15/GemRender0.1.20/Flywheel1.0.6; дополнительно Sodium0.6.13+mc1.21.1, Iris1.8.8+mc1.21.1 без pack и с Complementary Reimagined r5.5.1. Активный pack подтверждён API и просмотренным A-FIVE PNG, native GemRender shader path без fallback. Failed dev remap/dependency attempts сохранены в отчёте003.

Обычный `gradlew build` PASS27s:86tests,83PASS/3opt-in skipped,0failures/errors. JAR audit:63motion classes, без моделей/текстур/Gem/Flywheel/Iris/fixture; nested только SQLite. Common не импортирует client/GPU. `git diff --check` PASS. Commit/push не выполнялись.

## Границы завершения

P3 завершена как фаза проверенного motion/render/dialogue pipeline на локальных dev assets. Для включения используется явный local metadata property на host и client; внешние Content Packs/выбор модели — следующая P4, не начата. Без property остаётся P3.2 static adapter, missing backend/resources сохраняют same-entity fallback. Restricted geometry/textures не распространяются.

[Firefly visual limits](../assets/FIREFLY_VISUAL_LIMITS.md): ограниченные sphere/tip contacts, hard folds/overlap тонкой одежды, часть цепочек static, chain-local resets. Full cloth/MToon/outline/все shaderpacks не заявлены. Navigation P7, affect P6 и audio/phoneme lipsync P8 не реализованы этой фазой. P3 не является приёмкой релизного качества P9.

[Поиск Miyabi](../assets/MIYABI_MODEL_SEARCH.md) завершён отдельно до P3.4: primary rigs и clothing toggles найдены, несколько сменных комплектов не подтверждены; новая модель не скачана/не активирована. Пользовательская коллекция сохранена по пути `C:/Users/User/Documents/Codex/AGNOR/assets/models/разное`.
