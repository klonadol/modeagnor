package dev.agnor.check;

import java.nio.file.*;

final class P1Files {
    static final Path ROOT = Path.of(System.getProperty("agnor.p1.root"));
    static final String WORLD = System.getProperty("agnor.p1.world");
    static final String STAGE = System.getProperty("agnor.p1.stage");
    static String key(String value) { return WORLD + STAGE + "-" + value; }
    static boolean exists(String value) { return Files.exists(ROOT.resolve(value)); }
    static void write(String value, String text) {
        try { Files.createDirectories(ROOT); Files.writeString(ROOT.resolve(value),text); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String read(String value) {
        try { return Files.readString(ROOT.resolve(value)).trim(); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
