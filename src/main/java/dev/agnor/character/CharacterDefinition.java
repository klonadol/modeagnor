package dev.agnor.character;

/** Immutable definition; identity and runtime state belong to individual NPC instances. */
public record CharacterDefinition(String id, int schemaVersion, int revision, String displayName, String prompt) {
    public CharacterDefinition(String id, int schemaVersion, int revision, String displayName) {
        this(id,schemaVersion,revision,displayName,"");
    }
    public CharacterDefinition {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid character definition ID");
        if (schemaVersion != 1 || revision < 1) throw new IllegalArgumentException("Unsupported definition version");
        if (displayName == null || displayName.isBlank() || displayName.length() > 128)
            throw new IllegalArgumentException("Invalid character display name");
        if (prompt == null || prompt.length() > 8192 || prompt.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid character prompt");
    }
}
