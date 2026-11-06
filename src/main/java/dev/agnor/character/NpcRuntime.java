package dev.agnor.character;

import dev.agnor.storage.NpcRepository;
import dev.agnor.storage.WorldConfig;
import dev.agnor.storage.WorldStorage;
import dev.agnor.Agnor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.*;

/** Server-owned P1 lifecycle. SQLite owns durable identity; placeholders never write entity NBT. */
public final class NpcRuntime implements AutoCloseable {
    private final MinecraftServer server;
    private final UUID worldId;
    private final DefinitionRegistry definitions;
    private final dev.agnor.content.ContentService content;
    private final NpcRepository repository;
    private final dev.agnor.storage.HistoryRepository history;
    private final dev.agnor.storage.LoreRepository lore;
    private final WorldStorage storage;
    private final dev.agnor.storage.MemoryPreferences preferences;
    private boolean maintenance;
    private final dev.agnor.dialogue.ContextBuilder contextBuilder = new dev.agnor.dialogue.ContextBuilder();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> new Thread(runnable, "agnor-world-storage"), new ThreadPoolExecutor.AbortPolicy());
    private final Map<UUID, RuntimeSnapshot> desired = new HashMap<>();
    private final Map<UUID, Placeholder> active = new HashMap<>();
    private final Set<UUID> suspended = new HashSet<>();
    private final int saveInterval;
    private boolean closed;
    private int ticks;

    public NpcRuntime(MinecraftServer server, WorldStorage storage, WorldConfig config) {
        this.server = server;this.storage=storage;preferences=new dev.agnor.storage.MemoryPreferences(storage);content=Agnor.content(server);definitions=content==null?DefinitionRegistry.builtIn():content.registry();
        worldId = storage.worldId(); repository = new NpcRepository(storage, definitions);
        history = new dev.agnor.storage.HistoryRepository(storage);
        lore = new dev.agnor.storage.LoreRepository(storage);
        saveInterval = config.autosaveSeconds() * 20;
    }

    public CompletableFuture<Void> start() {
        checkThread();
        return submit(() -> { history.recoverInterrupted(worldId); return repository.active(worldId); }).thenAcceptAsync(snapshots -> {
            if (closed) return;
            snapshots.forEach(s -> desired.put(s.id(), s));
            tick();
        }, server);
    }

    public CompletableFuture<RuntimeSnapshot> create(UUID operation, UUID owner, String definitionId,
                                                    ServerWorld world, double x, double y, double z) {
        checkThread();
        var definition = definitions.require(definitionId);
        if (world.getServer() != server) throw new IllegalArgumentException("Wrong server world");
        var initial = new RuntimeSnapshot(UUID.randomUUID(), owner, definition.id(), definition.revision(),
                world.getRegistryKey().getValue().toString(), x, y, z, 0, 0, 0, false);
        return submit(() -> repository.create(worldId, operation, initial)).thenApplyAsync(snapshot -> {
            if (!closed && !snapshot.deleted()) { desired.put(snapshot.id(), snapshot); suspended.remove(snapshot.id()); activate(snapshot); }
            return snapshot;
        }, server);
    }

    public CompletableFuture<Void> delete(UUID operation, UUID id) {
        checkThread();
        return submit(() -> { repository.delete(worldId, operation, id); return null; }).thenAcceptAsync(ignored -> {
            if (closed) return;
            desired.remove(id); suspended.remove(id);
            Placeholder entity = active.remove(id);
            if (entity != null) entity.discard();
        }, server);
    }

    public CompletableFuture<Void> unload(UUID id) {
        checkThread();
        RuntimeSnapshot snapshot = capture(id);
        suspended.add(id);
        Placeholder entity = active.remove(id);
        if (entity != null) entity.discard();
        return persist(snapshot);
    }

    public CompletableFuture<RuntimeSnapshot> load(UUID id) {
        checkThread();
        return submit(() -> repository.find(worldId, id)).thenApplyAsync(snapshot -> {
            if (snapshot.deleted()) throw new CompletionException(new IllegalStateException("NPC deleted"));
            if (!closed) { desired.put(id, snapshot); suspended.remove(id); activate(snapshot); }
            return snapshot;
        }, server);
    }

    public CompletableFuture<Void> relationship(UUID npc, UUID player, int affinity) {
        checkThread();
        return submit(() -> { repository.relationship(worldId, npc, player, affinity); return null; });
    }
    public CompletableFuture<Integer> relationship(UUID npc, UUID player) {
        checkThread(); return submit(() -> repository.relationship(worldId, npc, player));
    }

    public UUID worldId() { return worldId; }
    public dev.agnor.content.PackGeneration.Binding binding(UUID id) {
        checkThread();var snapshot=desired.get(id);if(snapshot==null||content==null)return null;
        var binding=content.active().bindings().get(snapshot.definition());
        return binding!=null&&binding.definition().character().revision()==snapshot.definitionRevision()?binding:null;
    }
    public boolean external(UUID id) {checkThread();var snapshot=desired.get(id);return binding(id)!=null || snapshot!=null&&!snapshot.definition().equals(DefinitionRegistry.DEV_ID);}
    public void refreshContent() {
        checkThread();for(var entry:active.entrySet()) {
            var snapshot=desired.get(entry.getKey());var entity=entry.getValue();
            entity.setCustomName(Text.literal(definitions.available(snapshot.definition(),snapshot.definitionRevision()).displayName()));
            var binding=binding(entry.getKey());entity.content(binding==null?(external(entry.getKey())?"missing":""):binding.pack().digest()+"|"+snapshot.definition()+"|"+snapshot.definitionRevision());
        }
    }
    public CompletableFuture<RuntimeSnapshot> redefine(UUID id,String definition) {
        checkThread();definitions.require(definition);
        var dialogue=Agnor.dialogue(server);dialogue.contentChanged(id);
        return submit(()->repository.redefine(worldId,id,definition)).thenApplyAsync(snapshot->{
            if(!closed){desired.put(id,snapshot);refreshContent();}return snapshot;
        },server);
    }
    public List<NpcEntity> activeEntities() { checkThread(); return List.copyOf(active.values()); }
    public <T> CompletableFuture<T> history(HistoryWork<T> work) { checkThread(); return submit(() -> work.run(history)); }
    @FunctionalInterface public interface HistoryWork<T> { T run(dev.agnor.storage.HistoryRepository history) throws java.sql.SQLException; }
    public <T> CompletableFuture<T> lore(LoreWork<T> work){checkThread();return submit(()->work.run(lore));}
    public CompletableFuture<dev.agnor.storage.MemoryPreferences.Value> preferences(dev.agnor.dialogue.ConversationScope scope,int fallback){checkThread();return submit(()->preferences.get(scope,fallback));}
    public CompletableFuture<Void> preferences(dev.agnor.dialogue.ConversationScope scope,dev.agnor.storage.MemoryPreferences.Value value){checkThread();return submit(()->{preferences.set(scope,value);return null;});}
    public CompletableFuture<String> backupMemory(){checkThread();return submit(()->{String name="backup-"+UUID.randomUUID()+".db";storage.backup(storage.databasePath().getParent().resolve("backups").resolve(name));return name;});}
    public CompletableFuture<Void> restoreMemory(String name) {
        checkThread();if(name==null||!name.matches("[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,99}\\.db")||name.contains("..")||maintenance||closed)return CompletableFuture.failedFuture(new IllegalArgumentException("Backup name/session"));
        Agnor.dialogue(server).contentChanged(null);maintenance=true;Exception[] problem=new Exception[1];
        return enqueue(()->{
            try{storage.backup(storage.databasePath().getParent().resolve("backups/before-restore-"+UUID.randomUUID()+".db"));storage.restore(storage.databasePath().getParent().resolve("backups").resolve(name));history.recoverInterrupted(worldId);}
            catch(Exception e){problem[0]=e;}
            return repository.active(worldId);
        }).thenAcceptAsync(snapshots->{
            if(closed)return;
            active.values().forEach(Entity::discard);active.clear();desired.clear();suspended.clear();snapshots.forEach(s->desired.put(s.id(),s));maintenance=false;tick();
            if(problem[0]!=null)throw new CompletionException(problem[0]);
        },server).whenCompleteAsync((v,error)->{if(error!=null&&maintenance){Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();if(cause instanceof RejectedExecutionException){maintenance=false;return;}Agnor.LOGGER.error("AGNOR restore reconciliation failed; writes remain paused");server.stop(false);}},server);
    }
    @FunctionalInterface public interface LoreWork<T>{T run(dev.agnor.storage.LoreRepository lore)throws Exception;}
    public CompletableFuture<List<dev.agnor.lore.LoreRecord>> loreEntries(dev.agnor.dialogue.ConversationScope scope){
        checkThread();var packs=content==null?Map.<String,dev.agnor.lore.LoreRecord>of():content.active().lorebook();
        return lore(repository->dev.agnor.lore.PackLore.effective(packs,
            repository.list(new dev.agnor.storage.LoreRepository.Scope(scope.world(),scope.npc(),dev.agnor.storage.LoreRepository.SHARED),scope.player()),
            repository.list(new dev.agnor.storage.LoreRepository.Scope(scope.world(),scope.npc(),scope.player()),scope.player())));
    }
    public CompletableFuture<dev.agnor.dialogue.ContextBuilder.Context> context(dev.agnor.dialogue.ConversationScope scope,
            UUID request, RuntimeSnapshot snapshot, dev.agnor.dialogue.ContextBuilder.Policy policy) {
        checkThread();
        var definition=definitions.available(snapshot.definition(),snapshot.definitionRevision());
        var packs=content==null?Map.<String,dev.agnor.lore.LoreRecord>of():content.active().lorebook();
        return submit(() -> {
            dev.agnor.dialogue.ContextSourceRegistry.Snapshot knowledge;
            var selectedPolicy=new dev.agnor.dialogue.ContextBuilder.Policy(preferences.get(scope,policy.recentN()).recentN(),policy.maxInputBytes());
            try {var rows=dev.agnor.lore.PackLore.effective(packs,
                lore.list(new dev.agnor.storage.LoreRepository.Scope(scope.world(),scope.npc(),dev.agnor.storage.LoreRepository.SHARED),scope.player()),
                lore.list(new dev.agnor.storage.LoreRepository.Scope(scope.world(),scope.npc(),scope.player()),scope.player()));
                knowledge=dev.agnor.dialogue.ContextSourceRegistry.Snapshot.available(scope,rows);
            }catch(java.sql.SQLException unavailable){knowledge=new dev.agnor.dialogue.ContextSourceRegistry.Snapshot(scope,dev.agnor.dialogue.ContextBuilder.Status.TEMPORARILY_UNAVAILABLE,System.currentTimeMillis(),List.of());}
            return contextBuilder.build(scope, request, definition, snapshot,
                repository.relationship(scope.world(), scope.npc(), scope.player()), history.recent(scope, selectedPolicy.recentN()),
                history.find(scope,request).inbound(), selectedPolicy,knowledge,dev.agnor.dialogue.ContextSourceRegistry.Profile.mainDialogue());
        });
    }
    public CompletableFuture<RuntimeSnapshot> inspect(UUID id) {
        checkThread();
        return submit(() -> repository.find(worldId, id)).thenApplyAsync(snapshot -> {
            if (closed) throw new CompletionException(new IllegalStateException("NPC runtime closed"));
            Placeholder entity = active.get(id);
            return entity == null || entity.isRemoved() ? snapshot : snapshot.at(
                    entity.getWorld().getRegistryKey().getValue().toString(), entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
        }, server);
    }

    public void tick() {
        checkThread();
        if (closed||maintenance) return;
        for (var snapshot : List.copyOf(desired.values())) {
            Placeholder entity = active.get(snapshot.id());
            if (entity != null && entity.isRemoved()) {
                RuntimeSnapshot latest = capture(snapshot.id());
                active.remove(snapshot.id());
                persist(latest).exceptionally(error -> { Agnor.LOGGER.error("AGNOR unload save failed", error); return null; });
            }
            if (!active.containsKey(snapshot.id()) && !suspended.contains(snapshot.id())) activate(desired.get(snapshot.id()));
        }
        if (++ticks % saveInterval == 0) {
            for (UUID id : List.copyOf(active.keySet())) persist(capture(id)).exceptionally(error -> {
                Agnor.LOGGER.error("AGNOR autosave failed", error); return null;
            });
        }
    }

    private void activate(RuntimeSnapshot snapshot) {
        if (active.containsKey(snapshot.id()) || snapshot.deleted()) return;
        var definition = definitions.available(snapshot.definition(),snapshot.definitionRevision());
        var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(snapshot.dimension())));
        if (world == null || !world.isChunkLoaded(BlockPos.ofFloored(snapshot.x(), snapshot.y(), snapshot.z()))) return;
        Entity existing = world.getEntity(snapshot.id());
        if (existing != null) throw new IllegalStateException("NPC entity UUID collision");
        Placeholder entity = new Placeholder(world, snapshot);
        entity.setCustomName(Text.literal(definition.displayName()));
        entity.setCustomNameVisible(true);
        if (!world.spawnEntity(entity)) throw new IllegalStateException("NPC placeholder spawn rejected");
        active.put(snapshot.id(), entity);
        refreshContent();
    }

    private RuntimeSnapshot capture(UUID id) {
        RuntimeSnapshot snapshot = desired.get(id);
        if (snapshot == null) throw new IllegalArgumentException("NPC not loaded");
        Placeholder entity = active.get(id);
        if (entity != null) {
            snapshot = snapshot.at(entity.getWorld().getRegistryKey().getValue().toString(), entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
            desired.put(id, snapshot);
        }
        return snapshot;
    }

    private CompletableFuture<Void> persist(RuntimeSnapshot snapshot) {
        return submit(() -> {
            var latest = repository.find(worldId, snapshot.id());
            if (!latest.deleted()) repository.save(worldId, latest.at(snapshot.dimension(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch()));
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        if(maintenance)return CompletableFuture.failedFuture(new IllegalStateException("Memory maintenance in progress"));
        return enqueue(work);
    }
    private <T> CompletableFuture<T> enqueue(SqlWork<T> work) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("NPC runtime closed"));
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return work.run(); } catch (Exception e) { throw new CompletionException(e); }
            }, io);
        } catch (RejectedExecutionException e) { return CompletableFuture.failedFuture(e); }
    }
    private void checkThread() { if (!server.isOnThread()) throw new IllegalStateException("NPC lifecycle requires server thread"); }

    @Override public void close() {
        checkThread();
        if (closed) return;
        if(maintenance){closed=true;io.shutdown();try{io.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}active.values().forEach(Entity::discard);active.clear();desired.clear();return;}
        var saves = List.copyOf(active.keySet()).stream().map(this::capture).toList();
        // Drain admitted work before the final snapshots, without waiting on server callbacks.
        CompletableFuture<Void> finalSave = submit(() -> {
            for (var snapshot : saves) {
                var latest = repository.find(worldId, snapshot.id());
                if (!latest.deleted()) repository.save(worldId, latest.at(snapshot.dimension(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch()));
            }
            return null;
        });
        closed = true; io.shutdown();
        try { finalSave.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException("NPC shutdown save failed", e); }
        finally { active.values().forEach(Entity::discard); active.clear(); desired.clear(); }
    }
    @FunctionalInterface private interface SqlWork<T> { T run() throws Exception; }

    private static final class Placeholder extends NpcEntity {
        Placeholder(ServerWorld world, RuntimeSnapshot snapshot) {
            super(world);
            setUuid(snapshot.id()); refreshPositionAndAngles(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
            setInvulnerable(true); setNoGravity(true);
        }
        @Override public boolean shouldSave() { return false; }
    }
}
