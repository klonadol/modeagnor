# ADR-0018: P3.2 client presentation и palette partition

- Дата: 2026-11-05
- Статус: Accepted; визуальная NPC/reload/restart проверены, fallback check в отчёте 014
- Область: P3.2, пересмотр tooling ADR-0001, дополнение ADR-0011/0017

## Решение

Сохранить Minecraft 1.21.1 / Java21 / Loader0.16.10 / Yarn1.21.1+build.3. Обновить Wrapper до Gradle9.7.1, Loom до1.16.3 и Fabric API до0.116.15+1.21.1: первые два необходимы для published GemRender artifact, API — для Flywheel1.0.6. Изолированный proof отчёта013 подтвердил выбранную матрицу. После миграции пересобрать AGNOR и выполнить ordinary tests, dedicated classloading и реальный client сценарий.

Ввести отдельный entity type `agnor:npc` на базе ArmorStandEntity. Существующий Storage/NpcRuntime остаётся единственным владельцем UUID/позиции/lifecycle; `shouldSave=false`, сохранения entity NBT нет. При восстановлении из SQL создаётся новый runtime entity того же UUID, но с зарегистрированным presentation type. Нового storage record/schema migration не требуется. Server code не ссылается на GemRender/GPU.

Клиент регистрирует GemRender adapter при наличии optional GemRender, иначе armor-stand fallback. Missing/loading/failed model resources сохраняют fallback того же entity. GemRender0.1.20/Flywheel1.0.6 — compile-only client dependencies; в локальной runtime проверке включаются отдельным флагом. Сторонние JAR и restricted models не bundled в AGNOR. Для воспроизводимой сборки подготовить pinned JAR скриптами P3.1; публичная dependency packaging ещё не заявлена.

## Модель и ограничение backend

Исходные локальные модели превышают `BoneAttributeCodec.MAX_JOINTS=256`: GemRender считает и node slots, и skin slots. Не удалять weighted bones ради лимита. Разбить mesh на части с <=110 используемых joints; сохранять все вершины/индексы/атрибуты/веса/material images. Каждая часть имеет flattened global bind transforms и оригинальные inverse binds. Скрипт `partition-local-model.py` сохраняет исходную hierarchy и source joint mapping в capability JSON; future host solver должен вычислять исходный rig и публиковать palettes по mapping каждой части. Это не упрощённый skeleton для физики и не разрешение двигать части независимо.

Первый dev descriptor использует семь частей Furina (max221slots). Исходный GLB не имеет morph targets; presentation capability — static bind pose, skeletal skinning доступен, expressions/clips отсутствуют. P3.3 должен решить face-capability на пригодной модели прежде чем объявлять готовый face pipeline. Публичный внешний pack loader и выбор модели UI — P4.

## Ограничения

Прозрачность/scale/orientation/швы и reference pose проверяются реальным кадром; компиляция не означает PASS. Сохранение UUID и отсутствие GPU на server — отдельные проверки. P3.3–P3.5 пока не начаты. Происхождение модели — пользовательский локальный файл без предоставленной лицензии, TEMPORARY_RESTRICTED в реестре; все производные только ignored local assets/resourcepack.
