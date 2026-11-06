package dev.agnor.client;

import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Immutable in-memory resource pack; old reload generations release their byte maps on close. */
public final class ContentResources extends AbstractFileResourcePack {
    private static volatile Map<String,byte[]> current=Map.of();
    private Map<String,byte[]> resources;
    private static final ResourcePackInfo INFO=new ResourcePackInfo("agnor-content",Text.literal("AGNOR Content Packs"),ResourcePackSource.BUILTIN,Optional.empty());
    private ContentResources(ResourcePackInfo info) {super(info);resources=current;}
    public static void publish(Map<String,byte[]> resources) {current=Map.copyOf(resources);}
    public static void profiles(Consumer<ResourcePackProfile> consumer) {
        var profile=ResourcePackProfile.create(INFO,new ResourcePackProfile.PackFactory() {
            public ResourcePack open(ResourcePackInfo info) {return new ContentResources(info);}
            public ResourcePack openWithOverlays(ResourcePackInfo info,ResourcePackProfile.Metadata metadata) {return open(info);}
        },ResourceType.CLIENT_RESOURCES,new ResourcePackPosition(true,ResourcePackProfile.InsertionPosition.TOP,true));
        if(profile!=null)consumer.accept(profile);
    }
    @Override public InputSupplier<InputStream> openRoot(String... segments) {
        if(segments.length!=1||!segments[0].equals("pack.mcmeta"))return null;
        return ()->new ByteArrayInputStream("{\"pack\":{\"pack_format\":34,\"description\":\"AGNOR validated local content\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Override public InputSupplier<InputStream> open(ResourceType type,Identifier id) {
        if(type!=ResourceType.CLIENT_RESOURCES||!id.getNamespace().equals("agnor"))return null;
        byte[] bytes=resources.get(id.getPath());return bytes==null?null:()->new ByteArrayInputStream(bytes);
    }
    @Override public void findResources(ResourceType type,String namespace,String prefix,ResourcePack.ResultConsumer consumer) {
        if(type!=ResourceType.CLIENT_RESOURCES||!namespace.equals("agnor"))return;
        resources.forEach((path,bytes)->{if(path.startsWith(prefix))consumer.accept(Identifier.of("agnor",path),()->new ByteArrayInputStream(bytes));});
    }
    @Override public Set<String> getNamespaces(ResourceType type) {return type==ResourceType.CLIENT_RESOURCES?Set.of("agnor"):Set.of();}
    @Override public void close() {resources=Map.of();}
}
