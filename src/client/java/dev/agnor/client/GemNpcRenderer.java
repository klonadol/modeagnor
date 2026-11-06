package dev.agnor.client;

import dev.agnor.character.NpcEntity;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.*;
import com.wf.gemrender.render.MorphBuffer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Client adapter. Absent/failed resources retain the same gameplay entity and UUID. */
final class GemNpcRenderer extends NpcPresentation.Fallback {
    GemNpcRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public void render(NpcEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        if(NpcMotionClient.asset(entity)!=null){renderMotion(entity,yaw,delta,matrices,vertices,light);return;}
        if(!entity.content().isEmpty()) {
            var binding=ContentClient.binding(entity);
            if(binding==null||binding.definition().model().isEmpty()){super.render(entity,yaw,delta,matrices,vertices,light);return;}
            var source=binding.pack().assets().get(binding.definition().model());
            var model=GemRenderModels.get(Identifier.of("agnor",binding.resourcePrefix()+source.path()));
            if(model==null){super.render(entity,yaw,delta,matrices,vertices,light);return;}
            Matrix4f pose=new Matrix4f(matrices.peek().getPositionMatrix()).rotateY((float)Math.toRadians(180-yaw));
            MorphBuffer.getInstance().uploadAndBind();
            DirectRenderer.submit(model,(com.wf.gemrender.gltf.GltfAnimation)null,0,pose,light,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,-1,DirectPass.LEVEL);return;
        }
        var models=new com.wf.gemrender.gltf.GemRenderGltfModel[7];
        for(int i=0;i<models.length;i++) {
            var resource=Identifier.of("agnor","models/dev/part-%03d.glb".formatted(i));
            if(net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(resource).isEmpty()){super.render(entity,yaw,delta,matrices,vertices,light);return;}
            models[i]=GemRenderModels.get(resource);
            if(models[i]==null) { super.render(entity,yaw,delta,matrices,vertices,light); return; }
        }
        Matrix4f pose=new Matrix4f(matrices.peek().getPositionMatrix()).rotateY((float)Math.toRadians(180-yaw));
        MorphBuffer.getInstance().uploadAndBind();
        for(var model:models) DirectRenderer.submit(model,(com.wf.gemrender.gltf.GltfAnimation)null,0,pose,light,
            net.minecraft.client.render.OverlayTexture.DEFAULT_UV,-1,DirectPass.LEVEL);
    }
    private void renderMotion(NpcEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light){
        long start=System.nanoTime();var asset=NpcMotionClient.asset(entity);var sample=NpcMotionClient.sample(entity);
        var palettes=sample.isPresent()?asset.palettes(sample.get().pose()):asset==NpcMotionClient.asset()?NpcMotionClient.neutral():asset.palettes(asset.neutral());
        var models=new com.wf.gemrender.gltf.GemRenderGltfModel[asset.parts().size()];var blocks=new float[models.length][];
        for(int p=0;p<models.length;p++){
            var model=GemRenderModels.get(Identifier.of("agnor",asset.prefix()+"/part-%03d.glb".formatted(p)));models[p]=model;
            if(model==null||model.jointCount()!=asset.parts().get(p).slots()){super.render(entity,yaw,delta,matrices,vertices,light);return;}
            if(!model.morphs().isEmpty()){
                var block=new float[model.morphs().blockFloats()];model.morphs().writeBlock(block,model.layout().nodeTable().newScratch());
                for(int set=0;set<model.morphs().sets().size();set++){
                    int header=set*4,count=(int)block[header+1],base=(int)block[header+2];
                    if(count!=asset.morphCount()){super.render(entity,yaw,delta,matrices,vertices,light);return;}
                    for(int i=0;i<count;i++)block[base+i]=sample.isPresent()?sample.get().morphs().get(i):0;
                }blocks[p]=block;
            }
        }
        var pose=new Matrix4f(matrices.peek().getPositionMatrix());
        if(sample.isPresent()){
            var s=sample.get();var entityPosition=entity.getLerpedPos(delta);
            pose.translate((float)(s.x()-entityPosition.x),(float)(s.y()-entityPosition.y),(float)(s.z()-entityPosition.z)).rotateY(s.yaw());
        }else pose.rotateY((float)Math.toRadians(180-yaw));
        MorphBuffer.getInstance().uploadAndBind();
        // Accepted Firefly materials use KHR_materials_unlit, as in the P3.3/P3.4 proofs.
        for(int p=0;p<models.length;p++)DirectRenderer.submit(models[p],palettes[p],blocks[p],pose,-1,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,-1,DirectPass.LEVEL,com.wf.gemrender.texture.VariantUv.NONE,com.wf.gemrender.texture.Paint.NONE,0,null);
        NpcMotionClient.rendered(System.nanoTime()-start);
    }
}
