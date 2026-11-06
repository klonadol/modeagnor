package dev.agnor.character;

import java.util.UUID;

/** Explicit permission policy for P1: public nearby inspection; owner/admin management. */
public final class NpcAccess {
    private NpcAccess() {}
    public static boolean canManage(UUID actor, boolean administrator, RuntimeSnapshot snapshot) {
        return administrator || snapshot.owner().equals(actor);
    }
    public static boolean nearby(String dimension, double x, double y, double z, RuntimeSnapshot snapshot) {
        double dx = x - snapshot.x(), dy = y - snapshot.y(), dz = z - snapshot.z();
        return dimension.equals(snapshot.dimension()) && dx*dx + dy*dy + dz*dz <= 64*64;
    }
}
