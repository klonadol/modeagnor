package dev.agnor.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small world-scoped configuration file with validated load and atomic replacement. */
public record WorldConfig(boolean enabled, int autosaveSeconds) {
    public static final WorldConfig DEFAULT = new WorldConfig(true, 30);

    public WorldConfig {
        if (autosaveSeconds < 5 || autosaveSeconds > 3600) throw new IllegalArgumentException("autosaveSeconds must be 5..3600");
    }

    public static WorldConfig load(Path serverSaveRoot) throws IOException {
        Path file = configPath(serverSaveRoot);
        if (!Files.exists(file)) return DEFAULT;
        Properties values = new Properties();
        try (var input = Files.newInputStream(file)) { values.load(input); }
        try {
            if (Integer.parseInt(values.getProperty("version", "1")) != 1) throw new IllegalArgumentException("Unsupported config version");
            String enabled = values.getProperty("enabled", "true");
            if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) throw new IllegalArgumentException("enabled must be true or false");
            return new WorldConfig(Boolean.parseBoolean(enabled),
                    Integer.parseInt(values.getProperty("autosaveSeconds", "30")));
        } catch (RuntimeException e) { throw new IOException("Invalid AGNOR world config: " + file, e); }
    }

    public void save(Path serverSaveRoot) throws IOException {
        Path file = configPath(serverSaveRoot);
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "config-", ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("version", "1");
            values.setProperty("enabled", Boolean.toString(enabled));
            values.setProperty("autosaveSeconds", Integer.toString(autosaveSeconds));
            try (var output = Files.newOutputStream(temp)) { values.store(output, "AGNOR world settings"); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { throw new IOException("Atomic config replacement is unavailable", e); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static Path configPath(Path saveRoot) { return saveRoot.toAbsolutePath().normalize().resolve("agnor").resolve("config.properties"); }
}
