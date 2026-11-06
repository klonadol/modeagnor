package dev.agnor.lore;

import dev.agnor.content.*;
import java.io.IOException;
import java.util.*;

public final class PackLore {
    public static Map<String,LoreRecord> resolve(Collection<ContentPack> candidates)throws IOException {
        var sorted=candidates.stream().sorted(Comparator.comparingInt(ContentPack::priority).thenComparing(ContentPack::id)).toList();
        var entries=new LinkedHashMap<String,LoreRecord>();var owners=new HashMap<String,ContentPack>();
        for(var pack:sorted) {
            var localIds=new HashSet<String>();int count=0;
            for(var asset:pack.assets().values())if(asset.format().equals("json")) {
                var value=PackJson.read(asset.bytes());if(!value.isJsonObject())continue;var object=value.getAsJsonObject();
                if(!object.has("type")||!object.get("type").isJsonPrimitive()||!object.get("type").getAsString().equals("agnor:lorebook"))continue;
                if(asset.size()>1_048_576||!object.keySet().equals(Set.of("type","schemaVersion","entries"))||!object.getAsJsonPrimitive("schemaVersion").isNumber()||object.get("schemaVersion").getAsBigDecimal().intValueExact()!=1)throw new IOException("Lore asset schema/size");
                for(var element:object.getAsJsonArray("entries")) {
                    var json=element.getAsJsonObject();String id=LoreJson.string(json,"id");
                    if(++count>256||!localIds.add(id))throw new IOException("Lore count/duplicate ID");
                    var entry=LoreJson.entry(id,json,true);var previous=owners.get(id);
                    if(previous==null&&!id.startsWith(pack.id()+":"))throw new IOException("Lore ID namespace");
                    if(previous!=null&&(!pack.overrides().contains(previous.id())||pack.priority()<=previous.priority()))throw new IOException("Lore override conflict");
                    entries.put(id,new LoreRecord(entry,1,0,"pack:"+pack.id()+"@"+pack.digest()+"/"+id,false));owners.put(id,pack);
                    if(entries.size()>2048)throw new IOException("Generation lore limit");
                }
            }
        }
        return Map.copyOf(entries);
    }
    /** Precedence: pack < NPC shared local < this player's local; disabled entries suppress lower sources. */
    public static List<LoreRecord> effective(Map<String,LoreRecord> pack,List<LoreRecord> shared,List<LoreRecord> personal) {
        var merged=new HashMap<>(pack);for(var layer:List.of(shared,personal))for(var row:layer)if(!row.deleted())merged.put(row.entry().id(),row);
        return merged.values().stream().sorted(Comparator.comparingInt((LoreRecord r)->r.entry().priority()).reversed().thenComparing(r->r.entry().id())).toList();
    }
}
