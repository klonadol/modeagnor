package dev.agnor.content;

import dev.agnor.character.CharacterDefinition;
import java.util.*;

/** Fully validated passive content. No source path is retained or reopened after validation. */
public record ContentPack(String id, String version, int priority, Map<String, String> dependencies,
                          Set<String> overrides, Provenance provenance, Map<String, Asset> assets,
                          List<Definition> definitions, String digest, byte[] manifest) {
    public ContentPack {
        dependencies = Map.copyOf(dependencies); overrides = Set.copyOf(overrides);
        assets = Map.copyOf(assets); definitions = List.copyOf(definitions);
        manifest = manifest.clone();
    }
    @Override public byte[] manifest() { return manifest.clone(); }
    public enum Rights { ALLOWED, LOCAL_ONLY, UNKNOWN }
    public record Provenance(String source, String author, String license, Rights rights) {}
    public record Definition(CharacterDefinition character, String prompt, String model, String rig) {}
    public record Asset(String id, String path, String format, String sha256, byte[] bytes) {
        public Asset { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        public int size() { return bytes.length; }
    }
}
