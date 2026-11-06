package dev.agnor.content;

import com.google.gson.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Builds only verified render resources. Never trusts importer-specific resource hooks from input. */
public final class PackResources {
    public static Map<String,byte[]> build(Collection<ContentPack> packs) throws IOException {
        var result=new HashMap<String,byte[]>();
        for(var pack:packs)for(var asset:pack.assets().values()) {
            if(!Set.of("glb","png").contains(asset.format()))continue;
            result.put("content/"+pack.id()+"/"+asset.path(),asset.format().equals("glb")?model(pack,asset):asset.bytes());
        }
        return Map.copyOf(result);
    }
    private static byte[] model(ContentPack pack,ContentPack.Asset asset) throws IOException {
        byte[] source=asset.bytes();var input=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        int length=input.getInt(12);byte[] json=Arrays.copyOfRange(source,20,20+length);
        var document=PackJson.read(json).getAsJsonObject();stripHooks(document);
        if(document.has("images"))for(var element:document.getAsJsonArray("images")) {
            var image=element.getAsJsonObject();
            if(image.has("uri")) {
                String uri=image.get("uri").getAsString();PackLoader.path(uri);
                int slash=asset.path().lastIndexOf('/');String path=(slash<0?"":asset.path().substring(0,slash+1))+uri;
                if(pack.assets().values().stream().noneMatch(a->a.path().equals(path)&&a.format().equals("png")))throw new IOException("Texture binding");
                var extras=image.has("extras")&&image.get("extras").isJsonObject()?image.getAsJsonObject("extras"):new JsonObject();
                extras.addProperty("resourceLocation","agnor:content/"+pack.id()+"/"+path);image.add("extras",extras);
            }
        }
        byte[] text=document.toString().getBytes(StandardCharsets.UTF_8);int padded=(text.length+3)/4*4;
        byte[] tail=Arrays.copyOfRange(source,20+length,source.length);
        var out=ByteBuffer.allocate(20+padded+tail.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546c67).putInt(2).putInt(out.capacity()).putInt(padded).putInt(0x4e4f534a).put(text);
        while(out.position()<20+padded)out.put((byte)' ');out.put(tail);return out.array();
    }
    private static void stripHooks(JsonElement value) {
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();object.remove("resourceLocation");
            for(var child:object.entrySet())stripHooks(child.getValue());
        } else if(value.isJsonArray())for(var child:value.getAsJsonArray())stripHooks(child);
    }
}
