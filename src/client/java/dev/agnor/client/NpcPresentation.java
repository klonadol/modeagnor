package dev.agnor.client;

import dev.agnor.character.NpcEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class NpcPresentation {
    public static void install() {
        if (FabricLoader.getInstance().isModLoaded("gemrender")) {
            EntityRendererRegistry.register(NpcEntity.TYPE, GemNpcRenderer::new);
        } else EntityRendererRegistry.register(NpcEntity.TYPE, Fallback::new);
    }
    static class Fallback extends EntityRenderer<NpcEntity> {
        final ArmorStandEntityRenderer fallback;
        Fallback(EntityRendererFactory.Context context) { super(context); fallback=new ArmorStandEntityRenderer(context); shadowRadius=.3f; }
        @Override public Identifier getTexture(NpcEntity entity) { return fallback.getTexture(entity); }
        @Override public void render(NpcEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
            fallback.render(entity,yaw,delta,matrices,vertices,light);
        }
    }
}
