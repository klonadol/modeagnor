package dev.agnor.character;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable server-side definitions. External packs will provide their own source in P4. */
public final class DefinitionRegistry {
    public static final String DEV_ID = "agnor:dev";
    private volatile Map<String, CharacterDefinition> definitions;

    public DefinitionRegistry(Collection<CharacterDefinition> definitions) {
        var indexed = new LinkedHashMap<String, CharacterDefinition>();
        for (CharacterDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.id(), definition) != null)
                throw new IllegalArgumentException("Duplicate definition ID: " + definition.id());
        }
        this.definitions = Map.copyOf(indexed);
    }

    public static DefinitionRegistry builtIn() {
        return new DefinitionRegistry(java.util.List.of(
                new CharacterDefinition(DEV_ID, 1, 1, "AGNOR development NPC")));
    }

    public CharacterDefinition require(String id) {
        CharacterDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown character definition: " + id);
        return definition;
    }
    public Map<String,CharacterDefinition> snapshot() { return definitions; }
    /** Generation replacement is one volatile write; callers stage and validate beforehand. */
    public void replace(DefinitionRegistry candidate) { definitions=candidate.definitions; }
    public CharacterDefinition available(String id,int revision) {
        var definition=definitions.get(id);
        return definition!=null && definition.revision()==revision ? definition :
            new CharacterDefinition(id,1,revision,"Unavailable character");
    }
}
