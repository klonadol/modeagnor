package dev.agnor.character;

import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.world.World;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

/** Presentation type; UUID and saving remain owned by NpcRuntime/Storage. */
public class NpcEntity extends ArmorStandEntity {
    private static final net.minecraft.entity.data.TrackedData<String> CONTENT=net.minecraft.entity.data.DataTracker.registerData(NpcEntity.class,net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {super.initDataTracker(builder);builder.add(CONTENT,"");}
    public void content(String key) { if(key.length()>256)throw new IllegalArgumentException("Content binding limit");getDataTracker().set(CONTENT,key); }
    public String content() {return getDataTracker().get(CONTENT);}
    public static final EntityType<NpcEntity> TYPE = Registry.register(Registries.ENTITY_TYPE,
        Identifier.of("agnor", "npc"), EntityType.Builder.<NpcEntity>create(NpcEntity::new, SpawnGroup.MISC)
            .dimensions(.6f, 1.8f).maxTrackingRange(10).build("agnor:npc"));
    public NpcEntity(EntityType<? extends ArmorStandEntity> type, World world) { super(type,world); }
    public NpcEntity(World world) { this(TYPE,world); }
    public static void register() { FabricDefaultAttributeRegistry.register(TYPE, createLivingAttributes()); }
    @Override public boolean shouldSave() { return false; }
}
