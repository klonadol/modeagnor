package dev.agnor.content;

import dev.agnor.character.DefinitionRegistry;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Server-thread publication with one bounded IO worker; failures leave the active generation intact. */
public final class ContentService implements AutoCloseable {
    private final Path packs;
    private final PackStore store;
    private final Executor owner;
    private final Thread ownerThread=Thread.currentThread();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
            runnable->new Thread(runnable,"agnor-content-io"),new ThreadPoolExecutor.AbortPolicy());
    private final DefinitionRegistry registry=DefinitionRegistry.builtIn();
    private PackGeneration active;
    private boolean busy,closed;
    private long epoch;
    private Runnable changed=()->{};
    public ContentService(Path packs,Path privateRoot,Executor owner) throws IOException {
        this.packs=packs.toAbsolutePath().normalize();this.store=new PackStore(privateRoot);this.owner=owner;
        active=store.restore();registry.replace(active.registry());
    }
    public DefinitionRegistry registry() { return registry; }
    public PackGeneration active() { thread();return active; }
    public long epoch() { thread();return epoch; }
    public void onChanged(Runnable changed) { thread();this.changed=changed; }
    public CompletableFuture<PackGeneration> enable(String filename) {
        thread();
        if(!filename.matches("[a-z][a-z0-9_.-]{0,67}")||filename.contains(".."))return CompletableFuture.failedFuture(new IOException("Pack filename"));
        Path source=packs.resolve(filename).normalize();
        if(!source.getParent().equals(packs))return CompletableFuture.failedFuture(new IOException("Pack path"));
        var snapshot=active;
        return change(()->{
            var selected=Files.exists(source,LinkOption.NOFOLLOW_LINKS)||filename.endsWith(".zip")?source:source.resolveSibling(filename+".zip");
            var pack=new PackLoader().load(selected);
            if(!filename.equals(pack.id()) && !filename.equals(pack.id()+".zip"))throw new IOException("Pack filename must match ID");
            var candidates=new HashMap<>(snapshot.packs());candidates.put(pack.id(),pack);
            return PackGeneration.stage(candidates.values());
        });
    }
    public CompletableFuture<PackGeneration> disable(String id) {
        thread();var snapshot=active;
        return change(()->{if(!snapshot.packs().containsKey(id))throw new IOException("Pack not active");
            var candidates=new HashMap<>(snapshot.packs());candidates.remove(id);return PackGeneration.stage(candidates.values());});
    }
    public CompletableFuture<PackGeneration> rollback() { thread();return change(store::previous); }
    private CompletableFuture<PackGeneration> change(Work work) {
        if(busy||closed)return CompletableFuture.failedFuture(new IllegalStateException("Content busy/closed"));
        busy=true;var result=new CompletableFuture<PackGeneration>();
        var before=active;
        try {
            io.execute(()->{
                PackGeneration candidate=null;Throwable failure=null;
                try { candidate=work.run();if(!same(before,candidate))store.commit(candidate); } catch(Exception error) { failure=error; }
                var ready=candidate;var error=failure;
                owner.execute(()->{
                    busy=false;
                    if(closed){result.completeExceptionally(new IllegalStateException("Content session closed"));return;}
                    if(error!=null){result.completeExceptionally(error);return;}
                    if(same(before,ready)){result.complete(active);return;}
                    active=ready;registry.replace(ready.registry());epoch++;
                    // Publication observers cannot undo a committed generation.
                    try { changed.run(); } catch(RuntimeException observer) { System.getLogger("agnor").log(System.Logger.Level.WARNING,"Content committed; presentation refresh failed",observer); }
                    result.complete(ready);
                });
            });
        } catch(RejectedExecutionException error) { busy=false;result.completeExceptionally(error); }
        return result;
    }
    private void thread() { if(Thread.currentThread()!=ownerThread)throw new IllegalStateException("Content publication requires owner thread"); }
    private static boolean same(PackGeneration a,PackGeneration b) {
        if(!a.packs().keySet().equals(b.packs().keySet()))return false;
        return a.packs().entrySet().stream().allMatch(e->e.getValue().digest().equals(b.packs().get(e.getKey()).digest()));
    }
    @Override public void close() { thread();closed=true;io.shutdown();try{if(!io.awaitTermination(15,TimeUnit.SECONDS))io.shutdownNow();}catch(InterruptedException e){io.shutdownNow();Thread.currentThread().interrupt();} }
    @FunctionalInterface private interface Work { PackGeneration run() throws Exception; }
}
