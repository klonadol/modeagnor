package dev.agnor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolved AGNOR directories. Shared content and private server data stay separate. */
public final class DataRoots {
    public final Path root, packs, presets, cache, logs, serverData;
    private DataRoots(Path root) {
        this.root = root; packs = root.resolve("packs"); presets = root.resolve("presets");
        cache = root.resolve("cache"); logs = root.resolve("logs"); serverData = root.resolve("server");
    }
    private DataRoots() { this.root = null; this.packs = null; this.presets = null; this.cache = null; this.logs = null; this.serverData = null; }

    public static Path prepare(Path gameDirectory) throws IOException { return prepareLayout(gameDirectory).root; }

    public static DataRoots prepareLayout(Path gameDirectory) throws IOException {
        Path root = gameDirectory.toAbsolutePath().normalize().resolve("mods").resolve("agnor");
        DataRoots roots = new DataRoots(root);
        for (Path directory : new Path[]{root, roots.packs, roots.presets, roots.cache, roots.logs, roots.serverData}) {
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
                throw new IOException("AGNOR directory is not writable: " + directory);
            }
        }
        return roots;
    }
}
