package dev.agnor.network;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.dialogue.*;
import dev.agnor.storage.MemoryPreferences;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.*;
import java.util.concurrent.*;

public final class MemoryCommands {
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
        var memory=CommandManager.literal("memory").executes(c->with(c.getSource(),(p,r,s)->r.preferences(s,Agnor.dialogue(p.getServer()).defaultRecentN()).thenAcceptAsync(v->say(p,"recentN="+v.recentN()+" retentionKeep="+v.retentionKeep()),p.getServer())));
        memory.then(CommandManager.literal("recent").then(CommandManager.argument("n",IntegerArgumentType.integer(1,100)).executes(c->with(c.getSource(),(p,r,s)->r.preferences(s,Agnor.dialogue(p.getServer()).defaultRecentN()).thenComposeAsync(v->r.preferences(s,new MemoryPreferences.Value(IntegerArgumentType.getInteger(c,"n"),v.retentionKeep())),p.getServer()).thenRunAsync(()->say(p,"Recent window saved; History was not deleted"),p.getServer())))));
        memory.then(CommandManager.literal("keep").then(CommandManager.argument("n",IntegerArgumentType.integer(2,100000)).executes(c->with(c.getSource(),(p,r,s)->r.preferences(s,Agnor.dialogue(p.getServer()).defaultRecentN()).thenComposeAsync(v->r.preferences(s,new MemoryPreferences.Value(v.recentN(),IntegerArgumentType.getInteger(c,"n"))),p.getServer()).thenRunAsync(()->say(p,"Retention target saved; pending sources remain protected"),p.getServer())))));
        memory.then(CommandManager.literal("history").executes(c->page(c.getSource(),Long.MAX_VALUE)).then(CommandManager.argument("beforeSequence",LongArgumentType.longArg(1)).executes(c->page(c.getSource(),LongArgumentType.getLong(c,"beforeSequence")))));
        memory.then(CommandManager.literal("retention").executes(c->retention(c.getSource(),false)).then(CommandManager.literal("apply").executes(c->retention(c.getSource(),true))));
        memory.then(CommandManager.literal("backup").requires(s->s.hasPermissionLevel(2)).executes(c->{var source=c.getSource();Agnor.runtime(source.getServer()).backupMemory().whenCompleteAsync((name,error)->{if(error==null)source.sendFeedback(()->Text.literal("AGNOR memory backup: "+name),false);else source.sendError(Text.literal("AGNOR memory backup rejected"));},source.getServer());return 1;}));
        memory.then(CommandManager.literal("restore").requires(s->s.hasPermissionLevel(2)).then(CommandManager.argument("backup",StringArgumentType.word()).executes(c->{var source=c.getSource();Agnor.runtime(source.getServer()).restoreMemory(StringArgumentType.getString(c,"backup")).whenCompleteAsync((v,error)->{if(error==null)source.sendFeedback(()->Text.literal("AGNOR memory restored; NPCs reloaded from snapshot"),false);else source.sendError(Text.literal("AGNOR memory restore rejected; current DB reconciled"));},source.getServer());return 1;})));
        dispatcher.register(CommandManager.literal("agnor").then(memory));
    });}
    private static int page(ServerCommandSource source,long before){return with(source,(p,r,s)->r.history(h->h.page(s,before,20)).thenAcceptAsync(rows->{say(p,"History page: "+rows.size()+"; beforeSequence is exclusive");for(var row:rows)say(p,"["+row.sequence()+"] "+row.role()+": "+row.text());if(!rows.isEmpty())say(p,"Older: /agnor memory history "+rows.getFirst().sequence());},p.getServer()));}
    private static int retention(ServerCommandSource source,boolean apply){return with(source,(p,r,s)->r.preferences(s,Agnor.dialogue(p.getServer()).defaultRecentN()).thenComposeAsync(v->r.history(h->h.retention(s,v.retentionKeep(),apply)),p.getServer()).thenAcceptAsync(report->say(p,report.toString()),p.getServer()));}
    private static int with(ServerCommandSource source,Work work){
        try{var player=source.getPlayerOrThrow();var id=NpcManagement.selected(player);if(id==null)throw new IllegalStateException();var runtime=Agnor.runtime(player.getServer());
            runtime.inspect(id).thenComposeAsync(snapshot->{if(!current(player)||snapshot.deleted()||!NpcAccess.nearby(player.getWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),snapshot))return CompletableFuture.failedFuture(new IllegalStateException("Denied"));return work.run(player,runtime,new ConversationScope(runtime.worldId(),id,player.getUuid()));},player.getServer())
                .whenCompleteAsync((v,e)->{if(e!=null)say(player,"Request rejected; scope/maintenance/unavailable");},player.getServer());
        }catch(Exception error){source.sendError(Text.literal("AGNOR memory: select an available NPC first"));return 0;}return 1;
    }
    private static boolean current(ServerPlayerEntity p){return p.getServer()!=null&&p.getServer().getPlayerManager().getPlayer(p.getUuid())==p;}
    private static void say(ServerPlayerEntity p,String text){if(current(p))p.sendMessage(Text.literal("AGNOR memory: "+text),false);}
    @FunctionalInterface private interface Work{CompletableFuture<?> run(ServerPlayerEntity player,NpcRuntime runtime,ConversationScope scope);}
}
