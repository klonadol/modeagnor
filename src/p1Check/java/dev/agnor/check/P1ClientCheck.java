package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.network.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import static dev.agnor.network.NpcWire.*;

/** Drives the real command and play networking paths; filesystem only coordinates fixtures. */
public final class P1ClientCheck implements ClientModInitializer {
    private final String role = System.getProperty("agnor.p1.role");
    private final boolean integrated = Boolean.getBoolean("agnor.p1.integrated");
    private final Map<Long,CompletableFuture<Snapshot>> pending = new HashMap<>();
    private final List<Wait> waits = new ArrayList<>();
    private UUID world, npc;
    private long sequence = "A".equals(role) ? 100 : 200;
    private int ticks, finishTicks;
    private boolean started, finished;
    @Override public void onInitializeClient() {
        ClientPlayNetworking.unregisterGlobalReceiver(NpcProtocol.Snapshot.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(NpcProtocol.Snapshot.ID, (payload, context) -> {
            Snapshot snapshot = payload.value();
            CompletableFuture<Snapshot> future = pending.remove(snapshot.sequence());
            if (future == null) { fail(new IllegalStateException("Unexpected/broadcast NPC snapshot sequence="+snapshot.sequence())); return; }
            future.complete(snapshot);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            for (Wait wait : List.copyOf(waits)) if (wait.condition.getAsBoolean()) { waits.remove(wait); wait.future.complete(null); }
            if (finished) { if (++finishTicks > 100) client.scheduleStop(); return; }
            if (ticks > 5000) { fail(new IllegalStateException("P1 client timeout")); return; }
            if (started || client.player == null || client.getNetworkHandler() == null) return;
            if (!integrated && client.getNetworkHandler().getPlayerList().size()<2) return;
            started = true;
            pause().thenCompose(v -> packet(INFO,NIL,0)).thenCompose(info -> {
                expect(info,OK); world = info.world();
                if ("1".equals(P1Files.STAGE)) {
                    if (role.equals("A")) P1Files.write("world-"+P1Files.WORLD,world.toString());
                    return stageOne();
                }
                require(world.toString().equals(P1Files.read("world-"+P1Files.WORLD)),"WorldId changed after restart");
                npc = UUID.fromString(P1Files.read("npc-"+P1Files.WORLD));
                return stageTwo();
            }).whenComplete((value,error) -> {
                if (error != null) fail(error);
                else {
                    P1Files.write(P1Files.key("done-"+role),"PASS"); finished=true;
                    Agnor.LOGGER.info("P1 CLIENT PASS role={} world={} stage={}",role,P1Files.WORLD,P1Files.STAGE);
                }
            });
        });
    }
    private CompletableFuture<Void> stageOne() {
        if (role.equals("A")) {
            CompletableFuture<Void> scope = CompletableFuture.completedFuture(null);
            if (P1Files.WORLD.equals("B")) scope = packetWith(new Request(VERSION,++sequence,DELETE,
                    UUID.fromString(P1Files.read("world-A")),UUID.fromString(P1Files.read("npc-A")),UUID.randomUUID(),0))
                    .thenAccept(reply -> expect(reply,WORLD_MISMATCH));
            return scope.thenCompose(v -> command("agnor spawn")).thenCompose(reply -> {
                expect(reply,OK); require(reply.owner(),"spawn ownership"); npc=reply.npc();
                P1Files.write("npc-"+P1Files.WORLD,npc.toString());
                return command("agnor relationship "+npc+" "+ownerAffinity());
            }).thenCompose(reply -> {
                expect(reply,OK); require(reply.affinity()==ownerAffinity(),"owner relationship write");
                P1Files.write(P1Files.key("owner-ready"),"yes");
                return integrated ? CompletableFuture.completedFuture(null) : until(() -> P1Files.exists(P1Files.key("b-actions")));
            }).thenCompose(v -> command("agnor select "+npc)).thenCompose(reply -> {
                expect(reply,OK); require(reply.affinity()==ownerAffinity(),"other player overwrote owner relationship");
                return packetWith(new Request(VERSION+1,++sequence,DELETE,world,npc,UUID.randomUUID(),0));
            }).thenCompose(reply -> {
                expect(reply,VERSION_MISMATCH);
                return packetWith(new Request(VERSION,++sequence,DELETE,UUID.randomUUID(),npc,UUID.randomUUID(),0));
            }).thenCompose(reply -> {
                expect(reply,WORLD_MISMATCH);
                return packetWith(new Request(VERSION,++sequence,255,world,npc,NIL,0));
            }).thenAccept(reply -> expect(reply,INVALID));
        }
        return until(() -> P1Files.exists(P1Files.key("owner-ready"))).thenCompose(v -> {
            npc=UUID.fromString(P1Files.read("npc-"+P1Files.WORLD)); return packet(SELECT,npc,0);
        }).thenCompose(reply -> {
            expect(reply,OK); require(!reply.owner() && reply.affinity()==0,"private owner relationship leaked");
            return packet(DELETE,npc,0);
        }).thenCompose(reply -> { expect(reply,DENIED); return packet(RELATIONSHIP,npc,playerAffinity()); })
          .thenCompose(reply -> { expect(reply,OK); return packet(SELECT,npc,0); })
          .thenCompose(reply -> {
              expect(reply,OK); require(reply.affinity()==playerAffinity(),"player relationship lost");
              P1Files.write(P1Files.key("b-actions"),"yes");
              return until(() -> P1Files.exists(P1Files.key("done-A")));
          });
    }
    private CompletableFuture<Void> stageTwo() {
        return packet(SELECT,npc,0).thenCompose(reply -> {
            expect(reply,OK); require(reply.affinity()==(role.equals("A") ? ownerAffinity():playerAffinity()),"restart relationship");
            require(reply.npc().equals(npc),"restart NPC identity");
            return burst().thenCompose(v -> afterRestartSelection());
        });
    }
    private CompletableFuture<Void> afterRestartSelection() {
            if (integrated) return command("agnor delete").thenAccept(r -> expect(r,OK));
            if (role.equals("A")) return command("agnor spawn").thenCompose(spawn -> {
                expect(spawn,OK); require(!spawn.npc().equals(npc),"new NPC reused identity");
                return command("agnor delete");
            }).thenCompose(deleted -> {
                expect(deleted,OK); P1Files.write(P1Files.key("owner-delete"),"yes");
                return until(() -> P1Files.exists(P1Files.key("admin-delete")));
            });
            return packet(DELETE,npc,0).thenCompose(denied -> {
                expect(denied,DENIED); P1Files.write(P1Files.key("admin-request"),"yes");
                return until(() -> P1Files.exists(P1Files.key("admin-granted")));
            }).thenCompose(v -> command("agnor delete")).thenCompose(deleted -> {
                expect(deleted,OK); P1Files.write(P1Files.key("admin-delete"),"yes");
                return until(() -> P1Files.exists(P1Files.key("owner-delete")));
            });
    }
    private CompletableFuture<Void> burst() {
        var first = packet(SELECT,npc,0); var second = packet(SELECT,npc,0);
        return first.thenCombine(second,(ok,busy) -> { expect(ok,OK); expect(busy,BUSY); return null; });
    }
    private int ownerAffinity() { return P1Files.WORLD.equals("B")?31:21; }
    private int playerAffinity() { return P1Files.WORLD.equals("B")?-17:-7; }
    private CompletableFuture<Snapshot> packet(int action,UUID id,int affinity) {
        return packetWith(new Request(VERSION,++sequence,action,world==null?NIL:world,id,UUID.randomUUID(),affinity));
    }
    private CompletableFuture<Snapshot> packetWith(Request request) {
        var future = new CompletableFuture<Snapshot>(); pending.put(request.sequence(),future);
        ClientPlayNetworking.send(new NpcProtocol.Request(request));
        return future.thenCompose(snapshot -> pause().thenApply(v -> snapshot));
    }
    private CompletableFuture<Snapshot> command(String command) {
        var future = new CompletableFuture<Snapshot>(); pending.put(0L,future);
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command);
        return future.thenCompose(snapshot -> pause().thenApply(v -> snapshot));
    }
    private CompletableFuture<Void> pause() { int until=ticks+40; return until(() -> ticks>=until); }
    private CompletableFuture<Void> until(BooleanSupplier condition) {
        var future=new CompletableFuture<Void>(); waits.add(new Wait(condition,future)); return future;
    }
    private void fail(Throwable error) {
        if (finished) return;
        finished=true; P1Files.write(P1Files.key("FAILED-"+role),error.toString());
        Agnor.LOGGER.error("P1 CLIENT FAIL role={} world={} stage={}",role,P1Files.WORLD,P1Files.STAGE,error);
    }
    private static void expect(Snapshot reply,int status) {
        require(reply.version()==VERSION && reply.status()==status,"expected status="+status+" got="+reply.status());
    }
    private static void require(boolean result,String error) { if(!result) throw new IllegalStateException(error); }
    private record Wait(BooleanSupplier condition,CompletableFuture<Void> future) {}
}
