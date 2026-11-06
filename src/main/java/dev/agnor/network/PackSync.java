package dev.agnor.network;

import dev.agnor.Agnor;
import dev.agnor.content.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.agnor.network.PackWire.*;

/** One addressed, bounded, cancellable pull transfer per connection. No source paths cross the wire. */
public final class PackSync implements AutoCloseable {
    private static final Map<MinecraftServer,PackSync> hosts=new HashMap<>();
    private final MinecraftServer server;
    private final Path policyFile;
    private Map<String,String> optIn;
    private final Map<ServerPlayNetworkHandler,Session> sessions=new HashMap<>();
    private final Map<ServerPlayNetworkHandler,Integer> lastAction=new HashMap<>();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->new Thread(r,"agnor-pack-sharing"),new ThreadPoolExecutor.AbortPolicy());
    private boolean closed,policyBusy;
    private final Map<String,Long> transmitted=new HashMap<>();
    public static long bytesSent(MinecraftServer server,String id){var host=hosts.get(server);return host==null?0:host.transmitted.getOrDefault(id,0L);}
    private static final class Session {
        final UUID token;final ContentPack pack;byte[] archive;String hash;int offset,nextTick,expires;
        Session(Request request,ContentPack pack,int tick){token=request.token();this.pack=pack;expires=tick+600;}
    }
    private PackSync(MinecraftServer server,Path save)throws IOException {
        this.server=server;policyFile=save.resolve("agnor/content-share.properties");optIn=load(policyFile);
    }
    public static void start(MinecraftServer server,Path save)throws IOException {hosts.put(server,new PackSync(server,save));}
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((h,sender,server)->{var host=hosts.get(server);if(host!=null)host.catalog(h.player,new UUID(0,0));});
        ServerPlayConnectionEvents.DISCONNECT.register((h,server)->{var host=hosts.get(server);if(host!=null){host.sessions.remove(h);host.lastAction.remove(h);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{var host=hosts.get(server);if(host!=null)host.sessions.entrySet().removeIf(e->server.getTicks()>e.getValue().expires);});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var host=hosts.remove(server);if(host!=null)host.close();});
    }
    public static void changed(MinecraftServer server) {
        var host=hosts.get(server);if(host==null)return;host.sessions.clear();
        for(var player:server.getPlayerManager().getPlayerList())host.catalog(player,new UUID(0,0));
    }
    public static void handle(ServerPlayerEntity player,Request request) {
        var host=hosts.get(player.getServer());if(host==null){send(player,status(request.token(),Status.UNAVAILABLE));return;}
        host.handleRequest(player,request);
    }
    private boolean allowed(ContentPack pack) {return pack.digest().equals(optIn.get(pack.id()))&&PackTransfer.allowed(pack,Set.of(pack.id()));}
    private void catalog(ServerPlayerEntity player,UUID token) {
        var offers=Agnor.content(server).active().packs().values().stream().sorted(Comparator.comparing(ContentPack::id))
            .map(p->new Offer(p.id(),p.version(),p.digest(),allowed(p))).toList();
        send(player,new Response(VERSION,token,Status.CATALOG,offers,"","",0,0,"",new byte[0]));
    }
    private void handleRequest(ServerPlayerEntity player,Request request) {
        if(!server.isOnThread())throw new IllegalStateException("Pack sync requires server thread");
        if(request.version()!=VERSION){send(player,status(request.token(),Status.VERSION_MISMATCH));return;}
        if(!valid(request)){send(player,status(request.token(),Status.INVALID));return;}
        if(request.action()==Action.CANCEL){var session=sessions.get(player.networkHandler);if(session!=null&&session.token.equals(request.token()))sessions.remove(player.networkHandler);send(player,status(request.token(),Status.CANCELLED));return;}
        int tick=server.getTicks();
        if(request.action()==Action.LIST||request.action()==Action.FETCH) {
            if(tick-lastAction.getOrDefault(player.networkHandler,-1000)<10){send(player,status(request.token(),Status.BUSY));return;}
            lastAction.put(player.networkHandler,tick);
        }
        if(request.action()==Action.LIST){catalog(player,request.token());return;}
        var pack=Agnor.content(server).active().packs().get(request.id());
        if(pack==null||!pack.digest().equals(request.digest())){sessions.remove(player.networkHandler);send(player,status(request.token(),Status.STALE));return;}
        if(!allowed(pack)){sessions.remove(player.networkHandler);send(player,status(request.token(),Status.DENIED));return;}
        if(request.action()==Action.NEXT) {
            var session=sessions.get(player.networkHandler);
            if(session==null||!session.token.equals(request.token())||session.pack!=pack||session.archive==null||request.offset()!=session.offset){send(player,status(request.token(),Status.STALE));return;}
            if(tick<session.nextTick){send(player,status(request.token(),Status.BUSY));return;}
            chunk(player,session);return;
        }
        if(sessions.containsKey(player.networkHandler)||sessions.size()>=4){send(player,status(request.token(),Status.BUSY));return;}
        var session=new Session(request,pack,tick);sessions.put(player.networkHandler,session);
        try { io.execute(()->{
            byte[] archive=null;Exception failure=null;
            try{archive=PackTransfer.archive(pack,Set.of(pack.id()));}catch(Exception e){failure=e;}
            var bytes=archive;var hash=bytes==null?"":PackLoader.sha256(bytes);var error=failure;
            server.execute(()->{
                if(closed||sessions.get(player.networkHandler)!=session||!connected(player))return;
                if(error!=null){sessions.remove(player.networkHandler);send(player,status(session.token,Status.UNAVAILABLE));return;}
                if(Agnor.content(server).active().packs().get(pack.id())!=pack||!allowed(pack)){sessions.remove(player.networkHandler);send(player,status(session.token,Status.STALE));return;}
                session.archive=bytes;session.hash=hash;chunk(player,session);
            });
        });}catch(RejectedExecutionException error){sessions.remove(player.networkHandler);send(player,status(session.token,Status.BUSY));}
    }
    private void chunk(ServerPlayerEntity player,Session session) {
        int end=Math.min(session.archive.length,session.offset+PackTransfer.CHUNK_BYTES);
        transmitted.merge(session.pack.id(),(long)(end-session.offset),Long::sum);
        send(player,new Response(VERSION,session.token,Status.CHUNK,List.of(),session.pack.id(),session.pack.digest(),session.offset,session.archive.length,session.hash,Arrays.copyOfRange(session.archive,session.offset,end)));
        session.offset=end;session.nextTick=server.getTicks()+2;session.expires=server.getTicks()+600;
        if(end==session.archive.length)sessions.remove(player.networkHandler);
    }
    public static CompletableFuture<Void> share(MinecraftServer server,String id,boolean enabled) {
        var host=hosts.get(server);if(host==null)return CompletableFuture.failedFuture(new IllegalStateException("Sharing unavailable"));
        return host.updatePolicy(id,enabled);
    }
    private CompletableFuture<Void> updatePolicy(String id,boolean enabled) {
        if(policyBusy||closed)return CompletableFuture.failedFuture(new IllegalStateException("Sharing busy"));
        var pack=Agnor.content(server).active().packs().get(id);
        if(enabled&&(pack==null||!PackTransfer.allowed(pack,Set.of(id))))return CompletableFuture.failedFuture(new IOException("Pack is not redistributable or exceeds transfer limit"));
        var next=new TreeMap<>(optIn);if(enabled)next.put(id,pack.digest());else next.remove(id);
        if(next.size()>32)return CompletableFuture.failedFuture(new IOException("Sharing opt-in count limit; revoke an old entry first"));
        policyBusy=true;var result=new CompletableFuture<Void>();
        try{io.execute(()->{
            Exception error=null;
            try{var p=new Properties();next.forEach(p::setProperty);Path temp=Files.createTempFile(policyFile.getParent(),"share-",".tmp");
                try{try(var out=Files.newOutputStream(temp)){p.store(out,"Explicit admin opt-in, pinned to inventory digest");}Files.move(temp,policyFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
            }catch(Exception e){error=e;}var failure=error;
            server.execute(()->{policyBusy=false;if(closed){result.completeExceptionally(new IllegalStateException("Session closed"));return;}if(failure!=null){result.completeExceptionally(failure);return;}optIn=Map.copyOf(next);changed(server);result.complete(null);});
        });}catch(RejectedExecutionException error){policyBusy=false;result.completeExceptionally(error);}return result;
    }
    private static Map<String,String> load(Path path)throws IOException {
        if(!Files.exists(path))return Map.of();if(Files.size(path)>8192)throw new IOException("Sharing policy size");var p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}
        if(p.size()>32)throw new IOException("Sharing policy count");var result=new HashMap<String,String>();
        for(String id:p.stringPropertyNames()){String digest=p.getProperty(id);if(!id.matches("[a-z][a-z0-9_.-]{0,63}")||!digest.matches("[0-9a-f]{64}"))throw new IOException("Sharing policy value");result.put(id,digest);}return Map.copyOf(result);
    }
    private boolean connected(ServerPlayerEntity player){return server.getPlayerManager().getPlayer(player.getUuid())==player;}
    private static void send(ServerPlayerEntity player,Response response){if(ServerPlayNetworking.canSend(player,PackProtocol.Response.ID))ServerPlayNetworking.send(player,new PackProtocol.Response(response));}
    @Override public void close(){closed=true;sessions.clear();lastAction.clear();io.shutdownNow();}
}
