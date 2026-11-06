package dev.agnor.content;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Sharing is deny-by-default and independent of the client possessing a local copy. */
public final class PackTransfer {
    public static final int MAX_BYTES=16*1024*1024, CHUNK_BYTES=28*1024;
    public static boolean allowed(ContentPack pack,Set<String> optIn) {
        long size=pack.manifest().length+512L;
        for(var asset:pack.assets().values())size+=asset.size()+512L;
        return pack.provenance().rights()==ContentPack.Rights.ALLOWED&&optIn.contains(pack.id())&&size<=MAX_BYTES;
    }
    public static byte[] archive(ContentPack pack,Set<String> optIn) throws IOException {
        if(!allowed(pack,optIn))throw new IOException("Pack transfer denied");
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {
            entry(zip,"manifest.json",pack.manifest());
            for(var asset:pack.assets().values().stream().sorted(Comparator.comparing(ContentPack.Asset::path)).toList())entry(zip,asset.path(),asset.bytes());
        }
        if(bytes.size()>MAX_BYTES)throw new IOException("Pack transfer limit");return bytes.toByteArray();
    }
    private static void entry(ZipOutputStream zip,String path,byte[] bytes)throws IOException {
        var crc=new CRC32();crc.update(bytes);var entry=new ZipEntry(path);entry.setMethod(ZipEntry.STORED);entry.setSize(bytes.length);entry.setCompressedSize(bytes.length);entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();
    }
}
