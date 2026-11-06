package dev.agnor.provider;

import java.util.*;

/** Neutral chat-only contract. Only Main Dialogue has an active consumer in P2. */
public record ProviderRequest(UUID id, List<Message> messages) {
    public ProviderRequest {
        Objects.requireNonNull(id);
        messages = List.copyOf(messages);
        if (messages.isEmpty() || messages.size() > 128 || messages.stream().mapToInt(m -> m.content().length()).sum() > 24000)
            throw new IllegalArgumentException("Invalid provider input bounds");
    }
    public record Message(String role, String content) {
        public Message {
            if (!Set.of("system", "user", "assistant").contains(role) || content == null || content.isBlank() || content.length() > 24000)
                throw new IllegalArgumentException("Invalid chat message");
        }
        @Override public String toString() { return "Message[role=" + role + ",redacted]"; }
    }
    @Override public String toString() { return "ProviderRequest[id=" + id + ",redacted]"; }
}
