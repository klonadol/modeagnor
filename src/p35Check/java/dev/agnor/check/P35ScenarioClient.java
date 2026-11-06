package dev.agnor.check;

import dev.agnor.client.*;
import dev.agnor.character.NpcEntity;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import com.google.gson.*;
import java.util.*;
import java.nio.file.Files;

final class P35ScenarioClient {
    final String role=System.getProperty("agnor.p35.role","A");final boolean b=role.equals("B"),multi=P35Files.STAGE.equals("multi"),compat=P35Files.STAGE.startsWith("compat");
    final P35Gpu timer=new P35Gpu();final JsonObject trace=new JsonObject(),renderBench=new JsonObject();final Set<PoseWire.State> states=EnumSet.noneOf(PoseWire.State.class);final Set<String> captured=new HashSet<>();
    String phase="WAIT",failure="";int age,ticks,worldFrames,disconnectTick=-1,entityId=-1,responses;long lastSequence,sequenceBeforeReconnect;UUID npc,world,request,message;
    boolean sent,retrySent,finalSeen,retrySeen,disconnected,connecting,reconnected,cacheCleared,hashFallback,recovered,finished,hadWorld;float peakBlink;
    NpcMotionClient.Metrics startMetrics;
    void install(){
        ClientPlayNetworking.unregisterGlobalReceiver(DialogueProtocol.Response.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(DialogueProtocol.Response.ID,(p,c)->c.client().execute(()->{
            var r=p.value();boolean displayed=DialogueClient.receive(c.client(),r);responses++;
            if(b){failure="foreign private dialogue response";return;}
            if(r.status()==DialogueWire.Status.FINAL){
                if(!retrySent){finalSeen=true;message=r.message();if(!displayed)failure="first reply was not displayed";var data=new JsonObject();data.addProperty("message",message.toString());P35Files.write("A-final.json",data);}
                else{retrySeen=true;if(!message.equals(r.message())||displayed)failure="retry duplicated final UI";var data=new JsonObject();data.addProperty("message",r.message().toString());P35Files.write("A-retry.json",data);}
            }else if(r.status()!=DialogueWire.Status.ACCEPTED&&r.status()!=DialogueWire.Status.PENDING)failure="dialogue terminal "+r.status()+":"+r.error();
        }));
        WorldRenderEvents.START.register(context->timer.begin(age>=100&&List.of("BASELINE","ONE","FIVE").contains(phase)?phase:null));
        WorldRenderEvents.END.register(context->{timer.end();if(++worldFrames==30){var marker=new JsonObject();marker.addProperty("ready",true);P35Files.write(role+"-render-ready.json",marker);}var client=MinecraftClient.getInstance();if(b&&phase.equals("MISMATCH")&&age>30&&client.world!=null&&client.world.getEntityById(entityId) instanceof NpcEntity entity&&NpcMotionClient.sample(entity).isEmpty()){hashFallback=true;capture(client,"fallback");}});ClientLifecycleEvents.CLIENT_STOPPING.register(c->timer.close());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    void tick(MinecraftClient client){
        ticks++;
        if(finished){if(P35Files.read("server-result.json")!=null)client.scheduleStop();return;}
        if(ticks>6000){failure="client deadline";result(client);return;}
        if(!failure.isEmpty()){result(client);return;}
        if(client.world!=null)hadWorld=true;else if(hadWorld&&(!disconnected||reconnected)){failure="connection lost before fixture completion";result(client);return;}
        var data=P35Files.read("state.json");if(data==null)return;
        String next=data.get("phase").getAsString();int nextAge=data.get("age").getAsInt();
        if(!next.equals(phase)){
            if(startMetrics!=null&&List.of("BASELINE","ONE","FIVE").contains(phase))saveRenderBench(phase);
            phase=next;startMetrics=null;
        }
        age=nextAge;
        if(age>=100&&startMetrics==null&&List.of("BASELINE","ONE","FIVE").contains(phase))startMetrics=NpcMotionClient.metrics();
        String id=data.get("npc").getAsString();if(!id.isEmpty()){npc=UUID.fromString(id);world=UUID.fromString(data.get("world").getAsString());}
        if(disconnected&&client.world==null&&npc!=null&&NpcMotionClient.latest(npc)==null)cacheCleared=true;
        if(disconnected&&!connecting&&ticks-disconnectTick>60){connecting=true;ConnectScreen.connect(new TitleScreen(),client,ServerAddress.parse("127.0.0.1:25584"),new ServerInfo("P35 reconnect","127.0.0.1:25584",ServerInfo.ServerType.OTHER),false,null);}
        if(client.world==null||client.player==null)return;
        client.options.hudHidden=true;client.options.getFov().setValue(!b&&phase.equals("DIALOGUE")?30:45);
        var frame=npc==null?null:NpcMotionClient.latest(npc);
        if(compat&&phase.equals("ONE")&&NpcMotionClient.metrics().rendered()>60&&P35Files.read("A-model-ready.json")==null){var marker=new JsonObject();marker.addProperty("ready",true);P35Files.write("A-model-ready.json",marker);}
        if(frame!=null){
            states.add(frame.state());entityId=frame.entityId();if(frame.morphs().size()>13)peakBlink=Math.max(peakBlink,frame.morphs().get(13));
            if(frame.sequence()!=lastSequence){lastSequence=frame.sequence();trace.addProperty(Long.toString(lastSequence),P35ScenarioHost.digest(frame));}
            if(disconnected&&connecting&&!reconnected&&frame.sequence()>sequenceBeforeReconnect+5){reconnected=true;var marker=new JsonObject();marker.addProperty("status",cacheCleared?"PASS":"FAIL");marker.addProperty("sequence",frame.sequence());P35Files.write("B-reconnected.json",marker);}
        }
        if((phase.equals("ONE")||phase.equals("FIVE"))&&age>160&&frame!=null&&NpcMotionClient.metrics().rendered()>30)capture(client,phase);
        if(phase.equals("DIALOGUE")&&!b){
            if(!sent&&age>20&&frame!=null){sent=true;request=UUID.randomUUID();ClientPlayNetworking.send(new DialogueProtocol.Request(new DialogueWire.Request(1,1,DialogueWire.Action.SAY,world,npc,request,"Ответь одним коротким предложением о погоде. Это проверка диалога.")));}
            if(frame!=null&&frame.state()==PoseWire.State.SPEAKING)capture(client,"dialogue");
        }
        if(phase.equals("RETRY")&&!b&&!retrySent&&age>10){retrySent=true;ClientPlayNetworking.send(new DialogueProtocol.Request(new DialogueWire.Request(1,2,DialogueWire.Action.RETRY,world,npc,request,"")));}
        if(phase.equals("RECONNECT")&&b&&!disconnected&&age>20&&frame!=null){
            disconnected=true;disconnectTick=ticks;sequenceBeforeReconnect=frame.sequence();var marker=new JsonObject();marker.addProperty("sequence",sequenceBeforeReconnect);P35Files.write("B-disconnected.json",marker);client.getNetworkHandler().getConnection().disconnect(Text.literal("P35 reconnect check"));return;
        }
        if(phase.equals("MISMATCH")&&b&&NpcMotionClient.metrics().rigMismatch()>5&&client.world.getEntityById(entityId) instanceof NpcEntity entity&&NpcMotionClient.sample(entity).isEmpty()){
            hashFallback=true;if(age>30)capture(client,"fallback");
        }
        if(phase.equals("RECOVER")&&b&&age>40&&frame!=null&&NpcMotionClient.metrics().stale()>0){recovered=true;var marker=new JsonObject();marker.addProperty("status","PASS");marker.addProperty("sequence",frame.sequence());P35Files.write("B-recovered.json",marker);}
        if(phase.equals("DONE"))result(client);
    }
    void saveRenderBench(String label){var end=NpcMotionClient.metrics();var data=new JsonObject();long count=end.rendered()-startMetrics.rendered();data.addProperty("renderCalls",count);data.addProperty("meanNpcSubmissionMs",count==0?0:(end.submissionNanos()-startMetrics.submissionNanos())/1e6/count);renderBench.add(label,data);}
    void capture(MinecraftClient client,String label){
        if(!captured.add(label))return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
            var path=P35Files.ROOT.resolve(role+"-"+label+".png");image.writeTo(path);if(label.equals("ONE"))Files.copy(path,P35Files.ROOT.resolve(role+"-motion.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    void result(MinecraftClient client){
        finished=true;var metrics=NpcMotionClient.metrics();boolean pass=failure.isEmpty()&&metrics.accepted()>100&&metrics.rendered()>100&&peakBlink>.5&&captured.containsAll(Set.of("ONE","FIVE"));
        if(!compat)pass&=states.containsAll(EnumSet.allOf(PoseWire.State.class))&&(b?responses==0:finalSeen&&retrySeen&&captured.contains("dialogue"));
        if(multi&&b)pass&=cacheCleared&&reconnected&&hashFallback&&recovered&&captured.contains("fallback");
        for(String label:List.of("BASELINE","ONE","FIVE"))if(timer.gpu.getOrDefault(label,List.of()).size()<20)pass=false;
        var output=new JsonObject();output.addProperty("status",pass?"PASS":"FAIL");output.addProperty("failure",failure);output.add("metrics",new Gson().toJsonTree(metrics));output.addProperty("states",states.toString());output.addProperty("peakBlink",peakBlink);output.addProperty("privateResponses",responses);output.addProperty("cacheCleared",cacheCleared);output.addProperty("reconnected",reconnected);output.addProperty("hashFallback",hashFallback);output.addProperty("recovered",recovered);output.add("timers",timer.report());output.add("renderBench",renderBench);output.add("trace",trace);
        var mods=new JsonObject();for(String id:List.of("minecraft","fabricloader","fabric-api","gemrender","flywheel","sodium","iris"))mods.addProperty(id,net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id).map(c->c.getMetadata().getVersion().getFriendlyString()).orElse("absent"));output.add("mods",mods);
        boolean shader=false;if(!mods.get("iris").getAsString().equals("absent"))try{var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");shader=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));}catch(Exception error){output.addProperty("shaderApiError",error.getClass().getSimpleName());}
        output.addProperty("shaderActive",shader);String expected=System.getProperty("agnor.p35.compat","none");
        if(!expected.equals("none")&&!mods.get("sodium").getAsString().startsWith("0.6.13"))pass=false;
        if((expected.equals("iris")||expected.equals("shader"))&&!mods.get("iris").getAsString().startsWith("1.8.8"))pass=false;
        if(expected.equals("shader")&&!shader||expected.equals("iris")&&shader)pass=false;output.addProperty("status",pass?"PASS":"FAIL");
        P35Files.write(role+"-result.json",output);System.out.println("P35_SCENARIO_CLIENT "+role+" status="+(pass?"PASS":"FAIL")+" failure="+failure+" states="+states+" metrics="+metrics);
        if(!pass)client.scheduleStop();
    }
}
