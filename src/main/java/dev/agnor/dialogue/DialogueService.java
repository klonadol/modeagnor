package dev.agnor.dialogue;

import dev.agnor.character.*;
import dev.agnor.network.*;
import dev.agnor.provider.*;
import dev.agnor.storage.HistoryRepository;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.*;
import java.util.concurrent.*;
import static dev.agnor.network.DialogueWire.*;

/** One server-owned ingress and orchestration path for commands and C2S. */
public final class DialogueService implements AutoCloseable {
    private final MinecraftServer server;
    private final NpcRuntime runtime;
    private final ProviderGateway provider;
    private final ContextBuilder.Policy policy;
    private final Map<ServerPlayNetworkHandler,Task> tasks=new HashMap<>();
    private final Map<ServerPlayNetworkHandler,Integer> lastTick=new HashMap<>();
    public record Trace(UUID npc,Map<String,ContextBuilder.Status> sources,List<String> loreIds,List<String> truncated,int omitted,int budget,long runtimeRevision,int definitionRevision,int historyMessages){}
    private final Map<ServerPlayNetworkHandler,Trace> traces=new HashMap<>();
    public Trace trace(ServerPlayerEntity player){if(!server.isOnThread())throw new IllegalStateException("Trace requires server thread");return traces.get(player.networkHandler);}
    public int defaultRecentN(){return policy.recentN();}
    private boolean closed;
    public DialogueService(MinecraftServer server,NpcRuntime runtime,ProviderGateway provider,ContextBuilder.Policy policy){this.server=server;this.runtime=runtime;this.provider=provider;this.policy=policy;}
    public void handle(ServerPlayerEntity player,Request request){
        if(!server.isOnThread())throw new IllegalStateException("Dialogue requires server thread");
        if(request.version()!=VERSION){status(player,request,Status.VERSION_MISMATCH,"");return;}
        if(!valid(request)){status(player,request,Status.INVALID,"");return;}
        if(closed||runtime==null||(provider==null&&request.action()==Action.SAY)){status(player,request,Status.UNAVAILABLE,"");return;}
        if(!request.world().equals(runtime.worldId())){status(player,request,Status.DENIED,"");return;}
        Task active=tasks.get(player.networkHandler);
        if(request.action()==Action.CANCEL){
            if(active!=null&&active.request.request().equals(request.request())){cancel(active);status(player,active.request,Status.CANCELLED,"");}
            else status(player,request,Status.INVALID,"");return;
        }
        if(active!=null){status(player,request,active.request.request().equals(request.request())?Status.PENDING:Status.BUSY,"");return;}
        int tick=server.getTicks();
        if(tick-lastTick.getOrDefault(player.networkHandler,Integer.MIN_VALUE/2)<10){status(player,request,Status.BUSY,"");return;}
        lastTick.put(player.networkHandler,tick);
        Task task=new Task(player,request,new ConversationScope(runtime.worldId(),request.npc(),player.getUuid()));tasks.put(player.networkHandler,task);
        runtime.inspect(request.npc()).thenComposeAsync(snapshot->{
            if(!permitted(task,snapshot)){if(current(task))status(player,request,Status.DENIED,"");return CompletableFuture.<Void>completedFuture(null);}
            if(request.action()==Action.HISTORY)return runtime.preferences(task.scope,policy.recentN()).thenComposeAsync(value->runtime.history(h->h.recent(task.scope,value.recentN())),server).thenAcceptAsync(messages->{
                if(current(task))for(var m:messages)message(task,m,Status.HISTORY);
            },server);
            return runtime.history(h->request.action()==Action.RETRY?h.find(task.scope,request.request()):h.accept(task.scope,request.request(),request.text()))
                .thenComposeAsync(turn->{
                    if(turn==null){if(current(task))status(player,request,Status.INVALID,"");return CompletableFuture.<Void>completedFuture(null);}
                    task.accepted=true;
                    if(!current(task))return terminate(task,HistoryRepository.State.CANCELLED,"CANCELLATION");
                    if(turn.state()==HistoryRepository.State.FINALIZED){message(task,turn.reply(),Status.FINAL);return CompletableFuture.<Void>completedFuture(null);}
                    if(turn.state()!=HistoryRepository.State.ACCEPTED){status(player,request,turn.state()==HistoryRepository.State.RUNNING?Status.PENDING:turn.state()==HistoryRepository.State.INTERRUPTED?Status.INTERRUPTED:turn.state()==HistoryRepository.State.CANCELLED?Status.CANCELLED:Status.FAILED,turn.error());return CompletableFuture.<Void>completedFuture(null);}
                    if(provider==null)return terminate(task,HistoryRepository.State.FAILED,"CONFIGURATION").thenRunAsync(()->{if(current(task))status(player,request,Status.FAILED,"CONFIGURATION");},server);
                    return runtime.history(h->h.claim(task.scope,request.request())).thenComposeAsync(claimed->{
                        if(!claimed){if(current(task))status(player,request,Status.PENDING,"");return CompletableFuture.<Void>completedFuture(null);}
                        if(!current(task))return terminate(task,HistoryRepository.State.CANCELLED,"CANCELLATION");
                        status(player,request,Status.ACCEPTED,"");
                        var motion=dev.agnor.Agnor.motion(server);if(motion!=null)motion.begin(task.scope.npc(),task.scope.player(),task.request.request());
                        return runtime.context(task.scope,request.request(),snapshot,policy).thenComposeAsync(context->{
                            if(!current(task))return terminate(task,HistoryRepository.State.CANCELLED,"CANCELLATION");
                            traces.put(player.networkHandler,new Trace(snapshot.id(),context.sources(),context.loreSourceIds(),context.truncatedLoreIds(),context.omittedLoreCount(),context.budgetUsed(),context.runtimeRevision(),context.definitionRevision(),context.historyIds().size()));
                            task.call=provider.submit(context.request());
                            return task.call.result().toCompletableFuture().thenComposeAsync(result->finish(task,result),server);
                        },server);
                    },server);
                },server);
        },server).whenCompleteAsync((value,error)->{
            if(error!=null){dev.agnor.Agnor.LOGGER.warn("AGNOR dialogue failed; request={}, error=UNAVAILABLE",request.request());if(task.accepted)terminate(task,HistoryRepository.State.FAILED,"UNAVAILABLE");if(current(task))status(player,request,Status.UNAVAILABLE,"");}
            tasks.remove(player.networkHandler,task);
        },server);
    }
    private CompletableFuture<Void> finish(Task task,ProviderResult result){
        if(!current(task))return terminate(task,HistoryRepository.State.CANCELLED,"CANCELLATION");
        if(result.error()!=ProviderResult.Error.NONE)return terminate(task,result.error()==ProviderResult.Error.CANCELLATION?HistoryRepository.State.CANCELLED:HistoryRepository.State.FAILED,result.error().name())
                .thenRunAsync(()->{if(current(task))status(task.player,task.request,Status.FAILED,result.error().name());},server);
        return runtime.inspect(task.scope.npc()).thenComposeAsync(snapshot->{
            if(!permitted(task,snapshot))return terminate(task,HistoryRepository.State.CANCELLED,"STALE").thenRunAsync(()->{if(current(task))status(task.player,task.request,Status.CANCELLED,"STALE");},server);
            return runtime.history(h->h.finalizeReply(task.scope,task.request.request(),result.text(),provider.model()))
                    .thenAcceptAsync(turn->{if(current(task))message(task,turn.reply(),Status.FINAL);},server);
        },server);
    }
    private CompletableFuture<Void> terminate(Task task,HistoryRepository.State state,String error){var motion=dev.agnor.Agnor.motion(server);if(motion!=null)motion.cancel(task.scope.npc(),task.scope.player(),task.request.request());return runtime.<Void>history(h->{h.terminate(task.scope,task.request.request(),state,error);return null;})
            .whenComplete((v,e)->{if(e!=null)dev.agnor.Agnor.LOGGER.warn("AGNOR dialogue terminal persistence failed; request={}",task.request.request());});}
    private boolean current(Task task){return !closed&&!task.cancelled&&tasks.get(task.player.networkHandler)==task&&server.getPlayerManager().getPlayer(task.player.getUuid())==task.player;}
    private boolean permitted(Task task,RuntimeSnapshot npc){return current(task)&&!npc.deleted()&&NpcAccess.nearby(task.player.getWorld().getRegistryKey().getValue().toString(),task.player.getX(),task.player.getY(),task.player.getZ(),npc);}
    private void message(Task task,HistoryMessage m,Status status){
        if(status==Status.FINAL){var motion=dev.agnor.Agnor.motion(server);if(motion!=null)motion.finish(task.scope.npc(),task.scope.player(),task.request.request());}
        send(task.player,new Response(VERSION,task.request.sequence(),task.scope.npc(),task.request.request(),status,m.id(),m.sequence(),m.role(),m.text(),""));
    }
    private static void status(ServerPlayerEntity player,Request r,Status status,String error){send(player,new Response(VERSION,Math.max(0,r.sequence()),r.npc(),r.request(),status,NpcWire.NIL,0,"","",error));}
    private static void send(ServerPlayerEntity player,Response response){
        if(ServerPlayNetworking.canSend(player,DialogueProtocol.Response.ID))ServerPlayNetworking.send(player,new DialogueProtocol.Response(response));
        else player.sendMessage(Text.literal(response.status()==Status.FINAL||response.status()==Status.HISTORY?"AGNOR: "+response.text():"AGNOR: "+response.status()+" "+response.error()+" request="+response.request()),false);
    }
    public void contentChanged(UUID npc){for(var task:List.copyOf(tasks.values()))if(npc==null||task.scope.npc().equals(npc))cancel(task);traces.entrySet().removeIf(e->npc==null||e.getValue().npc().equals(npc));}
    public void disconnect(ServerPlayNetworkHandler handler){Task task=tasks.get(handler);if(task!=null)cancel(task);lastTick.remove(handler);traces.remove(handler);}
    private void cancel(Task task){task.cancelled=true;var motion=dev.agnor.Agnor.motion(server);if(motion!=null)motion.cancel(task.scope.npc(),task.scope.player(),task.request.request());if(task.call!=null)task.call.cancel();if(task.accepted)terminate(task,HistoryRepository.State.CANCELLED,"CANCELLATION");tasks.remove(task.player.networkHandler,task);}
    public Request cancelRequest(ServerPlayerEntity player){Task task=tasks.get(player.networkHandler);return task==null?null:new Request(VERSION,0,Action.CANCEL,runtime.worldId(),task.scope.npc(),task.request.request(),"");}
    @Override public void close(){for(Task task:List.copyOf(tasks.values()))cancel(task);closed=true;tasks.clear();lastTick.clear();traces.clear();}
    private static final class Task{
        final ServerPlayerEntity player;final Request request;final ConversationScope scope;boolean accepted,cancelled;ProviderGateway.Call call;
        Task(ServerPlayerEntity player,Request request,ConversationScope scope){this.player=player;this.request=request;this.scope=scope;}
    }
}
