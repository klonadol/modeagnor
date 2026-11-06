package dev.agnor.character;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcAccessTest {
    @Test void onlyOwnerOrAdminCanManageAndInspectionHasDimensionAndRange() {
        var owner = UUID.randomUUID();
        var snapshot = new RuntimeSnapshot(UUID.randomUUID(), owner, "agnor:dev", 1, "minecraft:overworld",0,80,0,0,0,0,false);
        assertTrue(NpcAccess.canManage(owner, false, snapshot));
        assertFalse(NpcAccess.canManage(UUID.randomUUID(), false, snapshot));
        assertTrue(NpcAccess.canManage(UUID.randomUUID(), true, snapshot));
        assertTrue(NpcAccess.nearby("minecraft:overworld",64,80,0,snapshot));
        assertFalse(NpcAccess.nearby("minecraft:overworld",65,80,0,snapshot));
        assertFalse(NpcAccess.nearby("minecraft:the_nether",0,80,0,snapshot));
    }
}
