package dev.agnor.client;

import dev.agnor.content.*;
import dev.agnor.character.NpcEntity;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local-only content discovery. Exact authoritative inventory match is required for presentation. */
public final class ContentClient {
    private static PackGeneration active;
    private static Path packs;
    private static Path received;
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
        runnable->{var thread=new Thread(runnable,"agnor-client-content");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private static boolean busy;
    private static boolean ready=true;
    private static Map<String,byte[]> renderResources=Map.of();
    public static void install() {
        var client=MinecraftClient.getInstance();
        try{var roots=dev.agnor.DataRoots.prepareLayout(client.runDirectory.toPath());packs=roots.packs;received=roots.cache.resolve("received-packs");Files.createDirectories(received);active=PackGeneration.stage(List.of());}
        catch(Exception error){throw new IllegalStateException("Content directories unavailable",error);}
        var access=(dev.agnor.client.mixin.ContentProviderAccess)client.getResourcePackManager();
        var providers=new LinkedHashSet<>(access.agnor$getProviders());providers.add(ContentResources::profiles);access.agnor$setProviders(providers);
        // Populate before Minecraft's initial reload; a concurrent CLIENT_STARTED reload can
        // deadlock Quick Play while startIntegratedServer owns the render-thread loading loop.
        try {active=scan();renderResources=PackResources.build(active.packs().values());ContentResources.publish(renderResources);}
        catch(Exception error){dev.agnor.Agnor.LOGGER.warn("AGNOR initial local content rejected ({})",error.getClass().getSimpleName());}
        client.getResourcePackManager().scanPacks();
    }
    private static PackGeneration scan() throws Exception {
        var candidates=new LinkedHashMap<String,ContentPack>();
        try(var paths=Files.list(received)) {
            var iterator=paths.sorted().iterator();int count=0;
            while(iterator.hasNext()){var path=iterator.next();if(++count>32)throw new java.io.IOException("Received pack count limit");var pack=new PackLoader().load(path);candidates.put(pack.id(),pack);}
        }
        try(var paths=Files.list(packs)) {
            var iterator=paths.sorted().iterator();int count=0;
            var localIds=new HashSet<String>();
            while(iterator.hasNext()){var path=iterator.next();if(++count>32)throw new java.io.IOException("Local pack count limit");var pack=new PackLoader().load(path);if(!localIds.add(pack.id()))throw new java.io.IOException("Duplicate local pack ID");candidates.put(pack.id(),pack);}
        }
        return PackGeneration.stage(candidates.values());
    }
    public static CompletableFuture<Void> reload() {
        return reload(()->true);
    }
    private static CompletableFuture<Void> reload(java.util.function.BooleanSupplier current) {
        var client=MinecraftClient.getInstance();
        if(busy)return CompletableFuture.failedFuture(new IllegalStateException("Content reload busy"));busy=true;
        var result=new CompletableFuture<Void>();
        try{IO.execute(()->{
            try {
                var generation=scan();var resources=PackResources.build(generation.packs().values());
                client.execute(()->{
                    if(!current.getAsBoolean()){busy=false;result.completeExceptionally(new java.io.IOException("Content session ended"));return;}
                    var previousResources=renderResources;ready=false;ContentResources.publish(resources);NpcMotionClient.clear();
                    client.getResourcePackManager().scanPacks();
                    client.reloadResources().whenComplete((v,error)->client.execute(()->{
                        if(error==null&&current.getAsBoolean()){active=generation;renderResources=resources;ready=true;busy=false;result.complete(null);}
                        else {ContentResources.publish(previousResources);client.getResourcePackManager().scanPacks();
                            client.reloadResources().whenComplete((restored,restoreError)->client.execute(()->{ready=restoreError==null;busy=false;result.completeExceptionally(error==null?new java.io.IOException("Content session ended"):error);}));}
                    }));
                });
            } catch(Exception error){client.execute(()->{busy=false;result.completeExceptionally(error);});}
        });}catch(RejectedExecutionException error){busy=false;result.completeExceptionally(error);}
        result.whenComplete((v,e)->{if(e!=null)dev.agnor.Agnor.LOGGER.warn("AGNOR local pack reload rejected; previous generation retained ({})",e.getClass().getSimpleName());});
        return result;
    }
    public static PackGeneration.Binding binding(NpcEntity entity) {
        String[] key=entity.content().split("\\|",-1);if(key.length!=3||active==null||!ready)return null;
        var binding=active.bindings().get(key[1]);
        return binding!=null&&binding.pack().digest().equals(key[0])&&Integer.toString(binding.definition().character().revision()).equals(key[2])?binding:null;
    }
    public static boolean matches(String id,String digest){return ready&&active!=null&&active.packs().containsKey(id)&&active.packs().get(id).digest().equals(digest);}
    public static CompletableFuture<Void> installDownloaded(dev.agnor.network.PackWire.Offer offer,java.util.concurrent.Callable<byte[]> data,java.util.function.BooleanSupplier current) {
        var client=MinecraftClient.getInstance();var result=new CompletableFuture<Void>();
        if(busy)return CompletableFuture.failedFuture(new IllegalStateException("Content reload busy"));busy=true;
        try{IO.execute(()->{
            Path pending=null;byte[] previous=null;Path destination=received.resolve(offer.id()+".zip");
            try {
                byte[] archive=data.call();
                if(archive.length>PackTransfer.MAX_BYTES||!offer.id().matches("[a-z][a-z0-9_.-]{0,63}"))throw new java.io.IOException("Download limit/ID");
                // Temporary files stay outside the scanned received-packs directory.
                pending=Files.createTempFile(received.getParent(),"download-",".zip");Files.write(pending,archive);
                var pack=new PackLoader().load(pending);
                if(!pack.id().equals(offer.id())||!pack.version().equals(offer.version())||!pack.digest().equals(offer.digest())||pack.provenance().rights()!=ContentPack.Rights.ALLOWED)throw new java.io.IOException("Downloaded pack inventory/rights mismatch");
                if(Files.exists(packs.resolve(pack.id()))||Files.exists(packs.resolve(pack.id()+".zip")))throw new java.io.IOException("Local pack exists; update your local copy manually");
                var candidate=new HashMap<>(scan().packs());candidate.put(pack.id(),pack);PackGeneration.stage(candidate.values());
                if(!current.getAsBoolean())throw new java.io.IOException("Download session ended");
                if(Files.exists(destination))previous=Files.readAllBytes(destination);
                Files.move(pending,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);pending=null;
                var backup=previous;
                client.execute(()->{
                    busy=false;
                    if(!current.getAsBoolean()){result.completeExceptionally(new java.io.IOException("Download session ended"));return;}
                    reload(current).whenComplete((v,error)->{
                        if(error==null){result.complete(null);return;}
                        try{IO.execute(()->{try{if(backup==null)Files.deleteIfExists(destination);else{Path restore=Files.createTempFile(received.getParent(),"restore-",".zip");Files.write(restore,backup);Files.move(restore,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}result.completeExceptionally(error);}catch(Exception restoreError){result.completeExceptionally(restoreError);}});}catch(RejectedExecutionException rejected){result.completeExceptionally(rejected);}
                    });
                });
            }catch(Exception error){client.execute(()->{busy=false;result.completeExceptionally(error);});}
            finally{if(pending!=null)try{Files.deleteIfExists(pending);}catch(Exception ignored){}}
        });}catch(RejectedExecutionException error){busy=false;result.completeExceptionally(error);}return result;
    }
    private ContentClient() {}
}
