package dev.agnor.client;

import dev.agnor.network.DialogueWire;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.*;

/** Private chat presentation. Retransmitted finals use stable MessageId dedupe per connection. */
public final class DialogueClient {
    private static final LinkedHashMap<UUID,Boolean> shown=new LinkedHashMap<>();
    public static void clear(){shown.clear();}
    public static boolean receive(MinecraftClient client,DialogueWire.Response r){
        if(client.player==null||r.version()!=DialogueWire.VERSION)return false;
        if(r.status()==DialogueWire.Status.FINAL&&shown.containsKey(r.message()))return false;
        if(r.status()==DialogueWire.Status.FINAL){shown.put(r.message(),true);if(shown.size()>1024)shown.remove(shown.keySet().iterator().next());}
        String text=switch(r.status()){
            case FINAL->"AGNOR: "+r.text();
            case HISTORY->(r.role().equals("user")?"Вы":"AGNOR")+" ["+r.messageSequence()+"]: "+r.text();
            case ACCEPTED,PENDING->"AGNOR думает. Request: "+r.request();
            default->"AGNOR: "+r.status()+(r.error().isEmpty()?"":" ("+r.error()+")")+". Request: "+r.request();
        };
        client.player.sendMessage(Text.literal(text),false);return true;
    }
}
