package dev.agnor.content;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Private world-scoped last-known-good snapshots. Pointer replacement is the only commit point. */
public final class PackStore {
    private final Path root;
    private final PackLoader loader=new PackLoader();
    public PackStore(Path root) throws IOException { this.root=root;Files.createDirectories(root.resolve("objects")); }
    public PackGeneration restore() throws IOException { return read("current"); }
    public PackGeneration previous() throws IOException { return read("previous"); }
    private PackGeneration read(String slot) throws IOException {
        Path pointer=root.resolve(slot+".txt");
        if(!Files.exists(pointer))return PackGeneration.stage(List.of());
        if(Files.size(pointer)>4096)throw new IOException("Pack pointer size");
        var packs=new ArrayList<ContentPack>();
        for(String digest:Files.readAllLines(pointer)) {
            if(!digest.matches("[0-9a-f]{64}"))throw new IOException("Invalid pack pointer");
            var pack=loader.load(root.resolve("objects").resolve(digest+".zip"));
            if(!pack.digest().equals(digest))throw new IOException("Pack cache checksum");packs.add(pack);
        }
        return PackGeneration.stage(packs);
    }
    public void commit(PackGeneration generation) throws IOException {
        for(var pack:generation.packs().values()) {
            Path object=root.resolve("objects").resolve(pack.digest()+".zip");
            if(!Files.exists(object)) {
                Path pending=Files.createTempFile(root.resolve("objects"),"pending-",".zip");
                try {
                    archive(pack,pending);
                    if(!loader.load(pending).digest().equals(pack.digest()))throw new IOException("Pack snapshot verification");
                    Files.move(pending,object,StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(pending); }
            } else if(!loader.load(object).digest().equals(pack.digest()))throw new IOException("Corrupt pack cache");
        }
        Path current=root.resolve("current.txt");
        if(Files.exists(current))atomic(root.resolve("previous.txt"),Files.readAllBytes(current));
        var text=new StringBuilder();generation.packs().values().stream().sorted(Comparator.comparing(ContentPack::id)).forEach(p->text.append(p.digest()).append('\n'));
        atomic(current,text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void atomic(Path destination,byte[] bytes) throws IOException {
        Path pending=Files.createTempFile(destination.getParent(),"pointer-",".tmp");
        try { Files.write(pending,bytes);Files.move(pending,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(pending); }
    }
    public static void archive(ContentPack pack,Path destination) throws IOException {
        try(var zip=new ZipOutputStream(Files.newOutputStream(destination))) {
            entry(zip,"manifest.json",pack.manifest());
            for(var asset:pack.assets().values().stream().sorted(Comparator.comparing(ContentPack.Asset::path)).toList())entry(zip,asset.path(),asset.bytes());
        }
    }
    private static void entry(ZipOutputStream zip,String path,byte[] bytes) throws IOException {
        var entry=new ZipEntry(path);var crc=new CRC32();crc.update(bytes);entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);entry.setCompressedSize(bytes.length);entry.setCrc(crc.getValue());zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();
    }
}
