package dev.agnor.check;

import com.google.gson.*;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.*;
import com.wf.gemrender.texture.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.nio.file.*;

/** Displays only server-baked matrices; it never evaluates clips or chooses a pose. */
public final class P33Client implements ClientModInitializer {
    int ticks;BlendScreen screen;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(++ticks==100){screen=new BlendScreen();client.setScreen(screen);}
        if(ticks==320){
            if(screen==null||screen.frames<10)throw new IllegalStateException("No rendered pose frames");
            try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(Path.of(System.getProperty("agnor.p33.root"),"pose-proof-"+System.getProperty("agnor.p33.stage","a")+".png"));}
            catch(Exception e){throw new RuntimeException(e);}
            System.out.println("P33_CLIENT_CAPTURE stage="+System.getProperty("agnor.p33.stage","a")+" frames="+screen.frames);client.scheduleStop();
        }
    });}
    static final class BlendScreen extends Screen {
        final Matrix4f[][][] poses;final String[] labels;final JsonArray targets,morphs,faceCenter;final String prefix;final float viewYaw;final boolean unlit;int frames;
        BlendScreen(){
            super(Text.literal("Host pose acceptance"));
            try{
                var path=Path.of(System.getProperty("agnor.p33.root"),"pose-frames-"+System.getProperty("agnor.p33.stage","a")+".json");
                var data=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var json=data.getAsJsonArray("frames");
                morphs=data.has("morphs")?data.getAsJsonArray("morphs"):null;
                faceCenter=data.has("faceCenter")?data.getAsJsonArray("faceCenter"):null;
                prefix=data.has("resourcePrefix")?data.get("resourcePrefix").getAsString():"models/dev";
                viewYaw=data.has("viewYaw")?data.get("viewYaw").getAsFloat():0;unlit=faceCenter!=null||(data.has("unlit")&&data.get("unlit").getAsBoolean());
                labels=new String[json.size()];for(int i=0;i<labels.length;i++)labels[i]=data.getAsJsonArray("labels").get(i).getAsString();targets=data.getAsJsonArray("targets");
                poses=new Matrix4f[json.size()][][];
                for(int f=0;f<poses.length;f++){
                    var parts=json.get(f).getAsJsonArray();poses[f]=new Matrix4f[parts.size()][];
                    for(int p=0;p<parts.size();p++){
                        var matrices=parts.get(p).getAsJsonArray();poses[f][p]=new Matrix4f[matrices.size()];
                        for(int i=0;i<matrices.size();i++){float[] v=new float[16];for(int k=0;k<16;k++)v[k]=matrices.get(i).getAsJsonArray().get(k).getAsFloat();poses[f][p][i]=new Matrix4f().set(v);}
                    }
                }
            }catch(Exception e){throw new RuntimeException(e);}
        }
        public void render(DrawContext context,int mx,int my,float delta){
            context.fill(0,0,width,height,0xff18212e);
            var models=new com.wf.gemrender.gltf.GemRenderGltfModel[poses[0].length];
            for(int i=0;i<models.length;i++){models[i]=GemRenderModels.get(Identifier.of("agnor",prefix+"/part-%03d.glb".formatted(i)));if(models[i]==null)return;}
            if(morphs!=null)com.wf.gemrender.render.MorphBuffer.getInstance().uploadAndBind();
            for(int f=0;f<poses.length;f++){
                int columns=faceCenter==null?(poses.length>4?3:poses.length):4,rows=faceCenter==null?(poses.length>4?2:1):2;
                float x=width*((f%columns)+.5f)/columns,y=height*((f/columns)+.5f)/rows;
                context.drawCenteredTextWithShadow(textRenderer,labels[f],(int)x,(f/columns)*height/rows+8,0xffffff);
                float scale=Math.min(width/columns*.70f,height/rows*.44f);
                float ground=(f/columns)*height/rows+height/rows*.89f;
                context.fill((int)(x-width/columns*.4f),(int)ground,(int)(x+width/columns*.4f),(int)ground+1,0xff536575);
                var transform=new Matrix4f().translation(x,ground,0).scale(scale,-scale,scale).rotateY(viewYaw+Float.parseFloat(System.getProperty("agnor.p33.view","0")));
                if(faceCenter!=null){
                    context.enableScissor((f%columns)*width/columns,(f/columns)*height/rows+22,((f%columns)+1)*width/columns,((f/columns)+1)*height/rows);
                    scale=width/columns*1.8f;
                    transform=new Matrix4f().translation(x,y+8,0).scale(scale,-scale,scale).rotateY((float)Math.PI)
                        .translate(-faceCenter.get(0).getAsFloat(),-faceCenter.get(1).getAsFloat(),-faceCenter.get(2).getAsFloat());
                }
                for(int p=0;p<models.length;p++){
                    if(poses[f][p].length!=models[p].jointCount())throw new IllegalStateException("Rig slot mismatch");
                    float[] block=null;
                    if(!models[p].morphs().isEmpty()){
                        block=new float[models[p].morphs().blockFloats()];
                        models[p].morphs().writeBlock(block,models[p].layout().nodeTable().newScratch());
                        for(int set=0;set<models[p].morphs().sets().size();set++){
                            int header=set*4,count=(int)block[header+1],base=(int)block[header+2];
                            if(morphs!=null){
                                if(count!=morphs.get(f).getAsJsonArray().size())throw new IllegalStateException("Morph slot mismatch");
                                for(int i=0;i<count;i++)block[base+i]=morphs.get(f).getAsJsonArray().get(i).getAsFloat();
                            }
                        }
                    }
                    // Face fixture's original materials are KHR_materials_unlit.
                    DirectRenderer.submit(models[p],poses[f][p],block,transform,unlit?-1:0xf000f0,
                        net.minecraft.client.render.OverlayTexture.DEFAULT_UV,-1,DirectPass.GUI,VariantUv.NONE,Paint.NONE,0,null);
                }
                if(faceCenter!=null){DirectRenderer.flush(DirectPass.GUI);context.disableScissor();}
            }
            DirectRenderer.flush(DirectPass.GUI);
            for(int f=0;f<poses.length;f++)if(!targets.get(f).isJsonNull()){
                float scale=Math.min(width/poses.length*.70f,height*.44f);var xyz=targets.get(f).getAsJsonArray();
                float rx=(float)(Math.cos(viewYaw)*xyz.get(0).getAsFloat()+Math.sin(viewYaw)*xyz.get(2).getAsFloat());
                int x=(int)(width*(f+.5f)/poses.length+rx*scale), y=(int)(height*.89f-xyz.get(1).getAsFloat()*scale);
                context.fill(x-3,y-1,x+4,y+2,0xffff5555);context.fill(x-1,y-3,x+2,y+4,0xffff5555);
            }
            frames++;
        }
        public boolean shouldPause(){return false;}
    }
}
