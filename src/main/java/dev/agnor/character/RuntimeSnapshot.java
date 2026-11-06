package dev.agnor.character;

import java.util.Objects;
import java.util.UUID;

/** Schema v1 instance state. Entity UUID is the stable NPC UUID. */
public record RuntimeSnapshot(UUID id, UUID owner, String definition, int definitionRevision,
                              String dimension, double x, double y, double z, float yaw, float pitch,
                              long revision, boolean deleted) {
    public RuntimeSnapshot {
        Objects.requireNonNull(id); Objects.requireNonNull(owner);
        if (definition == null || !definition.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || definitionRevision < 1)
            throw new IllegalArgumentException("Invalid definition reference");
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || revision < 0)
            throw new IllegalArgumentException("Invalid runtime state");
    }

    public RuntimeSnapshot at(String dimension, double x, double y, double z, float yaw, float pitch) {
        return new RuntimeSnapshot(id, owner, definition, definitionRevision, dimension, x, y, z, yaw, pitch, revision, deleted);
    }
}
