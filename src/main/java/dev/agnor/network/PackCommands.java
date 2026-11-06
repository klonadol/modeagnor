package dev.agnor.network;

import dev.agnor.Agnor;
import dev.agnor.character.NpcAccess;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.*;
import net.minecraft.text.*;
import com.mojang.brigadier.arguments.*;
import java.util.concurrent.*;

/** Chat control panel: clickable choices and server-owned permission checks for every mutation. */
public final class PackCommands {
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
        var packs=CommandManager.literal("packs").requires(source->source.hasPermissionLevel(2)).executes(c->list(c.getSource()));
        for(String action:new String[]{"enable","reload","disable"})packs.then(CommandManager.literal(action).then(CommandManager.argument("pack",StringArgumentType.word())
            .suggests((c,b)->{Agnor.content(c.getSource().getServer()).active().packs().keySet().forEach(b::suggest);return b.buildFuture();})
            .executes(c->{var service=Agnor.content(c.getSource().getServer());String id=StringArgumentType.getString(c,"pack");
                return respond(c.getSource(),action.equals("disable")?service.disable(id):service.enable(id));})));
        packs.then(CommandManager.literal("rollback").executes(c->respond(c.getSource(),Agnor.content(c.getSource().getServer()).rollback())));
        packs.then(CommandManager.literal("share").then(CommandManager.argument("pack",StringArgumentType.word()).then(CommandManager.argument("enabled",BoolArgumentType.bool())
            .executes(c->respond(c.getSource(),PackSync.share(c.getSource().getServer(),StringArgumentType.getString(c,"pack"),BoolArgumentType.getBool(c,"enabled")))))));
        dispatcher.register(CommandManager.literal("agnor").then(packs)
            .then(CommandManager.literal("characters").executes(c->characters(c.getSource())))
            .then(CommandManager.literal("character").then(CommandManager.argument("definition",net.minecraft.command.argument.IdentifierArgumentType.identifier())
                .suggests((c,b)->{Agnor.content(c.getSource().getServer()).registry().snapshot().keySet().forEach(b::suggest);return b.buildFuture();})
                .executes(c->choose(c.getSource(),net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(c,"definition").toString())))));
    });}
    private static int list(ServerCommandSource source) {
        var content=Agnor.content(source.getServer());
        source.sendFeedback(()->Text.literal("AGNOR packs: "+content.active().packs().size()+" / 32. Models: mods/agnor/packs/<id> or <id>.zip. ").append(button("[rollback]","/agnor packs rollback")),false);
        content.active().packs().values().forEach(p->source.sendFeedback(()->Text.literal(p.id()+" "+p.version()+" "+p.provenance().rights()+" "+p.digest().substring(0,12)+" ")
            .append(button("[reload]","/agnor packs reload "+p.id())).append(" ").append(button("[disable]","/agnor packs disable "+p.id())),false));
        return 1;
    }
    private static int characters(ServerCommandSource source) {
        var content=Agnor.content(source.getServer());content.registry().snapshot().values().stream().sorted(java.util.Comparator.comparing(dev.agnor.character.CharacterDefinition::id)).limit(32).forEach(d->{
            var binding=content.active().bindings().get(d.id());String capability=binding==null?"BUILT_IN":binding.capability().name();
            source.sendFeedback(()->Text.literal(d.displayName()+" ["+d.id()+"] "+capability+" ").append(button("[use]","/agnor character "+d.id())),false);
        });source.sendFeedback(()->Text.literal("First 32 definitions; all IDs are available through command suggestions. Voice/behavior: NOT_YET_ENABLED. /agnor select <UUID>."),false);return 1;
    }
    private static int choose(ServerCommandSource source,String definition) {
        try {
            var player=source.getPlayerOrThrow();var npc=NpcManagement.selected(player);if(npc==null){source.sendError(Text.literal("AGNOR: select an NPC first"));return 0;}
            var runtime=Agnor.runtime(source.getServer());Agnor.content(source.getServer()).registry().require(definition);
            return respond(source,runtime.inspect(npc).thenComposeAsync(snapshot->{
                if(!NpcAccess.canManage(player.getUuid(),source.hasPermissionLevel(2),snapshot)||!NpcAccess.nearby(player.getWorld().getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),snapshot))return CompletableFuture.failedFuture(new IllegalStateException("DENIED"));
                return runtime.redefine(npc,definition);
            },source.getServer()));
        }catch(Exception error){source.sendError(Text.literal("AGNOR: unavailable definition, selection or permission"));return 0;}
    }
    private static int respond(ServerCommandSource source,CompletableFuture<?> future) {
        future.whenCompleteAsync((value,error)->{if(source.getPlayer()!=null&&source.getServer().getPlayerManager().getPlayer(source.getPlayer().getUuid())!=source.getPlayer())return;
            if(error==null)source.sendFeedback(()->Text.literal("AGNOR: content operation complete"),false);else source.sendError(Text.literal("AGNOR: content operation rejected; previous active content retained. Check pack schema, dependencies, rights and limits."));
        },source.getServer());return 1;
    }
    private static MutableText button(String label,String command){return Text.literal(label).styled(s->s.withColor(net.minecraft.util.Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));}
}
