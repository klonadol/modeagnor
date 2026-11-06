package dev.agnor.network;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.dialogue.ConversationScope;
import dev.agnor.lore.*;
import dev.agnor.storage.LoreRepository;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.command.argument.IdentifierArgumentType;
import com.mojang.brigadier.arguments.*;
import java.util.*;
import java.util.concurrent.*;

/** Private addressed command UI. Scope/identity are always derived from the selected NPC and sender. */
public final class LoreCommands {
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
        var root=CommandManager.literal("lore").executes(c->list(c.getSource(),0))
            .then(CommandManager.literal("list").executes(c->list(c.getSource(),0)).then(CommandManager.argument("page",IntegerArgumentType.integer(0,200)).executes(c->list(c.getSource(),IntegerArgumentType.getInteger(c,"page")))));
        for(String area:List.of("private","shared")) {
            root.then(CommandManager.literal("put").then(CommandManager.literal(area).then(CommandManager.argument("id",IdentifierArgumentType.identifier())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(0)).then(CommandManager.argument("json",StringArgumentType.greedyString()).executes(c->with(c.getSource(),(player,runtime,npc)->{
                    var entry=LoreJson.read(IdentifierArgumentType.getIdentifier(c,"id").toString(),StringArgumentType.getString(c,"json"));
                    return runtime.lore(repo->repo.put(scope(runtime,npc,player,area),player.getUuid(),player.hasPermissionLevel(2),entry,LongArgumentType.getLong(c,"revision"))).thenAcceptAsync(row->feedback(player,"Saved "+row.entry().id()+" revision="+row.revision()),player.getServer());
                })))))));
            root.then(CommandManager.literal("delete").then(CommandManager.literal(area).then(CommandManager.argument("id",IdentifierArgumentType.identifier())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(1)).executes(c->with(c.getSource(),(player,runtime,npc)->runtime.lore(repo->{repo.delete(scope(runtime,npc,player,area),player.getUuid(),player.hasPermissionLevel(2),IdentifierArgumentType.getIdentifier(c,"id").toString(),LongArgumentType.getLong(c,"revision"));return null;})
                    .thenRunAsync(()->feedback(player,"Deleted; tombstone retained"),player.getServer())))))));
            root.then(CommandManager.literal("get").then(CommandManager.literal(area).then(CommandManager.argument("id",IdentifierArgumentType.identifier()).executes(c->with(c.getSource(),(player,runtime,npc)->runtime.lore(repo->repo.get(scope(runtime,npc,player,area),player.getUuid(),IdentifierArgumentType.getIdentifier(c,"id").toString()))
                .thenAcceptAsync(row->feedback(player,row==null?"No local entry":row.entry().id()+" revision="+row.revision()+" deleted="+row.deleted()+" "+LoreJson.json(row.entry())),player.getServer()))))));
            for(var mode:LoreEntry.Mode.values())root.then(CommandManager.literal("add").then(CommandManager.literal(area).then(CommandManager.literal(mode.name().toLowerCase(Locale.ROOT))
                .then(CommandManager.argument("id",IdentifierArgumentType.identifier()).then(CommandManager.argument("trigger",StringArgumentType.string()).then(CommandManager.argument("text",StringArgumentType.greedyString()).executes(c->with(c.getSource(),(player,runtime,npc)->{
                    var entry=new LoreEntry(IdentifierArgumentType.getIdentifier(c,"id").toString(),"Knowledge",StringArgumentType.getString(c,"text"),mode,mode==LoreEntry.Mode.ALWAYS?List.of():List.of(StringArgumentType.getString(c,"trigger")),0,"user",true,"*","*");
                    return runtime.lore(repo->repo.put(scope(runtime,npc,player,area),player.getUuid(),player.hasPermissionLevel(2),entry,0)).thenAcceptAsync(row->feedback(player,"Saved "+row.entry().id()+" revision="+row.revision()),player.getServer());
                }))))))));
        }
        dispatcher.register(CommandManager.literal("agnor").then(root));
    });}
    private static int list(ServerCommandSource source,int page){return with(source,(player,runtime,npc)->runtime.loreEntries(new ConversationScope(runtime.worldId(),npc.id(),player.getUuid())).thenAcceptAsync(entries->{
        feedback(player,"Lorebook page="+page+" entries="+entries.size()+" (20/page). /agnor lore get private|shared <id>. Pack < local shared < local private.");
        entries.stream().skip((long)page*20).limit(20).forEach(row->feedback(player,row.entry().id()+" rev="+row.revision()+" "+row.entry().mode()+" role="+row.entry().role()+" enabled="+row.entry().enabled()+" source="+row.source()));
    },player.getServer()));}
    private static LoreRepository.Scope scope(NpcRuntime runtime,RuntimeSnapshot npc,ServerPlayerEntity player,String area){return new LoreRepository.Scope(runtime.worldId(),npc.id(),area.equals("shared")?LoreRepository.SHARED:player.getUuid());}
    private static int with(ServerCommandSource source,Work work){
        try{var player=source.getPlayerOrThrow();var id=NpcManagement.selected(player);if(id==null)throw new IllegalStateException("Select NPC");var runtime=Agnor.runtime(player.getServer());
            runtime.inspect(id).thenComposeAsync(npc->{
                if(!current(player)||npc.deleted()||!NpcAccess.nearby(player.getWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),npc))return CompletableFuture.failedFuture(new IllegalStateException("DENIED"));
                try{return work.run(player,runtime,npc);}catch(Exception e){return CompletableFuture.failedFuture(e);}
            },player.getServer()).whenCompleteAsync((v,e)->{if(e!=null&&current(player))feedback(player,"Operation rejected: scope, rights, revision, schema or capacity. No entry was changed.");},player.getServer());
        }catch(Exception e){source.sendError(Text.literal("AGNOR lore: select an available NPC first"));return 0;}return 1;
    }
    private static boolean current(ServerPlayerEntity player){return player.getServer()!=null&&player.getServer().getPlayerManager().getPlayer(player.getUuid())==player;}
    private static void feedback(ServerPlayerEntity player,String text){if(current(player))player.sendMessage(Text.literal("AGNOR lore: "+text),false);}
    @FunctionalInterface private interface Work{CompletableFuture<?> run(ServerPlayerEntity player,NpcRuntime runtime,RuntimeSnapshot npc)throws Exception;}
}
