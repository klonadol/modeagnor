package dev.agnor.character;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionRegistryTest {
    @Test void builtInDefinitionHasStableVersionedIdentity() {
        var definition = DefinitionRegistry.builtIn().require(DefinitionRegistry.DEV_ID);
        assertEquals(1, definition.schemaVersion());
        assertEquals(definition, DefinitionRegistry.builtIn().require(DefinitionRegistry.DEV_ID));
        assertThrows(IllegalArgumentException.class, () -> DefinitionRegistry.builtIn().require("missing:npc"));
    }

    @Test void rejectsConflictingAndUnsupportedDefinitions() {
        var definition = DefinitionRegistry.builtIn().require(DefinitionRegistry.DEV_ID);
        assertThrows(IllegalArgumentException.class, () -> new DefinitionRegistry(java.util.List.of(definition, definition)));
        assertThrows(IllegalArgumentException.class, () -> new CharacterDefinition("agnor:dev", 2, 1, "dev"));
    }
}
