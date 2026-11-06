package dev.agnor.check;
import dev.agnor.Agnor;
import dev.agnor.dialogue.ConversationScope;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.GameMode;
public final class P2ServerCheck implements ModInitializer {
    final Set<UUID> players=new HashSet<>();boolean checking,finished,staleMoved,reset;int ticks,baseline;double resetX,resetY,resetZ;
    @Override public void onInitialize(){
      if(P2Files.STAGE.equals("3"))net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server->P2MockProvider.start());
      ServerTickEvents.END_SERVER_TICK.register(server->{
        if(finished)return;server.getOverworld().getChunk(0,0);
        for(var p:server.getPlayerManager().getPlayerList())if(players.add(p.getUuid())){
            p.changeGameMode(GameMode.SPECTATOR);
            if(P2Files.exists("npc-"+P2Files.WORLD)){
                var runtime=Agnor.runtime(server);var npc=UUID.fromString(P2Files.read("npc-"+P2Files.WORLD));
                runtime.inspect(npc).thenAcceptAsync(s->p.networkHandler.requestTeleport(s.x(),s.y(),s.z(),0,0),server);
                if(P2Files.STAGE.equals("3"))runtime.history(h->h.recent(new ConversationScope(runtime.worldId(),npc,p.getUuid()),100).size()).thenAcceptAsync(n->baseline=n,server);
            }else p.networkHandler.requestTeleport(0,80,0,0,0);
        }
        if(++ticks>7200||P2Files.exists(P2Files.key("FAILED-A"))||P2Files.exists(P2Files.key("FAILED-B"))){finished=true;P2Files.write(P2Files.key("SERVER-FAIL"),"client failure or timeout");server.stop(false);return;}
        if(P2Files.STAGE.equals("3")){
            var p=server.getPlayerManager().getPlayer("P2A");
            if(p!=null&&!staleMoved&&P2Files.exists(P2Files.key("stale"))){staleMoved=true;resetX=p.getX();resetY=p.getY();resetZ=p.getZ();p.networkHandler.requestTeleport(200,80,0,0,0);}
            if(p!=null&&staleMoved&&!reset&&P2Files.exists(P2Files.key("reset"))){reset=true;p.networkHandler.requestTeleport(resetX,resetY,resetZ,0,0);P2Files.write(P2Files.key("reset-done"),"ok");}
            if(!checking&&P2Files.exists(P2Files.key("disconnect"))&&server.getPlayerManager().getPlayerList().isEmpty()){
                checking=true;var runtime=Agnor.runtime(server);var npc=UUID.fromString(P2Files.read("npc-"+P2Files.WORLD));var scope=new ConversationScope(runtime.worldId(),npc,players.iterator().next());
                runtime.history(h->{var records=h.recent(scope,100);var events=h.events(scope,0,100);var turn=h.find(scope,UUID.fromString(P2Files.read(P2Files.key("disconnect"))));
                    if(records.size()!=baseline+4||events.size()!=baseline+4||turn.state()!=dev.agnor.storage.HistoryRepository.State.CANCELLED||P2MockProvider.timeouts.get()!=1)throw new IllegalStateException("fault persistence/dedupe mismatch");return null;
                }).whenCompleteAsync((v,e)->{finished=true;P2Files.write(P2Files.key(e==null?"SERVER-PASS":"SERVER-FAIL"),e==null?"timeout/stale/cancel/disconnect/counts PASS":"fault check failed");server.stop(false);},server);
            }
            if(checking||ticks<7200)return;
        }
        if(checking||!P2Files.exists(P2Files.key("done-A"))||(server.isDedicated()&&!P2Files.exists(P2Files.key("done-B"))))return;
        checking=true;var runtime=Agnor.runtime(server);var npc=UUID.fromString(P2Files.read("npc-"+P2Files.WORLD));
        int expected=P2Files.STAGE.equals("1")?2:P2Files.STAGE.equals("4")?6:4;
        var checks=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(var p:server.getPlayerManager().getPlayerList()){
            var scope=new ConversationScope(runtime.worldId(),npc,p.getUuid());
            checks.add(runtime.history(h->{var recent=h.recent(scope,100);var events=h.events(scope,0,100);if(recent.size()!=expected||events.size()!=expected)throw new IllegalStateException("message/event count mismatch");return null;}));
        }
        java.util.concurrent.CompletableFuture.allOf(checks.toArray(java.util.concurrent.CompletableFuture[]::new)).whenCompleteAsync((v,e)->{
            finished=true;P2Files.write(P2Files.key(e==null?"SERVER-PASS":"SERVER-FAIL"),e==null?"records/events checked":"storage check failed");
            Agnor.LOGGER.info("P2 SERVER {} world={} stage={} ticks={}",e==null?"PASS":"FAIL",P2Files.WORLD,P2Files.STAGE,ticks);server.stop(false);
        },server);
    });}
}
