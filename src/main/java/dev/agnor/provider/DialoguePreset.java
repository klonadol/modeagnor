package dev.agnor.provider;

import java.net.URI;
import java.nio.file.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

/** Immutable server-only Main Dialogue v1 snapshot. No credentials are persisted here. */
public record DialoguePreset(int version, boolean enabled, URI endpoint, String model,
                             Duration timeout, int workers, int queue, int maxOutputTokens,
                             int maxResponseBytes, String secretEnv, Adapter adapter, String secretFile, String secretSection) {
    public enum Adapter { OLLAMA, OPENROUTER }
    public DialoguePreset(int version, boolean enabled, URI endpoint, String model, Duration timeout, int workers, int queue,
                          int maxOutputTokens,int maxResponseBytes,String secretEnv) {
        this(version,enabled,endpoint,model,timeout,workers,queue,maxOutputTokens,maxResponseBytes,secretEnv,Adapter.OLLAMA,"","");
    }
    public DialoguePreset {
        if(adapter==null||secretFile==null||secretSection==null||(!secretFile.isEmpty()&&(!Path.of(secretFile).isAbsolute()
                ||!secretEnv.isEmpty()||adapter!=Adapter.OPENROUTER||!secretSection.equals("OpenRouter"))))
            throw new IllegalArgumentException("Invalid host secret reference");
        if (version != 1 || endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !(endpoint.getScheme().equals("https") || (endpoint.getScheme().equals("http")
                    && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))))
            throw new IllegalArgumentException("Invalid Main Dialogue endpoint");
        if (model == null || model.isBlank() || model.length() > 128 || !model.matches("[a-zA-Z0-9_./:-]+")
                || timeout == null || timeout.toMillis() < 100 || timeout.toMillis() > 180000
                || workers < 1 || workers > 4 || queue < 1 || queue > 64
                || maxOutputTokens < 1 || maxOutputTokens > 2048 || maxResponseBytes < 1024 || maxResponseBytes > 262144
                || secretEnv == null || (!secretEnv.isEmpty() && !secretEnv.matches("[A-Z][A-Z0-9_]{0,63}")))
            throw new IllegalArgumentException("Invalid Main Dialogue preset");
    }
    public static DialoguePreset disabled() {
        return new DialoguePreset(1, false, URI.create("http://127.0.0.1:11434"),
                "ternary-bonsai-text-20k:latest", Duration.ofSeconds(90), 1, 8, 256, 65536, "");
    }
    public static DialoguePreset load(Path saveRoot) throws IOException {
        Path file = saveRoot.resolve("agnor/main-dialogue.properties");
        if (!Files.exists(file)) return disabled();
        Properties p = new Properties();
        try (var input = Files.newInputStream(file)) { p.load(input); }
        try {
            if (!java.util.Set.of("true", "false").contains(p.getProperty("enabled", "false"))) throw new IllegalArgumentException();
            return new DialoguePreset(Integer.parseInt(p.getProperty("version", "1")), Boolean.parseBoolean(p.getProperty("enabled", "false")),
                URI.create(p.getProperty("endpoint", "http://127.0.0.1:11434")), p.getProperty("model", disabled().model()),
                Duration.ofMillis(Long.parseLong(p.getProperty("timeoutMillis", "90000"))),
                Integer.parseInt(p.getProperty("workers", "1")), Integer.parseInt(p.getProperty("queue", "8")),
                Integer.parseInt(p.getProperty("maxOutputTokens", "256")), Integer.parseInt(p.getProperty("maxResponseBytes", "65536")),
                p.getProperty("secretEnv", ""),Adapter.valueOf(p.getProperty("adapter","OLLAMA")),
                p.getProperty("secretFile",""),p.getProperty("secretSection",""));
        } catch (RuntimeException e) { throw new IOException("Invalid Main Dialogue configuration"); }
    }
    @Override public String toString() { return "MainDialoguePreset[v=" + version + ",enabled=" + enabled + "]"; }
}
