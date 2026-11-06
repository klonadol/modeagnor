package dev.agnor.dialogue;

import java.util.*;

public record HistoryMessage(ConversationScope scope, UUID id, UUID request, long sequence,
                             String role, String text, long createdAt, String model) {
    public HistoryMessage {
        Objects.requireNonNull(scope); Objects.requireNonNull(id); Objects.requireNonNull(request);
        if (sequence < 1 || !Set.of("user", "assistant").contains(role) || text == null || text.isBlank()
                || text.length() > (role.equals("user") ? 1024 : 4096) || model == null || model.length() > 128)
            throw new IllegalArgumentException("Invalid History record");
    }
    @Override public String toString() { return "HistoryMessage[id=" + id + ",sequence=" + sequence + ",role=" + role + ",redacted]"; }
}
