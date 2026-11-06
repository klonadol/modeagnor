package dev.agnor.client;

import dev.agnor.content.*;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import static dev.agnor.network.PackWire.*;

/** Session catalog and deliberate consent command. Join/catalog never starts a download. */
public final class PackClient {
    private static Map<String,Offer> offers=Map.of();
    private static PackDownload pending;
    private static volatile long epoch;
    private static int tick,nextTick,expires,retries;
    private static boolean next,installing;
    private static Status lastStatus;
    private static UUID lastToken;
    private static long receivedBytes,completed;
    public static Map<String,Offer> offers(){return offers;}
    public static Status lastStatus(){return lastStatus;}
    public static UUID lastToken(){return lastToken;}
    public static long receivedBytes(){return receivedBytes;}
    public static long completed(){return completed;}
    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(PackProtocol.Response.ID,(p,c)->c.client().execute(()->receive(p.value())));
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->clear());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(client->{tick++;
            if(pending!=null&&!installing&&tick>expires){cancel();message("Download expired; previous local content retained");}
            if(next&&pending!=null&&!installing&&tick>=nextTick){next=false;send(new Request(VERSION,pending.token(),pending.offset()==0?Action.FETCH:Action.NEXT,pending.offer().id(),pending.offer().digest(),pending.offset()));}
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(ClientCommandManager.literal("agnorpack")
            .executes(c->{panel();return 1;})
            .then(ClientCommandManager.literal("list").executes(c->{send(new Request(VERSION,UUID.randomUUID(),Action.LIST,"","",0));return 1;}))
            .then(ClientCommandManager.literal("reload").executes(c->{ContentClient.reload().whenComplete((v,e)->MinecraftClient.getInstance().execute(()->message(e==null?"Local packs reloaded":"Reload rejected; previous local content retained")));return 1;}))
            .then(ClientCommandManager.literal("fetch").then(ClientCommandManager.argument("pack",StringArgumentType.word()).suggests((c,b)->{offers.keySet().forEach(b::suggest);return b.buildFuture();}).executes(c->{fetch(StringArgumentType.getString(c,"pack"));return 1;})))
            .then(ClientCommandManager.literal("cancel").executes(c->{cancel();return 1;}))));
    }
    public static void fetch(String id) {
        var offer=offers.get(id);
        if(offer==null||!offer.allowed()){message("Transfer unavailable. Install a permitted local copy, then /agnorpack reload.");return;}
        if(pending!=null){message("One download at a time; /agnorpack cancel");return;}
        pending=new PackDownload(UUID.randomUUID(),offer);installing=false;expires=tick+600;retries=0;
        send(new Request(VERSION,pending.token(),Action.FETCH,id,offer.digest(),0));
    }
    public static void cancel() {
        if(pending!=null)send(new Request(VERSION,pending.token(),Action.CANCEL,"","",0));pending=null;next=false;installing=false;epoch++;
    }
    private static void clear(){pending=null;offers=Map.of();next=false;installing=false;epoch++;}
    private static void receive(Response response) {
        lastStatus=response.status();lastToken=response.token();
        if(response.status()==Status.CATALOG) {
            var catalog=new LinkedHashMap<String,Offer>();response.offers().forEach(o->catalog.put(o.id(),o));offers=Map.copyOf(catalog);
            if(pending!=null&&!pending.offer().equals(offers.get(pending.offer().id())))cancel();
            message("Server packs: "+offers.size()+". /agnorpack shows local matches and download choices.");return;
        }
        if(pending==null||!pending.token().equals(response.token()))return;
        if(installing)return;
        if(response.status()==Status.BUSY) {if(++retries<=3){next=true;nextTick=tick+10;}else{cancel();message("Server busy; retry later");}return;}
        if(response.status()!=Status.CHUNK){cancel();message("Transfer stopped: "+response.status());return;}
        try {
            receivedBytes+=response.bytes().length;
            if(pending.accept(response)) {
                installing=true;long session=epoch;var download=pending;
                ContentClient.installDownloaded(download.offer(),download::complete,()->epoch==session).whenComplete((v,error)->MinecraftClient.getInstance().execute(()->{
                    if(epoch!=session)return;pending=null;installing=false;next=false;
                    if(error==null){completed++;message("Pack installed and reloaded: "+download.offer().id());}else message("Pack installation rejected; previous local content retained");
                }));
            } else {next=true;nextTick=tick+3;expires=tick+600;retries=0;}
        }catch(Exception error){cancel();message("Invalid transfer; previous local content retained");}
    }
    private static void panel() {
        message("Local packs: mods/agnor/packs/<id> or <id>.zip. /agnorpack reload; /agnorpack cancel.");
        for(var offer:offers.values()) {
            var line=Text.literal(offer.id()+" "+offer.version()+" "+offer.digest().substring(0,12)+" "+(ContentClient.matches(offer.id(),offer.digest())?"MATCH":"MISSING / MISMATCH")+" ");
            if(offer.allowed())line.append(Text.literal("[download ≤16 MiB]").styled(s->s.withColor(net.minecraft.util.Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/agnorpack fetch "+offer.id()))));
            else line.append("LOCAL COPY ONLY");
            var player=MinecraftClient.getInstance().player;if(player!=null)player.sendMessage(line,false);
        }
    }
    private static void send(Request request){if(ClientPlayNetworking.canSend(PackProtocol.Request.ID))ClientPlayNetworking.send(new PackProtocol.Request(request));else message("Server does not support Content Pack protocol v1");}
    private static void message(String text){var player=MinecraftClient.getInstance().player;if(player!=null)player.sendMessage(Text.literal("AGNOR: "+text),false);}
    private PackClient(){}
}
