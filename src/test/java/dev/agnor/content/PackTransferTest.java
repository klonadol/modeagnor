package dev.agnor.content;

import dev.agnor.network.PackWire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.agnor.network.PackWire.*;

class PackTransferTest {
    @TempDir Path temp;
    ContentPack pack(ContentPack.Rights rights)throws Exception {
        var manifest=PackLoaderTest.manifest("fixture");manifest.getAsJsonObject("provenance").addProperty("rights",rights.name());
        // Enough stored UTF-8 data to require more than one bounded packet.
        byte[] bytes="original fixture content ".repeat(3000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PackLoaderTest.asset(manifest,"notes","notes.txt","text",Arrays.copyOf(bytes,60_000));
        Path directory=temp.resolve(rights.name());Files.createDirectories(directory);PackLoaderTest.write(directory,manifest);Files.write(directory.resolve("notes.txt"),Arrays.copyOf(bytes,60_000));
        return new PackLoader().load(directory);
    }
    @Test void noOptInAndRestrictedRightsNeverProduceArchive()throws Exception {
        for(var rights:ContentPack.Rights.values()) {
            var pack=pack(rights);assertFalse(PackTransfer.allowed(pack,Set.of()));assertThrows(IOException.class,()->PackTransfer.archive(pack,Set.of()));
            if(rights!=ContentPack.Rights.ALLOWED){assertFalse(PackTransfer.allowed(pack,Set.of(pack.id())));assertThrows(IOException.class,()->PackTransfer.archive(pack,Set.of(pack.id())));}
        }
    }
    @Test void transferLimitIsIndependentOfPermissiveIngressLimit()throws Exception {
        var pack=pack(ContentPack.Rights.ALLOWED);
        var large=new ContentPack.Asset("fixture:large","large.glb","glb","0".repeat(64),new byte[PackTransfer.MAX_BYTES]);
        var oversized=new ContentPack(pack.id(),pack.version(),pack.priority(),pack.dependencies(),pack.overrides(),pack.provenance(),Map.of(large.id(),large),pack.definitions(),pack.digest(),pack.manifest());
        assertFalse(PackTransfer.allowed(oversized,Set.of(pack.id())));assertThrows(IOException.class,()->PackTransfer.archive(oversized,Set.of(pack.id())));
    }
    @Test void exactRequestedInventorySurvivesBoundedChunksAndRevalidation()throws Exception {
        var pack=pack(ContentPack.Rights.ALLOWED);byte[] archive=PackTransfer.archive(pack,Set.of(pack.id()));
        assertTrue(archive.length>PackTransfer.CHUNK_BYTES);UUID token=UUID.randomUUID();var offer=new Offer(pack.id(),pack.version(),pack.digest(),true);
        var download=new PackDownload(token,offer);assertThrows(IOException.class,download::complete);
        for(int start=0;start<archive.length;start+=PackTransfer.CHUNK_BYTES) {
            byte[] chunk=Arrays.copyOfRange(archive,start,Math.min(archive.length,start+PackTransfer.CHUNK_BYTES));
            var response=new Response(VERSION,token,Status.CHUNK,List.of(),pack.id(),pack.digest(),start,archive.length,PackLoader.sha256(archive),chunk);
            byte[] wire=encode(response);assertTrue(wire.length<=MAX_RESPONSE);
            var decoded=response(wire);assertArrayEquals(chunk,decoded.bytes());assertEquals(start+chunk.length==archive.length,download.accept(decoded));
        }
        assertArrayEquals(archive,download.complete());Path zip=temp.resolve("received.zip");Files.write(zip,download.complete());assertEquals(pack.digest(),new PackLoader().load(zip).digest());
    }
    @Test void unsolicitedReplayedReorderedMixedAndCorruptTransfersAreRejected()throws Exception {
        var pack=pack(ContentPack.Rights.ALLOWED);byte[] archive=PackTransfer.archive(pack,Set.of(pack.id()));UUID token=UUID.randomUUID();var offer=new Offer(pack.id(),pack.version(),pack.digest(),true);
        var first=new Response(VERSION,token,Status.CHUNK,List.of(),pack.id(),pack.digest(),0,archive.length,PackLoader.sha256(archive),Arrays.copyOf(archive,PackTransfer.CHUNK_BYTES));
        var download=new PackDownload(token,offer);assertFalse(download.accept(first));assertThrows(IOException.class,()->download.accept(first));
        assertThrows(IOException.class,()->new PackDownload(UUID.randomUUID(),offer).accept(first));
        assertThrows(IllegalArgumentException.class,()->new PackDownload(token,new Offer(pack.id(),pack.version(),pack.digest(),false)));
        var wrong=new Response(VERSION,token,Status.CHUNK,List.of(),pack.id(),"f".repeat(64),PackTransfer.CHUNK_BYTES,archive.length,PackLoader.sha256(archive),new byte[]{1});
        assertThrows(IOException.class,()->download.accept(wrong));
        var corrupt=new PackDownload(token,offer);byte[] small={1,2,3};assertTrue(corrupt.accept(new Response(VERSION,token,Status.CHUNK,List.of(),pack.id(),pack.digest(),0,3,"0".repeat(64),small)));
        assertThrows(IOException.class,corrupt::complete);
    }
    @Test void wireRejectsMalformedLengthsEnumsVersionsAndUnboundedCatalogs()throws Exception {
        var request=new Request(99,UUID.randomUUID(),Action.FETCH,"fixture","a".repeat(64),0);assertEquals(request,request(encode(request)));
        byte[] bytes=encode(request);assertThrows(IllegalArgumentException.class,()->request(Arrays.copyOf(bytes,bytes.length-1)));assertThrows(IllegalArgumentException.class,()->request(Arrays.copyOf(bytes,bytes.length+1)));
        assertThrows(IllegalArgumentException.class,()->request(new byte[MAX_REQUEST+1]));bytes[20]=(byte)99;assertThrows(IllegalArgumentException.class,()->request(bytes));
        assertFalse(valid(new Request(VERSION,UUID.randomUUID(),Action.FETCH,"../escape","a".repeat(64),0)));
        assertFalse(valid(new Request(VERSION,UUID.randomUUID(),Action.NEXT,"fixture","a".repeat(64),PackTransfer.MAX_BYTES+1)));
        assertThrows(IllegalArgumentException.class,()->encode(new Response(VERSION,UUID.randomUUID(),Status.CATALOG,Collections.nCopies(33,new Offer("fixture","1.0.0","a".repeat(64),true)),"","",0,0,"",new byte[0])));
        var status=status(UUID.randomUUID(),Status.DENIED);assertEquals(Status.DENIED,response(encode(status)).status());
        assertThrows(IllegalArgumentException.class,()->encode(new Response(2,status.token(),Status.DENIED,List.of(),"","",0,0,"",new byte[0])));
    }
}
