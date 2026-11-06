package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.motion.MotionService;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.security.MessageDigest;

final class P35ScenarioHost {
    final int players=P35Files.STAGE.equals("multi")?2:1;final boolean compatibility=P35Files.STAGE.startsWith("compat");
    final List<UUID> npcs=new ArrayList<>();final Set<UUID> placed=new HashSet<>();final Set<PoseWire.State> states=EnumSet.noneOf(PoseWire.State.class);
    final JsonObject benchmarks=new JsonObject(),trace=new JsonObject(),rawTrace=new JsonObject();final List<Long> cpu=new ArrayList<>();
    boolean cleanup,ready,started;String phase="WAIT";int ticks,age,dialogueFinished=-1;long lastSequence,measureStarted;MotionService.Metrics from;
    void install(){
        ServerTickEvents.START_SERVER_TICK.register(this::inject);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    void tick(MinecraftServer server){
        if(++ticks>6000){finish(server,"FAIL","deadline");return;}var world=server.getOverworld();var runtime=Agnor.runtime(server);var motion=Agnor.motion(server);
        if(motion==null){finish(server,"FAIL","Motion producer unavailable before first tick");return;}
        for(String role:List.of("A","B")){var failed=P35Files.read(role+"-result.json");if(failed!=null&&failed.get("status").getAsString().equals("FAIL")){finish(server,"FAIL","client "+role+" failed");return;}}
        if(P35Files.read("abort.json")!=null){finish(server,"FAIL","orchestration abort");return;}
        for(var p:server.getPlayerManager().getPlayerList())if(placed.add(p.getUuid())){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);boolean b=p.getName().getString().equals("P35B");p.teleport(world,b?2:0,80,-4,b?18:0,7);}
        if(!started){
            if(server.getPlayerManager().getCurrentPlayerCount()<players)return;
            if(compatibility&&P35Files.read("A-render-ready.json")==null)return;
            if(!cleanup){cleanup=true;world.getChunk(0,0);world.getChunk(-1,-1);world.setTimeOfDay(6000);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());
                var deletions=runtime.activeEntities().stream().map(e->runtime.delete(UUID.randomUUID(),e.getUuid())).toArray(CompletableFuture[]::new);CompletableFuture.allOf(deletions).thenRunAsync(()->ready=true,server);return;
            }
            if(!ready)return;started=true;transition(server,"BASELINE");
        }
        age++;
        if(compatibility&&phase.equals("ONE")&&P35Files.read("A-model-ready.json")==null)age=0;
        if(phase.equals("ONE")||phase.equals("FIVE"))for(int i=0;i<npcs.size();i++){
            var e=world.getEntity(npcs.get(i));if(e!=null){double x=i==0?0:i<3?i-3:i-2;double z=2+.25*Math.sin(age*.06);e.refreshPositionAndAngles(x,80,z,180,0);e.setCustomNameVisible(false);}
        }
        if(!npcs.isEmpty()){
            var f=motion.latest(npcs.getFirst());if(f!=null){states.add(f.state());if(f.sequence()!=lastSequence){lastSequence=f.sequence();String key=Long.toString(f.sequence());trace.addProperty(key,digest(PoseWire.decode(PoseWire.encode(f))));rawTrace.addProperty(key,digest(f));}}
        }
        if(List.of("BASELINE","ONE","FIVE").contains(phase)){
            if(age==100){from=motion.metrics();measureStarted=System.nanoTime();cpu.clear();}
            if(age>=100)cpu.add(motion.lastTickNanos());
            if(age>=240){
                int expected=phase.equals("BASELINE")?0:phase.equals("ONE")?1:5;if(runtime.activeEntities().size()!=expected){finish(server,"FAIL","wrong NPC benchmark count");return;}
                var end=motion.metrics();double seconds=(System.nanoTime()-measureStarted)/1e9;var data=P35Stats.nanos(cpu);data.addProperty("npcs",runtime.activeEntities().size());data.addProperty("seconds",seconds);data.addProperty("payloadBytesPerSecond",(end.bytes()-from.bytes())/seconds);data.addProperty("payloadBytesPerSecondPerViewer",(end.bytes()-from.bytes())/seconds/players);data.addProperty("chainFallbacks",end.chainFallbacks()-from.chainFallbacks());data.addProperty("failures",end.failures()-from.failures());benchmarks.add(phase,data);
                if(phase.equals("BASELINE")){spawn(server,0);transition(server,"ONE");}
                else if(phase.equals("ONE")){for(int i=1;i<5;i++)spawn(server,i);transition(server,"FIVE");}
                else if(compatibility)transition(server,"DONE");
                else{
                    for(var id:List.copyOf(npcs).subList(1,npcs.size()))runtime.delete(UUID.randomUUID(),id);while(npcs.size()>1)npcs.removeLast();
                    var center=world.getEntity(npcs.getFirst());if(center!=null)center.refreshPositionAndAngles(0,80,2,180,0);
                    var a=server.getPlayerManager().getPlayerList().stream().filter(p->!p.getName().getString().equals("P35B")).findFirst().orElseThrow();a.teleport(world,0,80,.5,0,5);transition(server,"DIALOGUE");
                }
            }
        }else if(phase.equals("DIALOGUE")){
            var finalReply=P35Files.read("A-final.json");if(finalReply!=null&&dialogueFinished<0)dialogueFinished=age;
            if(dialogueFinished>=0&&age-dialogueFinished>65){if(!states.containsAll(EnumSet.allOf(PoseWire.State.class))){finish(server,"FAIL","dialogue visual states missing "+states);return;}transition(server,"RETRY");}
        }else if(phase.equals("RETRY")){
            var f=motion.latest(npcs.getFirst());if(f!=null&&f.state()==PoseWire.State.SPEAKING){finish(server,"FAIL","duplicate reply restarted speaking");return;}
            if(age>80&&P35Files.read("A-retry.json")!=null)transition(server,players==2?"RECONNECT":"DONE");
        }else if(phase.equals("RECONNECT")){
            if(age>120&&P35Files.read("B-reconnected.json")!=null)transition(server,"MISMATCH");
        }else if(phase.equals("MISMATCH")||phase.equals("RECOVER")){
            if(phase.equals("MISMATCH")&&age>80)transition(server,"RECOVER");else if(phase.equals("RECOVER")&&age>80&&P35Files.read("B-recovered.json")!=null)transition(server,"DONE");
        }else if(phase.equals("DONE")){
            var a=P35Files.read("A-result.json");var b=players==2?P35Files.read("B-result.json"):a;
            if(a!=null&&b!=null){
                boolean pass=a.get("status").getAsString().equals("PASS")&&b.get("status").getAsString().equals("PASS")&&motion.metrics().failures()==0;int shared=0;
                var ta=a.getAsJsonObject("trace");var tb=b.getAsJsonObject("trace");for(String seq:ta.keySet())if(tb.has(seq)&&trace.has(seq)){shared++;if(!ta.get(seq).equals(tb.get(seq))||(!ta.get(seq).equals(trace.get(seq))&&!ta.get(seq).equals(rawTrace.get(seq))))pass=false;}
                if(shared<100)pass=false;var result=new JsonObject();result.addProperty("status",pass?"PASS":"FAIL");result.addProperty("sharedAuthoritativeFrames",shared);result.addProperty("states",states.toString());result.add("benchmarks",benchmarks);result.add("metrics",new Gson().toJsonTree(motion.metrics()));P35Files.write("server-result.json",result);System.out.println("P35_SCENARIO_RESULT "+result);server.stop(false);return;
            }
        }
        if(age%5==0){var data=new JsonObject();data.addProperty("phase",phase);data.addProperty("age",age);data.addProperty("world",runtime.worldId().toString());data.addProperty("npc",npcs.isEmpty()?"":npcs.getFirst().toString());P35Files.write("state.json",data);}
    }
    void inject(MinecraftServer server){
        if(npcs.isEmpty()||server.getTicks()%2!=1||!(phase.equals("MISMATCH")||phase.equals("RECOVER")&&age<20))return;
        var motion=Agnor.motion(server);if(motion==null)return;var b=server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P35B")).findFirst().orElse(null);var f=motion.latest(npcs.getFirst());
        if(b!=null&&f!=null){boolean mismatch=phase.equals("MISMATCH");var bad=new PoseWire.Frame(f.version(),f.world(),f.npc(),f.entityId(),mismatch?f.sequence()+1:f.sequence()-1,mismatch?f.tick()+1:f.tick()-2,f.dimension(),mismatch?"f".repeat(64):f.rigHash(),f.x(),f.y(),f.z(),f.yaw(),f.state(),f.joints(),f.morphs());ServerPlayNetworking.send(b,new PoseProtocol.Snapshot(bad));}
    }
    void spawn(MinecraftServer server,int index){var p=server.getPlayerManager().getPlayerList().getFirst();double x=index==0?0:index<3?index-3:index-2;
        Agnor.runtime(server).create(UUID.randomUUID(),p.getUuid(),DefinitionRegistry.DEV_ID,server.getOverworld(),x,80,2).thenAccept(s->{npcs.add(s.id());var e=server.getOverworld().getEntity(s.id());if(e!=null)e.setYaw(180);});
    }
    void transition(MinecraftServer server,String next){phase=next;age=0;System.out.println("P35_PHASE "+phase+" tick="+server.getTicks());}
    void finish(MinecraftServer server,String status,String reason){var result=new JsonObject();result.addProperty("status",status);result.addProperty("reason",reason);P35Files.write("server-result.json",result);System.out.println("P35_SCENARIO_FAILED "+result);server.stop(false);}
    static String digest(PoseWire.Frame f){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PoseWire.encode(f)));}catch(Exception e){throw new RuntimeException(e);}}
}
