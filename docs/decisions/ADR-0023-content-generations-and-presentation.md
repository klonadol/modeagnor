# ADR-0023: Атомарные content generations и identity NPC

- Дата: 2026-11-06, Asia/Vladivostok
- Статус: Accepted / Implemented; evidence P4.2 — отчёт006
- Дополняет ADR-0003/0018/0019/0021/0022.

`PackGeneration.stage` разрешает весь набор до публикации: ≤32 активных packs, ≤512MiB payload, schema/dependencies, CPU rig и capability STATIC/MOTION/PLACEHOLDER. Неверная rig metadata отвергает candidate; отсутствующие humanoid/face channels дают статический/нейтральный fallback. P3 MotionRig adapter переиспользуется с in-memory bytes, не вводится новый rig engine. `GLB` ingestion дополнительно ограничивает collection/accessor/byte bounds, hierarchy cycle/depth; PNG decoded total ≤128MiB RGBA/pack. Произвольные glTF/extensions не объявляются поддержанными renderer.

`ContentService` имеет одну IO worker/queue1 и owner-thread publication. Одновременно одна мутация, после stage — `PackStore` в `<save>/agnor/content/objects/<inventory-digest>.zip`, verified STORED ZIP и atomic current/previous pointers. Только затем одной owner-thread операцией заменяется generation и существующий DefinitionRegistry. Невалидный source или ошибка записи сохраняет активную generation. Identical inventory — no-op, предыдущий rollback slot сохраняется. Cache snapshots приватны и не попадают в JAR/Git; текущая версия не зависит от дальнейших изменений исходного каталога. Нет автоматического удаления исторических objects (локальный admin может чистить неиспользуемые объекты при остановленном server).

`CharacterDefinition` получает bounded prompt с совместимым четырёхаргументным constructor. Единственный ContextBuilder добавляет ≤2048 UTF-8 bytes style/personality внутри host policy. Активация сама inference не запускает. Voice/behavior JSON остаётся пассивным; consumers P6/P7/P8 не включаются.

Registry остаётся единственным источником definitions. `NpcRepository.redefine` идемпотентно меняет definition/revision существующего row, не создаёт entity/UUID/relationship/History namespace; schema DB v3 не меняется. Недоступный definition/revision после disable даёт безопасное имя/placeholder при load, не удаляет NPC. Смена definition/активной generation отменяет текущие dialogue jobs. Уже accepted History сохраняется с terminal cancellation.

NPC tracked presentation key содержит только `digest|definitionID|revision`, максимум256 chars; missing marker задаёт placeholder. Это public presentation metadata поверх vanilla entity tracker, не private memory DTO. Совпадение одного названия/версии недостаточно: client требует точный inventory digest. P3 host motion выбирает rig по NPC binding, имеет monotonic session sequence через смену generation. Client сбрасывает buffer при смене binding/rig, reload и disconnect. Нет client-authoritative pose path.

Client-only resource provider монтирует проверенные bytes как `agnor:content/<packId>/<path>`. Input GemRender resource hooks удаляются; только validated image URI получает заново вычисленный contained hook. Стабильные resource IDs позволяют GemRender reload disposal переиспользовать handles без накопления ID каждой версии. Старые in-memory resource pack instances отдают maps после close. Пока reload не завершён, внешний NPC показывает fallback. Ошибка reload восстанавливает предыдущую карту и выполняет восстановительный reload. Missing GPU backend оставляет существующий fallback.

Начальный local scan выполняется до первого Minecraft resource reload. Асинхронный CLIENT_STARTED reload пересекался с Quick Play integrated startup и создавал ожидание render thread; diagnostic stack сохранён, этот путь заменён. Последующие пользовательские reload — bounded IO → resource reload → publish.

API проверен по локальному Yarn/JAR и [ResourcePackManager](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/resource/ResourcePackManager.html), [AbstractFileResourcePack](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/resource/AbstractFileResourcePack.html). Единственный client mixin открывает provider set; dedicated не загружает client config.
