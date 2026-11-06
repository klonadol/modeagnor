package dev.agnor.check;

import dev.agnor.client.*;
import dev.agnor.character.NpcEntity;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.minecraft.text.Text;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.google.gson.*;

final class P4SyncClient {
    final String role=System.getProperty("agnor.p4.role","A");final boolean b=role.equals("B"),integrated=System.getProperty("agnor.p4.stage","").equals("syncIntegrated");
    String phase="",failure="";int ticks,age,denyStage,privateReplies,disconnectTick,chosenTick;boolean sent,chosen,saying,reloaded,reloading,disconnected,connecting,done;long fromAccepted;UUID npc,world,request,denyToken;boolean versionRejected,restrictedRejected,optOutRejected;
    void install(){
        ClientPlayNetworking.unregisterGlobalReceiver(DialogueProtocol.Response.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(DialogueProtocol.Response.ID,(p,c)->c.client().execute(()->{
            var r=p.value();DialogueClient.receive(c.client(),r);privateReplies++;
            if(b){failure="foreign private reply";return;}
            if(r.status()==DialogueWire.Status.FINAL){var data=new JsonObject();data.addProperty("request",r.request().toString());data.addProperty("message",r.message().toString());P4Files.write("A-dialogue.json",data);}
            else if(r.status()!=DialogueWire.Status.ACCEPTED&&r.status()!=DialogueWire.Status.PENDING)failure="dialogue "+r.status()+":"+r.error();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    void tick(MinecraftClient client){
        if(P4Files.read("server-result.json")!=null){client.scheduleStop();return;}
        if(done)return;if(++ticks>5700){failure="deadline";result();return;}if(!failure.isEmpty()){result();return;}
        if(disconnected&&client.world==null&&ticks-disconnectTick>40&&!connecting){connecting=true;ConnectScreen.connect(new TitleScreen(),client,ServerAddress.parse("127.0.0.1:25586"),new ServerInfo("P4 reconnect","127.0.0.1:25586",ServerInfo.ServerType.OTHER),false,null);}
        if(client.player==null||client.world==null)return;
        var state=P4Files.read("state.json");if(state==null)return;String next=state.get("phase").getAsString();
        if(!phase.equals(next)){phase=next;age=0;sent=false;fromAccepted=NpcMotionClient.metrics().accepted();}age++;
        npc=UUID.fromString(state.get("npc").getAsString());world=UUID.fromString(state.get("world").getAsString());NpcEntity entity=null;
        for(var e:client.world.getEntities())if(e instanceof NpcEntity n&&n.getUuid().equals(npc)){entity=n;break;}if(entity==null)return;
        if(phase.equals("local")) {
            boolean correct=b?ContentClient.binding(entity)==null&&NpcMotionClient.sample(entity).isEmpty()&&PackClient.receivedBytes()==0:
                ContentClient.binding(entity)!=null&&NpcMotionClient.metrics().accepted()>fromAccepted+20;
            if(correct&&age>70){capture(client,"local");mark("local");}
        }else if(phase.equals("deny")) {
            var offers=PackClient.offers();if(offers.size()!=2)return;
            if(!sent&&age>15){sent=true;denyToken=UUID.randomUUID();ClientPlayNetworking.send(new PackProtocol.Request(new PackWire.Request(99,denyToken,PackWire.Action.LIST,"","",0)));}
            if(denyStage==0&&PackClient.lastStatus()==PackWire.Status.VERSION_MISMATCH&&denyToken.equals(PackClient.lastToken())){versionRejected=true;denyStage=1;}
            if(denyStage==1&&age>30){denyStage=2;denyToken=UUID.randomUUID();var offer=offers.get("firefly");ClientPlayNetworking.send(new PackProtocol.Request(new PackWire.Request(PackWire.VERSION,denyToken,PackWire.Action.FETCH,"firefly",offer.digest(),0)));}
            if(denyStage==2&&PackClient.lastStatus()==PackWire.Status.DENIED&&denyToken.equals(PackClient.lastToken())){restrictedRejected=true;denyStage=3;}
            if(denyStage==3&&age>45){denyStage=4;denyToken=UUID.randomUUID();var offer=offers.get("public");ClientPlayNetworking.send(new PackProtocol.Request(new PackWire.Request(PackWire.VERSION,denyToken,PackWire.Action.FETCH,"public",offer.digest(),0)));if(b)client.getNetworkHandler().sendChatCommand("agnor packs disable firefly");}
            if(denyStage==4&&PackClient.lastStatus()==PackWire.Status.DENIED&&denyToken.equals(PackClient.lastToken())){optOutRejected=true;denyStage=5;}
            if(versionRejected&&restrictedRejected&&optOutRejected&&PackClient.receivedBytes()==0&&age>70)mark("deny");
        }else if(phase.equals("download")) {
            if(b){if(entity.content().contains("public:companion")&&ContentClient.binding(entity)==null&&PackClient.receivedBytes()==0){capture(client,"download");mark("download");}return;}
            if(!sent&&age>15&&PackClient.offers().containsKey("public")&&PackClient.offers().get("public").allowed()){sent=true;if(!integrated)PackClient.fetch("public");}
            var offer=PackClient.offers().get("public");
            if(offer!=null&&ContentClient.matches("public",offer.digest())&&(integrated||PackClient.completed()>0)&&!chosen){
                chosen=true;chosenTick=ticks;client.getNetworkHandler().sendChatCommand("agnor select "+npc);
            }
            if(chosen&&ticks-chosenTick>20&&!saying){saying=true;client.getNetworkHandler().sendChatCommand("agnor characters");client.getNetworkHandler().sendChatCommand("agnor character public:companion");}
            if(entity.content().contains("public:companion")&&ContentClient.binding(entity)!=null&&age>90&&client.getOverlay()==null&&modelReady(entity)){capture(client,"download");mark("download");}
        }else if(phase.equals("dialogue")) {
            if(b){if(privateReplies==0&&age>50)mark("private");return;}
            if(!sent&&age>20){sent=true;request=UUID.randomUUID();ClientPlayNetworking.send(new DialogueProtocol.Request(new DialogueWire.Request(DialogueWire.VERSION,0,DialogueWire.Action.SAY,world,npc,request,"Кратко представься и упомяни звёзды.")));}
        }else if(phase.equals("reconnect")&&b) {
            if(!disconnected&&age>20){disconnected=true;disconnectTick=ticks;client.getNetworkHandler().getConnection().disconnect(Text.literal("P4 reconnect fixture"));return;}
            if(disconnected&&connecting&&PackClient.offers().containsKey("public")&&PackClient.receivedBytes()==0&&ContentClient.binding(entity)==null){mark("reconnect");}
        }else if(phase.equals("recover")) {
            if(!b&&!sent&&age>20){sent=true;client.getNetworkHandler().sendChatCommand("agnor character firefly:companion");}
            if(b&&!reloading) {
                reloading=true;CompletableFuture.runAsync(()->{try{Path root=P4Files.ROOT.getParent();Files.copy(root.resolve("clientA/mods/agnor/packs/firefly/manifest.json"),root.resolve("clientB/mods/agnor/packs/firefly/manifest.json"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new CompletionException(e);}})
                    .whenComplete((v,e)->client.execute(()->{if(e!=null){failure="manual copy "+e;return;}ContentClient.reload().whenComplete((ok,error)->client.execute(()->{if(error!=null)failure="manual reload "+error;else reloaded=true;}));}));
            }
            if(entity.content().contains("firefly:companion")&&ContentClient.binding(entity)!=null&&NpcMotionClient.metrics().accepted()>fromAccepted+20&&(!b||reloaded)&&age>100&&client.getOverlay()==null){capture(client,"recover");mark("recover");result();}
        }
    }
    void mark(String phase){if(P4Files.read(role+"-"+phase+".json")==null)P4Files.mark(role+"-"+phase+".json","PASS");}
    boolean modelReady(NpcEntity entity){var binding=ContentClient.binding(entity);if(binding==null)return false;var asset=binding.pack().assets().get(binding.definition().model());return asset!=null&&com.wf.gemrender.asset.GemRenderModels.get(net.minecraft.util.Identifier.of("agnor",binding.resourcePrefix()+asset.path()))!=null;}
    void capture(MinecraftClient client,String name){if(Files.exists(P4Files.ROOT.resolve(role+"-"+name+".png")))return;try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(P4Files.ROOT.resolve(role+"-"+name+".png"));}catch(Exception e){throw new RuntimeException(e);}}
    void result(){done=true;var result=new JsonObject();result.addProperty("status",failure.isEmpty()?"PASS":"FAIL");result.addProperty("failure",failure);result.addProperty("receivedBytes",PackClient.receivedBytes());result.addProperty("completed",PackClient.completed());result.addProperty("privateReplies",privateReplies);P4Files.write(role+"-result.json",result);System.out.println("P4_SYNC_CLIENT "+role+" "+result);}
}
