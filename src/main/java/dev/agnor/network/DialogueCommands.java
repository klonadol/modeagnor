package dev.agnor.network;

import dev.agnor.Agnor;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import static dev.agnor.network.DialogueWire.*;

public final class DialogueCommands {
    public static void register(){
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->Agnor.dialogueDisconnect(server,handler));
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(CommandManager.literal("agnor")
            .then(CommandManager.literal("context").executes(c->{var player=c.getSource().getPlayerOrThrow();var trace=Agnor.dialogue(player.getServer()).trace(player);player.sendMessage(Text.literal(trace==null?"AGNOR: no context snapshot":"AGNOR context: sources="+trace.sources()+" lore="+trace.loreIds()+" truncated="+trace.truncated().size()+" omitted="+trace.omitted()+" history="+trace.historyMessages()+" bytes="+trace.budget()),false);return 1;}))
            .then(CommandManager.literal("say").then(CommandManager.argument("text",StringArgumentType.greedyString())
                .executes(c->command(c.getSource().getPlayerOrThrow(),Action.SAY,UUID.randomUUID(),StringArgumentType.getString(c,"text")))))
            .then(CommandManager.literal("retry").then(CommandManager.argument("request",UuidArgumentType.uuid())
                .executes(c->command(c.getSource().getPlayerOrThrow(),Action.RETRY,UuidArgumentType.getUuid(c,"request"),""))))
            .then(CommandManager.literal("history").executes(c->command(c.getSource().getPlayerOrThrow(),Action.HISTORY,UUID.randomUUID(),"")))
            .then(CommandManager.literal("cancel").executes(c->{var player=c.getSource().getPlayerOrThrow();var service=Agnor.dialogue(player.getServer());var r=service.cancelRequest(player);if(r!=null)service.handle(player,r);return r==null?0:1;}))));
    }
    private static int command(ServerPlayerEntity player,Action action,UUID request,String text){
        UUID selected=NpcManagement.selected(player);
        if(selected==null){player.sendMessage(Text.literal("AGNOR: сначала /agnor select <UUID> или /agnor spawn."),false);return 0;}
        Agnor.dialogue(player.getServer()).handle(player,new Request(VERSION,0,action,Agnor.runtime(player.getServer()).worldId(),selected,request,text));return 1;
    }
}
