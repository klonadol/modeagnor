package dev.agnor.probe;

import dev.agnor.Agnor;
import dev.agnor.network.ProbeProtocol;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Local, opt-in scripted client; no production hooks or extra protocol messages. */
public final class ProbeHarness implements ClientModInitializer {
    private final List<ProbeProtocol.Snapshot> replies = new ArrayList<>();
    private final String label = System.getProperty("agnor.probe.label");
    private final long base = "A".equals(label) ? 1000 : 2000;
    private int ticks, scenarioTicks, previousServerTick = -1;
    private boolean started, finished;

    @Override public void onInitializeClient() {
        ClientPlayNetworking.unregisterGlobalReceiver(ProbeProtocol.Snapshot.ID.id());
        if (!ClientPlayNetworking.registerGlobalReceiver(ProbeProtocol.Snapshot.ID, (reply, context) -> {
            require(reply.version() == ProbeProtocol.VERSION, "response version");
            require(reply.status() != ProbeProtocol.OK || reply.serverTick() >= previousServerTick, "server tick order");
            if (reply.status() == ProbeProtocol.OK) previousServerTick = reply.serverTick();
            replies.add(reply);
            Agnor.LOGGER.info("PHASE0 {} received sequence={} status={} tick={}", label, reply.sequence(), reply.status(), reply.serverTick());
        })) throw new IllegalStateException("Probe receiver already installed");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            require(++ticks < 3600, "two-player scenario timed out");
            if (client.getNetworkHandler() == null || client.player == null) return;
            if (!started) {
                if (client.getNetworkHandler().getPlayerList().size() < 2) return;
                require(ClientPlayNetworking.canSend(ProbeProtocol.Request.ID), "probe channel missing");
                started = true;
                Agnor.LOGGER.info("PHASE0 {} observed two players; uuid={}", label, client.player.getUuid());
            }
            switch (++scenarioTicks) {
                case 100 -> send(ProbeProtocol.VERSION + 1, base + 1);
                case 180 -> send(ProbeProtocol.VERSION, -base);
                case 260 -> {
                    for (int i = 0; i < 8; i++) send(ProbeProtocol.VERSION, base + 10 + i);
                }
                case 340 -> send(ProbeProtocol.VERSION, base + 10); // read-only replay is safe
                case 420 -> {
                    require(replies.size() == 5, "expected exactly five replies (JOIN, two errors, burst, replay): " + replies);
                    check(0, 0, ProbeProtocol.OK);
                    check(1, base + 1, ProbeProtocol.VERSION_MISMATCH);
                    check(2, -base, ProbeProtocol.INVALID_SEQUENCE);
                    check(3, base + 10, ProbeProtocol.OK);
                    check(4, base + 10, ProbeProtocol.OK);
                    Agnor.LOGGER.info("PHASE0 {} PASS: two players, addressed replies, version, sequence, burst limit, safe replay", label);
                }
                case 620 -> { // keep the connection alive while the other client finishes
                    require(replies.size() == 5, "unexpected late/broadcast reply");
                    finished = true;
                    client.scheduleStop();
                }
                default -> { }
            }
        });
    }

    private void send(int version, long sequence) {
        ClientPlayNetworking.send(new ProbeProtocol.Request(version, sequence));
    }

    private void check(int index, long sequence, int status) {
        var reply = replies.get(index);
        require(reply.sequence() == sequence && reply.status() == status, "unexpected/addressed response " + reply);
        require(status == ProbeProtocol.OK ? reply.serverTick() > 0 : reply.serverTick() == 0, "tick visibility");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("PHASE0 " + label + " FAIL: " + message);
    }
}
