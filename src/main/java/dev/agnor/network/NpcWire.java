package dev.agnor.network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minecraft-independent v1 wire contract. No player identity is accepted from the client. */
public final class NpcWire {
    public static final int VERSION = 1, REQUEST_BYTES = 65, MAX_SNAPSHOT_BYTES = 256, MAX_ID = 64;
    public static final int SPAWN = 0, SELECT = 1, DELETE = 2, RELATIONSHIP = 3, INFO = 4;
    public static final int OK = 0, VERSION_MISMATCH = 1, INVALID = 2, WORLD_MISMATCH = 3,
            DENIED = 4, NOT_FOUND = 5, BUSY = 6, UNAVAILABLE = 7, DELETED = 8;
    public static final UUID NIL = new UUID(0, 0);
    private NpcWire() {}

    public record Request(int version, long sequence, int action, UUID world, UUID npc, UUID operation, int affinity) {}
    public record Snapshot(int version, long sequence, int status, UUID world, UUID npc, boolean owner,
                           int affinity, String definition, String dimension, double x, double y, double z,
                           float yaw, float pitch, long revision, boolean deleted) {}

    public static int validate(Request request) {
        if (request.version != VERSION) return VERSION_MISMATCH;
        if (request.sequence < 0 || request.action < SPAWN || request.action > INFO
                || request.affinity < -100 || request.affinity > 100
                || ((request.action == SELECT || request.action == DELETE || request.action == RELATIONSHIP) && request.npc.equals(NIL))
                || ((request.action == SPAWN || request.action == DELETE) && request.operation.equals(NIL))) return INVALID;
        return OK;
    }
    public static Snapshot status(long sequence, int status, UUID world) {
        return new Snapshot(VERSION, sequence, status, world, NIL, false, 0, "", "", 0, 0, 0, 0, 0, 0, false);
    }

    public static byte[] encode(Request request) {
        return write(out -> {
            out.writeInt(request.version); out.writeLong(request.sequence); out.writeByte(request.action);
            uuid(out, request.world); uuid(out, request.npc); uuid(out, request.operation); out.writeInt(request.affinity);
        });
    }
    public static Request request(byte[] bytes) {
        if (bytes.length != REQUEST_BYTES) throw new IllegalArgumentException("NPC request size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return new Request(in.readInt(), in.readLong(), in.readUnsignedByte(), uuid(in), uuid(in), uuid(in), in.readInt());
        } catch (IOException e) { throw new IllegalArgumentException("Invalid NPC request", e); }
    }
    public static byte[] encode(Snapshot snapshot) {
        byte[] bytes = write(out -> {
            out.writeInt(snapshot.version); out.writeLong(snapshot.sequence); out.writeInt(snapshot.status);
            uuid(out, snapshot.world); uuid(out, snapshot.npc); out.writeBoolean(snapshot.owner); out.writeInt(snapshot.affinity);
            identifier(out, snapshot.definition); identifier(out, snapshot.dimension);
            out.writeDouble(snapshot.x); out.writeDouble(snapshot.y); out.writeDouble(snapshot.z);
            out.writeFloat(snapshot.yaw); out.writeFloat(snapshot.pitch); out.writeLong(snapshot.revision); out.writeBoolean(snapshot.deleted);
        });
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("NPC snapshot size");
        return bytes;
    }
    public static Snapshot snapshot(byte[] bytes) {
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("NPC snapshot size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var result = new Snapshot(in.readInt(), in.readLong(), in.readInt(), uuid(in), uuid(in), in.readBoolean(),
                    in.readInt(), identifier(in), identifier(in), in.readDouble(), in.readDouble(), in.readDouble(),
                    in.readFloat(), in.readFloat(), in.readLong(), in.readBoolean());
            if (in.available() != 0 || !Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)
                    || !Float.isFinite(result.yaw) || !Float.isFinite(result.pitch) || result.revision < 0
                    || result.affinity < -100 || result.affinity > 100 || result.status < OK || result.status > DELETED || result.status == 5)
                throw new IllegalArgumentException("Invalid NPC snapshot");
            return result;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid NPC snapshot", e); }
    }
    private static byte[] write(Writer writer) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            writer.write(out); return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("NPC encoding failed", e); }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void identifier(DataOutputStream out, String value) throws IOException {
        if (value.length() > MAX_ID || (!value.isEmpty() && !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")))
            throw new IllegalArgumentException("NPC identifier bounds");
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII); out.writeByte(bytes.length); out.write(bytes);
    }
    private static String identifier(DataInputStream in) throws IOException {
        int size = in.readUnsignedByte(); if (size > MAX_ID) throw new IllegalArgumentException("NPC identifier bounds");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (!value.isEmpty() && !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid NPC identifier");
        return value;
    }
    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
}
