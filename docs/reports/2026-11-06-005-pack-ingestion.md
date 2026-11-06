# Отчёт005: P4.1 Content Pack manifest и loader

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Done / P4.1 Verified
- Ветка: main; HEAD до/после: `85c5f850a0d23ef9942a3c1cfef880f0d01431fd`; без commit/push
- ADR: [0022](../decisions/ADR-0022-content-pack-schema-and-ingestion.md)

## Требование и исходное состояние

ROADMAP P4.1: schema/version/hash, безопасные paths/limits/signatures, provenance/rights, dependency/cycle/priority. P3 завершена отчётом004; её незакоммиченный diff сохранён. Loader ранее отсутствовал. Применимых AGENTS.md нет.

## Реализация

`src/main/java/dev/agnor/content/{ContentPack,PackJson,PackLoader,PackResolver}.java`: immutable passive snapshot, strict bounded JSON и directory/ZIP ingestion без disk extraction, hashes/signatures/contained GLB image references, exact dependency resolver и explicit higher-priority overrides. Источники не открываются повторно после validation. `docs/CONTENT_PACKS.md` описывает полный обязательный manifest и ограничения. Новое решение зарегистрировано ADR-0022.

## Проверки

`powershell -ExecutionPolicy Bypass -File scripts/dev.ps1 test`: PASS (94 tests total, 91 passed, 3 existing opt-in skipped). 8 новых contract tests покрывают valid directory/ZIP/custom path, byte immutability, unsafe paths/archive entries, schemas/duplicate/trailing/fractional JSON, checksums/undeclared executables/signatures, size/count/ratio, rights/refs, GLB URI, cycles/dependencies/overrides. Main/client compile PASS в этом же запуске.

Первый запуск: compileTestJava FAIL (изменяемая captured переменная теста), исправлено. Второй: 2 FAIL выявили применение pack ID validator к namespaced asset ID, исправлено; остальные отрицательные тесты повторно прошли с правильным asset validator. Финальный test exit0.

## Границы

Это полная P4.1, без активации/runtime/sync. Semantic importer/GPU readiness ещё не проверены и относятся к P4.2. ACL/disk exhaustion/враждебная concurrent подмена ancestor локального admin-owned каталога не проверены. Signature validation не заменяет полную декодировку PNG/GLB или проверку прав на внешние модели. Не объявляется multiplayer PASS.

Следующая задача — P4.2 stage/activation через существующий Registry. P4.2 до приёмки P4.1 не начиналась; P4 целиком не завершена.
