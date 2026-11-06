package dev.agnor.network;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.agnor.network.NpcWire.*;

class NpcWireTest {
    private Request valid() { return new Request(VERSION, 42, SPAWN, UUID.randomUUID(), NIL, UUID.randomUUID(), 0); }
    @Test void fixedRequestRoundTripAndExactBound() {
        var request = valid(); var bytes = encode(request);
        assertEquals(REQUEST_BYTES, bytes.length); assertEquals(request, request(bytes));
        assertThrows(IllegalArgumentException.class, () -> request(Arrays.copyOf(bytes, bytes.length+1)));
        assertThrows(IllegalArgumentException.class, () -> request(Arrays.copyOf(bytes, bytes.length-1)));
    }
    @Test void invalidVersionActionSequenceAndOperationAreRejected() {
        var r = valid();
        assertEquals(VERSION_MISMATCH, validate(new Request(2, 1, SPAWN, r.world(), NIL, r.operation(), 0)));
        assertEquals(INVALID, validate(new Request(VERSION, -1, SELECT, r.world(), NIL, NIL, 0)));
        assertEquals(INVALID, validate(new Request(VERSION, 1, 255, r.world(), NIL, NIL, 0)));
        assertEquals(INVALID, validate(new Request(VERSION, 1, SPAWN, r.world(), NIL, NIL, 0)));
        assertEquals(INVALID, validate(new Request(VERSION, 1, SELECT, r.world(), NIL, NIL, 0)));
        assertEquals(INVALID, validate(new Request(VERSION, 1, RELATIONSHIP, r.world(), NIL, NIL, 101)));
    }
    @Test void snapshotRoundTripBoundsAndTruncation() {
        var snapshot = new Snapshot(VERSION, 9, OK, UUID.randomUUID(), UUID.randomUUID(), true, -7,
                "agnor:dev", "minecraft:overworld", 2, 81, 4, 0, 0, 1, false);
        byte[] bytes = encode(snapshot); assertTrue(bytes.length <= MAX_SNAPSHOT_BYTES);
        assertEquals(snapshot, snapshot(bytes));
        var maximum = new Snapshot(VERSION,1,OK,NIL,NIL,false,0,"agnor:"+"a".repeat(58),
                "minecraft:"+"a".repeat(54),0,0,0,0,0,0,false);
        assertEquals(224, encode(maximum).length); assertEquals(maximum,snapshot(encode(maximum)));
        var invalidAffinity = new Snapshot(VERSION,1,OK,NIL,NIL,false,101,"","",0,0,0,0,0,0,false);
        assertThrows(IllegalArgumentException.class, () -> snapshot(encode(invalidAffinity)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Arrays.copyOf(bytes, bytes.length-1)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Arrays.copyOf(bytes, MAX_SNAPSHOT_BYTES+1)));
        assertThrows(IllegalArgumentException.class, () -> encode(new Snapshot(VERSION, 1, OK, NIL, NIL, false, 0,
                "agnor:"+"a".repeat(64), "", 0,0,0,0,0,0,false)));
        bytes[53] = (byte) 65; // first identifier length byte
        byte[] malformed = bytes; assertThrows(IllegalArgumentException.class, () -> snapshot(malformed));
    }
}
