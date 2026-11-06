package dev.agnor.provider;

import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Opt-in real inference through production gateway, not a mock or availability probe. */
class ProviderLiveCheck {
    @Test void ollamaMainDialogue() throws Exception {
        assumeTrue(Boolean.getBoolean("agnor.providerLive"));
        var preset = new DialoguePreset(1, true, URI.create("http://127.0.0.1:11434"),
                "ternary-bonsai-text-20k:latest", Duration.ofSeconds(90), 1, 8, 128, 65536, "");
        try (var gateway = new ProviderGateway(preset)) {
            var result = gateway.submit(new ProviderRequest(UUID.randomUUID(), List.of(
                    new ProviderRequest.Message("system", "You are a friendly Minecraft companion. Give a short direct answer."),
                    new ProviderRequest.Message("user", "Hello! Can you hear me? Reply in one short sentence."))))
                    .result().toCompletableFuture().get(95, TimeUnit.SECONDS);
            Files.createDirectories(Path.of("build/p2-evidence"));
            Files.writeString(Path.of("build/p2-evidence/ollama-gateway.txt"), result + "\nmodel=" + preset.model()
                    + "\nthink=false\nstream=false\nreply=" + result.text());
            assertEquals(ProviderResult.Error.NONE, result.error()); assertFalse(result.text().isBlank());
        }
    }
}
