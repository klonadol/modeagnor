package dev.agnor.lore;

import com.google.gson.*;
import dev.agnor.content.PackJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LoreJson {
    public static LoreEntry read(String id,String json)throws IOException {
        if(json.getBytes(StandardCharsets.UTF_8).length>24_000)throw new IOException("Lore entry size");
        return entry(id,PackJson.read(json.getBytes(StandardCharsets.UTF_8)).getAsJsonObject(),false);
    }
    public static LoreEntry entry(String id,JsonObject object,boolean hasId)throws IOException {
        var keys=new HashSet<>(Set.of("title","content","mode","triggers","priority","role","enabled","definition","dimension"));if(hasId)keys.add("id");
        if(!object.keySet().equals(keys))throw new IOException("Lore fields");
        try {
            var triggers=new ArrayList<String>();for(var value:object.getAsJsonArray("triggers")){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IOException("Trigger type");triggers.add(value.getAsString());}
            if(!object.getAsJsonPrimitive("priority").isNumber()||!object.getAsJsonPrimitive("enabled").isBoolean())throw new IOException("Lore metadata types");
            return new LoreEntry(id,string(object,"title"),string(object,"content"),LoreEntry.Mode.valueOf(string(object,"mode")),triggers,
                object.get("priority").getAsBigDecimal().intValueExact(),string(object,"role"),object.get("enabled").getAsBoolean(),string(object,"definition"),string(object,"dimension"));
        }catch(RuntimeException e){throw new IOException("Invalid lore entry",e);}
    }
    public static String string(JsonObject object,String key)throws IOException {var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IOException("Lore string type");return value.getAsString();}
    public static JsonObject json(LoreEntry entry) {
        var object=new JsonObject();object.addProperty("title",entry.title());object.addProperty("content",entry.content());object.addProperty("mode",entry.mode().name());
        var triggers=new JsonArray();entry.triggers().forEach(triggers::add);object.add("triggers",triggers);object.addProperty("priority",entry.priority());object.addProperty("role",entry.role());
        object.addProperty("enabled",entry.enabled());object.addProperty("definition",entry.definition());object.addProperty("dimension",entry.dimension());return object;
    }
}
