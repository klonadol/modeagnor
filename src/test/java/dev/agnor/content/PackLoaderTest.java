package dev.agnor.content;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PackLoaderTest {
    @TempDir Path temp;
    static JsonObject manifest(String id) {
        return JsonParser.parseString("""
            {"schemaVersion":1,"apiVersion":1,"id":"%s","version":"1.0.0","priority":0,
             "dependencies":{},"overrides":[],
             "provenance":{"source":"https://example.org/original","author":"Fixture author","license":"CC0-1.0","rights":"ALLOWED"},
             "assets":[],"definitions":[{"id":"example:npc","revision":1,"displayName":"External NPC","prompt":"Be helpful.","model":"","rig":""}]}
            """.formatted(id)).getAsJsonObject();
    }
    static void asset(JsonObject manifest,String id,String path,String format,byte[] bytes) {
        var a=new JsonObject();a.addProperty("id",manifest.get("id").getAsString()+":"+id);a.addProperty("path",path);
        a.addProperty("format",format);a.addProperty("sha256",PackLoader.sha256(bytes));manifest.getAsJsonArray("assets").add(a);
    }
    Path directory(String name,JsonObject manifest) throws IOException {
        Path dir=temp.resolve(name);Files.createDirectories(dir);write(dir,manifest);return dir;
    }
    static void write(Path dir,JsonObject manifest) throws IOException { Files.writeString(dir.resolve("manifest.json"),manifest.toString()); }
    Path zip(String name,Map<String,byte[]> files) throws IOException {
        var path=temp.resolve(name);
        try(var out=new ZipOutputStream(Files.newOutputStream(path))) {
            for(var entry:files.entrySet()) { out.putNextEntry(new ZipEntry(entry.getKey()));out.write(entry.getValue());out.closeEntry(); }
        } return path;
    }
    static byte[] glb(String json) {
        byte[] text=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);int padded=(text.length+3)/4*4;
        var b=ByteBuffer.allocate(20+padded).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46546c67).putInt(2).putInt(b.capacity()).putInt(padded).putInt(0x4e4f534a).put(text);
        while(b.hasRemaining())b.put((byte)' ');return b.array();
    }
    @Test void directoryAndZipHaveSameInventoryAndImmutablePayload() throws Exception {
        var m=manifest("base");byte[] bytes="fixture".getBytes();asset(m,"notes","notes.txt","text",bytes);
        var dir=directory("non standard dir/pack",m);Files.write(dir.resolve("notes.txt"),bytes);
        var loader=new PackLoader();var pack=loader.load(dir);
        var archive=zip("pack.zip",Map.of("manifest.json",m.toString().getBytes(),"notes.txt",bytes));
        assertEquals(pack.digest(),loader.load(archive).digest());
        pack.assets().get("base:notes").bytes()[0]=0;
        assertArrayEquals(bytes,pack.assets().get("base:notes").bytes());
    }
    @Test void rejectsUnsafePathsAndZipEntries() throws Exception {
        for(String path:List.of("../escape","/absolute","c:/absolute","a\\b","a//b","a/./b","a/../b","nul.txt","con","a.","UPPER","a%2fb")) {
            assertThrows(IOException.class,()->PackLoader.path(path),path);
            var archive=zip("unsafe.zip",Map.of(path,new byte[]{1},"manifest.json",manifest("base").toString().getBytes()));
            assertThrows(IOException.class,()->new PackLoader().load(archive),path);
        }
        assertFalse(Files.exists(temp.resolve("escape")));
    }
    @Test void rejectsBadSchemaUnknownFieldsDuplicatesAndFractionalVersions() throws Exception {
        for(String field:List.of("schemaVersion","apiVersion")) {
            var m=manifest("base");m.addProperty(field,2);var dir=directory(field,m);
            assertThrows(IOException.class,()->new PackLoader().load(dir));
        }
        var dir=directory("json",manifest("base"));
        for(String json:List.of("{\"id\":1,\"id\":2}","{\"id\":NaN}","{} {}", "{\"a\":1,}")) {
            Files.writeString(dir.resolve("manifest.json"),json);assertThrows(IOException.class,()->new PackLoader().load(dir));
        }
        var m=manifest("base");m.addProperty("priority",0.5);write(dir,m);assertThrows(IOException.class,()->new PackLoader().load(dir));
        m=manifest("base");m.addProperty("code","execute");write(dir,m);assertThrows(IOException.class,()->new PackLoader().load(dir));
    }
    @Test void rejectsHashesUndeclaredExecutableAndSignatures() throws Exception {
        var m=manifest("base");byte[] payload="not a PNG".getBytes();asset(m,"model","model.png","png",payload);
        var dir=directory("payload",m);Files.write(dir.resolve("model.png"),payload);
        assertThrows(IOException.class,()->new PackLoader().load(dir));
        m=manifest("base");asset(m,"notes","notes.txt","text","expected".getBytes());write(dir,m);
        Files.delete(dir.resolve("model.png"));Files.writeString(dir.resolve("notes.txt"),"changed");
        assertThrows(IOException.class,()->new PackLoader().load(dir));
        write(dir,manifest("base"));Files.delete(dir.resolve("notes.txt"));Files.writeString(dir.resolve("evil.jar"),"code");
        assertThrows(IOException.class,()->new PackLoader().load(dir));
    }
    @Test void enforcesCountSizeTotalRatioAndCaseCollisions() throws Exception {
        var m=manifest("base");var dir=directory("limits",m);
        assertThrows(IOException.class,()->new PackLoader(new PackLoader.Limits(1,1024,2048,10,200)).load(dir));
        Files.write(dir.resolve("big.txt"),new byte[100]);
        assertThrows(IOException.class,()->new PackLoader(new PackLoader.Limits(1,64,2048,1024,200)).load(dir));
        var archive=zip("bomb.zip",Map.of("manifest.json",m.toString().getBytes(),"big.txt",new byte[100_000]));
        assertThrows(IOException.class,()->new PackLoader(new PackLoader.Limits(10,200_000,400_000,1024,2)).load(archive));
        Path collision=zip("case.zip",Map.of("manifest.json",m.toString().getBytes(),"a.txt",new byte[1],"A.txt",new byte[1]));
        assertThrows(IOException.class,()->new PackLoader().load(collision));
    }
    @Test void restrictsRedistributionDeclarationsAndReferences() throws Exception {
        var m=manifest("base");m.getAsJsonObject("provenance").addProperty("license","unknown");var dir=directory("rights",m);
        assertThrows(IOException.class,()->new PackLoader().load(dir));
        m.getAsJsonObject("provenance").addProperty("rights","LOCAL_ONLY");write(dir,m);
        assertEquals(ContentPack.Rights.LOCAL_ONLY,new PackLoader().load(dir).provenance().rights());
        m.getAsJsonArray("definitions").get(0).getAsJsonObject().addProperty("model","base:missing");write(dir,m);
        assertThrows(IOException.class,()->new PackLoader().load(dir));
    }
    @Test void validatesGlbChunksAndPreventsExternalResourceReads() throws Exception {
        var dir=directory("glb",manifest("base"));
        for(String json:List.of("{\"asset\":{\"version\":\"2.0\"}}", "{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"../secret.png\"}]}",
                "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"https://example.org/a\"}]}",
                "{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"bufferView\":0,\"mimeType\":\"image/png\"}]}",
                "{\"asset\":{\"version\":\"2.0\"},\"extensionsRequired\":[\"UNSUPPORTED\"]}")) {
            var m=manifest("base");byte[] bytes=glb(json);asset(m,"model","model.glb","glb",bytes);write(dir,m);Files.write(dir.resolve("model.glb"),bytes);
            if(json.contains("uri")||json.contains("images")||json.contains("extensionsRequired"))assertThrows(IOException.class,()->new PackLoader().load(dir));else assertNotNull(new PackLoader().load(dir));
        }
    }
    @Test void resolvesExactDependenciesCyclesAndExplicitPriority() throws Exception {
        var loader=new PackLoader();var a=manifest("base");var b=manifest("variant");
        var pa=loader.load(directory("a",a));var bdir=directory("b",b);
        assertThrows(IOException.class,()->PackResolver.resolve(List.of(pa,loader.load(bdir))));
        b.addProperty("priority",1);b.getAsJsonObject("dependencies").addProperty("base","1.0.0");b.getAsJsonArray("overrides").add("base");write(bdir,b);
        var pb=loader.load(bdir);assertEquals("variant",PackResolver.resolve(List.of(pb,pa)).get("example:npc").pack());
        assertThrows(IOException.class,()->PackResolver.resolve(List.of(pb)));
        a.getAsJsonObject("dependencies").addProperty("variant","1.0.0");var adir=temp.resolve("a");write(adir,a);
        assertThrows(IOException.class,()->PackResolver.resolve(List.of(loader.load(adir),pb)));
    }
}
