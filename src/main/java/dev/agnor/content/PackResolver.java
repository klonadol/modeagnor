package dev.agnor.content;

import java.io.IOException;
import java.util.*;

/** Resolves an entire candidate generation before any active state changes. */
public final class PackResolver {
    public record Source(String pack, ContentPack.Definition definition) {}
    public static Map<String, Source> resolve(Collection<ContentPack> candidates) throws IOException {
        var packs=new TreeMap<String,ContentPack>();
        for(var pack:candidates) if(packs.putIfAbsent(pack.id(),pack)!=null) throw new IOException("Duplicate pack ID");
        var done=new HashSet<String>(); var visiting=new HashSet<String>();
        for(var pack:packs.values()) visit(pack,packs,done,visiting);
        var ordered=new ArrayList<>(packs.values()); ordered.sort(Comparator.comparingInt(ContentPack::priority).thenComparing(ContentPack::id));
        var definitions=new LinkedHashMap<String,Source>();
        for(var pack:ordered) for(var definition:pack.definitions()) {
            var previous=definitions.get(definition.character().id());
            if(previous!=null && (!pack.overrides().contains(previous.pack()) || pack.priority()<=packs.get(previous.pack()).priority()))
                throw new IOException("Conflicting definition requires explicit higher-priority override");
            definitions.put(definition.character().id(),new Source(pack.id(),definition));
        }
        return Map.copyOf(definitions);
    }
    private static void visit(ContentPack pack,Map<String,ContentPack> packs,Set<String> done,Set<String> visiting) throws IOException {
        if(done.contains(pack.id())) return;
        if(!visiting.add(pack.id())) throw new IOException("Pack dependency cycle");
        for(var dependency:pack.dependencies().entrySet()) {
            var found=packs.get(dependency.getKey());
            if(found==null || !found.version().equals(dependency.getValue())) throw new IOException("Missing/incompatible pack dependency");
            visit(found,packs,done,visiting);
        }
        visiting.remove(pack.id()); done.add(pack.id());
    }
}
