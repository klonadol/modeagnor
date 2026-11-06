package dev.agnor.content;

import dev.agnor.character.*;
import dev.agnor.motion.MotionRig;
import java.io.IOException;
import java.util.*;

/** Candidate is completely resolved (including CPU rig capability) before Registry publication. */
public record PackGeneration(Map<String,ContentPack> packs, Map<String,Binding> bindings, DefinitionRegistry registry,Map<String,dev.agnor.lore.LoreRecord> lorebook) {
    public enum Capability { STATIC, MOTION, PLACEHOLDER }
    public record Binding(ContentPack pack,ContentPack.Definition definition,Capability capability,MotionRig rig,List<String> warnings) {
        public Binding { warnings=List.copyOf(warnings); }
        public String resourcePrefix() { return "content/"+pack.id()+"/"; }
    }
    public PackGeneration { packs=Map.copyOf(packs);bindings=Map.copyOf(bindings);lorebook=Map.copyOf(lorebook); }
    public static PackGeneration stage(Collection<ContentPack> candidates) throws IOException {
        var sources=PackResolver.resolve(candidates);var packs=new TreeMap<String,ContentPack>();
        long bytes=0;
        if(candidates.size()>32)throw new IOException("Active pack count limit");
        for(var pack:candidates){packs.put(pack.id(),pack);for(var asset:pack.assets().values())bytes+=asset.size();}
        if(bytes>512L*1024*1024)throw new IOException("Active generation byte limit");
        var definitions=new LinkedHashMap<>(DefinitionRegistry.builtIn().snapshot());var bindings=new HashMap<String,Binding>();
        for(var source:sources.values()) {
            var pack=packs.get(source.pack());var definition=source.definition();
            var warnings=new ArrayList<String>();MotionRig rig=null;
            Capability capability=definition.model().isEmpty()?Capability.PLACEHOLDER:Capability.STATIC;
            if(!definition.rig().isEmpty()) {
                if(definition.model().isEmpty())throw new IOException("Motion rig requires a model");
                var model=pack.assets().get(definition.model());int slash=model.path().lastIndexOf('/');
                String directory=slash<0?"":model.path().substring(0,slash+1);
                if(directory.isEmpty())throw new IOException("Motion parts require a model directory");
                rig=MotionRig.read(pack.assets().get(definition.rig()).bytes(),"content/"+pack.id()+"/"+directory.substring(0,directory.length()-1));
                for(int i=0;i<rig.parts().size();i++) {
                    String path=directory+"part-%03d.glb".formatted(i);
                    if(pack.assets().values().stream().noneMatch(a->a.path().equals(path)&&a.format().equals("glb")))throw new IOException("Missing rig model part");
                }
                try { rig.locomotion();rig.secondary();rig.face();capability=Capability.MOTION; }
                catch(IllegalArgumentException unsupported) { warnings.add("Motion channels unsupported; static model fallback"); }
            }
            if(capability!=Capability.MOTION)warnings.add("Body/face channels unavailable; static/placeholder fallback");
            definitions.put(definition.character().id(),definition.character());
            bindings.put(definition.character().id(),new Binding(pack,definition,capability,rig,warnings));
        }
        return new PackGeneration(packs,bindings,new DefinitionRegistry(definitions.values()),dev.agnor.lore.PackLore.resolve(candidates));
    }
}
