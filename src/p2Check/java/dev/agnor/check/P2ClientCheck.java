package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.client.DialogueClient;
import dev.agnor.network.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import static dev.agnor.network.DialogueWire.*;

/** Real commands/play packets; forwards every received response to production private chat UI. */
public final class P2ClientCheck implements ClientModInitializer {
    final String role=System.getProperty("agnor.p2.role"),word=role.equals("A")?"AlderAzure":"BirchBronze",other=role.equals("A")?"BirchBronze":"AlderAzure";
    final boolean integrated=Boolean.getBoolean("agnor.p2.integrated");
    final List<Wait> waits=new ArrayList<>();final List<Response> history=new ArrayList<>();
    CompletableFuture<NpcWire.Snapshot> npcPending;CompletableFuture<Response> terminal;
    UUID npc,world,firstMessage;int ticks,finishTicks;long sequence=100;boolean started,finished,replayExpected;
    String faultPhase="";CompletableFuture<Response> accepted;int minProbe=Integer.MAX_VALUE,maxProbe,lastProbeTick;
    @Override public void onInitializeClient(){
        ClientPlayNetworking.unregisterGlobalReceiver(ProbeProtocol.Snapshot.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(ProbeProtocol.Snapshot.ID,(payload,ctx)->{if(payload.sequence()>=500){minProbe=Math.min(minProbe,payload.serverTick());maxProbe=Math.max(maxProbe,payload.serverTick());}});
        ClientPlayNetworking.unregisterGlobalReceiver(NpcProtocol.Snapshot.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(NpcProtocol.Snapshot.ID,(payload,ctx)->{if(npcPending!=null){var f=npcPending;npcPending=null;f.complete(payload.value());}});
        ClientPlayNetworking.unregisterGlobalReceiver(DialogueProtocol.Response.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(DialogueProtocol.Response.ID,(payload,ctx)->{
            var r=payload.value();boolean displayed=DialogueClient.receive(ctx.client(),r);
            if(r.text().contains(other)){fail(new IllegalStateException("Foreign private dialogue reached client"));return;}
            if(r.status()==Status.HISTORY){history.add(r);return;}
            if(r.status()==Status.ACCEPTED){if(faultPhase.equals("stale"))P2Files.write(P2Files.key("stale"),"yes");if(accepted!=null){var f=accepted;accepted=null;f.complete(r);}return;}
            if(r.status()==Status.PENDING)return;
            if(terminal==null){fail(new IllegalStateException("Unexpected private response"));return;}
            if(r.status()==Status.FINAL&&replayExpected&&displayed){fail(new IllegalStateException("Retry duplicated chat presentation"));return;}
            var f=terminal;terminal=null;f.complete(r);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            ticks++;for(var w:List.copyOf(waits))if(w.condition.getAsBoolean()){waits.remove(w);w.future.complete(null);}
            if(faultPhase.equals("timeout")&&client.getNetworkHandler()!=null&&ticks-lastProbeTick>=20){lastProbeTick=ticks;ClientPlayNetworking.send(new ProbeProtocol.Request(ProbeProtocol.VERSION,500+ticks));}
            if(finished){if(++finishTicks>100)client.scheduleStop();return;}
            if(ticks>6000){fail(new IllegalStateException("Client deadline"));return;}
            if(started||client.player==null||client.getNetworkHandler()==null||(!integrated&&!P2Files.STAGE.equals("3")&&client.getNetworkHandler().getPlayerList().size()<2))return;
            started=true;
            pause().thenCompose(v->{
                if(P2Files.STAGE.equals("1")&&role.equals("A"))return npcCommand("agnor spawn").thenAccept(s->{require(s.status()==NpcWire.OK,"spawn");npc=s.npc();world=s.world();P2Files.write("npc-"+P2Files.WORLD,npc.toString());P2Files.write("world-"+P2Files.WORLD,world.toString());});
                return waitFor(()->P2Files.exists("npc-"+P2Files.WORLD)).thenCompose(x->{npc=UUID.fromString(P2Files.read("npc-"+P2Files.WORLD));return npcCommand("agnor select "+npc);})
                        .thenAccept(s->{require(s.status()==NpcWire.OK,"select");world=s.world();require(world.toString().equals(P2Files.read("world-"+P2Files.WORLD)),"world restart mismatch");});
            }).thenCompose(v->pause()).thenCompose(v->P2Files.STAGE.equals("1")?stageOne():P2Files.STAGE.equals("3")?stageFaults():P2Files.STAGE.equals("4")?stageCloud():stageTwo()).whenComplete((v,e)->{
                if(e!=null)fail(e);else{P2Files.write(P2Files.key("done-"+role),"PASS");finished=true;Agnor.LOGGER.info("P2 CLIENT PASS role={} world={} stage={}",role,P2Files.WORLD,P2Files.STAGE);}
            });
        });
    }
    CompletableFuture<Void> stageOne(){
        return dialogueCommand("agnor say My secret code is "+word+". Reply briefly and include that code.").thenCompose(r->{
            require(r.status()==Status.FINAL&&r.text().contains(word)&&r.messageSequence()==2,"first final");firstMessage=r.message();
            P2Files.write("request-"+P2Files.WORLD+role,r.request().toString());P2Files.write("message-"+P2Files.WORLD+role,r.message().toString());
            return pause().thenCompose(v->{replayExpected=true;return dialogueCommand("agnor retry "+r.request());});
        }).thenCompose(r->{require(r.status()==Status.FINAL&&r.message().equals(firstMessage)&&r.messageSequence()==2,"replay final");replayExpected=false;return history(2);})
          .thenCompose(v->pause()).thenCompose(v->packet(new Request(99,++sequence,Action.HISTORY,world,npc,UUID.randomUUID(),"")))
          .thenCompose(r->{require(r.status()==Status.VERSION_MISMATCH,"version guard");return packet(new Request(VERSION,++sequence,Action.HISTORY,UUID.randomUUID(),npc,UUID.randomUUID(),""));})
          .thenAccept(r->require(r.status()==Status.DENIED,"world guard"));
    }
    CompletableFuture<Void> stageTwo(){
        return history(2).thenCompose(v->pause()).thenCompose(v->dialogueCommand("agnor say What secret code did I tell you? Reply with only that code."))
          .thenCompose(r->{require(r.status()==Status.FINAL&&r.text().contains(word)&&r.messageSequence()==4,"remembered after restart");return history(4);});
    }
    CompletableFuture<Void> stageCloud(){return history(4).thenCompose(v->pause()).thenCompose(v->dialogueCommand("agnor say What secret code did I tell you? Reply with only that code."))
        .thenCompose(r->{require(r.status()==Status.FINAL&&r.text().contains(word)&&r.messageSequence()==6,"real cloud context/final");return history(6);});}
    CompletableFuture<Void> stageFaults(){
        faultPhase="timeout";minProbe=Integer.MAX_VALUE;maxProbe=0;
        return dialogueCommand("agnor say timeout fixture").thenCompose(r->{
            require(r.status()==Status.FAILED&&r.error().equals("TIMEOUT"),"timeout terminal");require(maxProbe-minProbe>=20,"server tick stalled during provider timeout");
            P2Files.write(P2Files.key("tick-proof"),"advanced="+(maxProbe-minProbe));faultPhase="";return pause();
        }).thenCompose(v->{faultPhase="stale";return dialogueCommand("agnor say stale fixture");}).thenCompose(r->{
            require(r.status()==Status.CANCELLED&&r.error().equals("STALE"),"stale reply was not cancelled");faultPhase="";P2Files.write(P2Files.key("reset"),"yes");return waitFor(()->P2Files.exists(P2Files.key("reset-done")));
        }).thenCompose(v->pause()).thenCompose(v->{faultPhase="cancel";accepted=new CompletableFuture<>();var seen=accepted;var done=dialogueCommand("agnor say cancel fixture");seen.thenRun(()->MinecraftClient.getInstance().getNetworkHandler().sendChatCommand("agnor cancel"));return done;})
        .thenCompose(r->{require(r.status()==Status.CANCELLED,"explicit cancel");return pause();}).thenCompose(v->{
            faultPhase="disconnect";accepted=new CompletableFuture<>();var seen=accepted;dialogueCommand("agnor say disconnect fixture");
            return seen.thenCompose(r->{P2Files.write(P2Files.key("disconnect"),r.request().toString());terminal=null;MinecraftClient.getInstance().getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.literal("P2 disconnect check"));return waitFor(()->P2Files.exists(P2Files.key("SERVER-PASS")));});
        });
    }
    CompletableFuture<Void> history(int count){return pause().thenCompose(x->{history.clear();ClientPlayNetworking.send(new DialogueProtocol.Request(new Request(VERSION,++sequence,Action.HISTORY,world,npc,UUID.randomUUID(),"")));
        return waitFor(()->history.size()>=count).thenAccept(v->{require(history.size()==count,"history size");for(int i=0;i<count;i++)require(history.get(i).messageSequence()==i+1,"chronological sequence");require(history.stream().anyMatch(r->r.text().contains(word)),"own private code missing");});});}
    CompletableFuture<Response> dialogueCommand(String command){terminal=new CompletableFuture<>();MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command);return terminal;}
    CompletableFuture<Response> packet(Request r){terminal=new CompletableFuture<>();ClientPlayNetworking.send(new DialogueProtocol.Request(r));return terminal;}
    CompletableFuture<NpcWire.Snapshot> npcCommand(String command){npcPending=new CompletableFuture<>();MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command);return npcPending;}
    CompletableFuture<Void> pause(){int until=ticks+15;return waitFor(()->ticks>=until);}
    CompletableFuture<Void> waitFor(BooleanSupplier condition){var f=new CompletableFuture<Void>();waits.add(new Wait(condition,f));return f;}
    void fail(Throwable e){if(finished)return;finished=true;P2Files.write(P2Files.key("FAILED-"+role),e.toString());Agnor.LOGGER.error("P2 CLIENT FAIL role={}",role,e);}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    record Wait(BooleanSupplier condition,CompletableFuture<Void> future){}
}
