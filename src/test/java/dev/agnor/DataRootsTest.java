package dev.agnor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DataRootsTest {
    @TempDir Path temporary;

    @Test void supportsCustomGameDirectoryAndRepeatedStartup() throws Exception {
        Path game = temporary.resolve("custom game directory");
        Path root = DataRoots.prepare(game);
        Files.writeString(root.resolve("local-marker"), "preserve");
        assertEquals(game.resolve("mods/agnor"), root);
        assertEquals(root, DataRoots.prepare(game));
        assertEquals("preserve", Files.readString(root.resolve("local-marker")));
    }

    @Test void independentGameDirectoriesHaveIndependentCatalogs() throws Exception {
        Path first = DataRoots.prepare(temporary.resolve("first"));
        Path second = DataRoots.prepare(temporary.resolve("second"));
        Files.writeString(first.resolve("marker"), "first only");
        assertFalse(Files.exists(second.resolve("marker")));
    }

    @Test void refusesFileInPlaceOfCatalogWithoutOverwritingIt() throws Exception {
        Files.createDirectories(temporary.resolve("mods"));
        Path collision = temporary.resolve("mods/agnor");
        Files.writeString(collision, "existing user data");
        assertThrows(java.io.IOException.class, () -> DataRoots.prepare(temporary));
        assertEquals("existing user data", Files.readString(collision));
    }
}
