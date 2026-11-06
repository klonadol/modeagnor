package dev.agnor.dialogue;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server derives player from the authenticated connection, never from a dialogue packet. */
public record ConversationScope(UUID world, UUID npc, UUID player) {
    public ConversationScope { Objects.requireNonNull(world); Objects.requireNonNull(npc); Objects.requireNonNull(player); }
    public UUID conversation() { return UUID.nameUUIDFromBytes((world + "/" + npc + "/" + player).getBytes(StandardCharsets.UTF_8)); }
}
