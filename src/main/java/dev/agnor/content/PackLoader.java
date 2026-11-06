package dev.agnor.content;

import com.google.gson.*;
import dev.agnor.character.CharacterDefinition;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Directory/ZIP ingestion, deliberately independent of Minecraft and renderer classes. */
public final class PackLoader {
    public record Limits(int files, int fileBytes, int totalBytes, int manifestBytes, int ratio) {
        public static final Limits DEFAULT = new Limits(1024, 64*1024*1024, 256*1024*1024, 256*1024, 200);
        public Limits {
            if (files < 1 || fileBytes < 1 || totalBytes < fileBytes || manifestBytes < 1 || ratio < 1)
                throw new IllegalArgumentException("Pack limits");
        }
    }
    private final Limits limits;
    public PackLoader() { this(Limits.DEFAULT); }
    public PackLoader(Limits limits) { this.limits = limits; }

    public ContentPack load(Path source) throws IOException {
        try {
            var files = new LinkedHashMap<String, byte[]>();
            if (Files.isSymbolicLink(source)) throw new IOException("Pack symlink");
            if (Files.isDirectory(source, NOFOLLOW_LINKS)) directory(source, files);
            else {
                if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.size(source) > limits.totalBytes())
                    throw new IOException("Pack archive size/type");
                try (var channel = Files.newByteChannel(source, Set.of(StandardOpenOption.READ, NOFOLLOW_LINKS));
                     var zip = new ZipInputStream(java.nio.channels.Channels.newInputStream(channel))) {
                    var names = new HashSet<String>(); int count = 0;
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                        String name = entry.getName();
                        if (entry.isDirectory()) name = name.substring(0, name.length()-1);
                        path(name);
                        if (++count > limits.files() || !names.add(name.toLowerCase(Locale.ROOT)))
                            throw new IOException("Duplicate entry or file count limit");
                        if (entry.isDirectory()) continue;
                        byte[] data = bounded(zip, name.equals("manifest.json") ? limits.manifestBytes() : limits.fileBytes());
                        long compressed = entry.getCompressedSize();
                        if (compressed >= 0 && data.length > Math.max(1024L, compressed * limits.ratio()))
                            throw new IOException("Pack compression ratio limit");
                        put(files, name, data);
                    }
                    if (count == 0) throw new IOException("Empty/invalid ZIP");
                }
            }
            return manifest(files);
        } catch (RuntimeException e) {
            throw new IOException("Invalid pack metadata", e);
        }
    }
    private void directory(Path source, Map<String, byte[]> files) throws IOException {
        try (var entries = Files.walk(source)) {
            var iterator = entries.iterator(); int count = 0;
            while (iterator.hasNext()) {
                Path entry = iterator.next(); if (entry.equals(source)) continue;
                if (++count > limits.files()) throw new IOException("Pack file count limit");
                String name = source.relativize(entry).toString().replace('\\','/'); path(name);
                if (Files.isSymbolicLink(entry)) throw new IOException("Pack symlink");
                if (Files.isDirectory(entry, NOFOLLOW_LINKS)) continue;
                if (!Files.isRegularFile(entry, NOFOLLOW_LINKS)) throw new IOException("Pack file type");
                try (var channel = Files.newByteChannel(entry, Set.of(StandardOpenOption.READ, NOFOLLOW_LINKS));
                     var input = java.nio.channels.Channels.newInputStream(channel)) {
                    put(files, name, bounded(input, name.equals("manifest.json") ? limits.manifestBytes() : limits.fileBytes()));
                }
            }
        }
    }
    private void put(Map<String, byte[]> files, String name, byte[] bytes) throws IOException {
        if (files.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name))) throw new IOException("Duplicate pack path");
        long total = bytes.length; for (var data : files.values()) total += data.length;
        if (total > limits.totalBytes()) throw new IOException("Pack total size limit");
        files.put(name, bytes);
    }
    private static byte[] bounded(InputStream input, int limit) throws IOException {
        var output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            if ((long)total + count > limit) throw new IOException("Pack file size limit");
            output.write(buffer, 0, count); total += count;
        }
        return output.toByteArray();
    }
    public static void path(String value) throws IOException {
        if (value.length() > 200 || !value.matches("[a-z0-9_./-]+") || value.startsWith("/") || value.contains("//"))
            throw new IOException("Unsafe pack path");
        for (String part : value.split("/", -1)) {
            String stem = part.split("\\.", -1)[0];
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") ||
                stem.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]")) throw new IOException("Unsafe pack path component");
        }
    }
    private ContentPack manifest(Map<String, byte[]> files) throws IOException {
        byte[] manifest = files.get("manifest.json");
        if (manifest == null || manifest.length > limits.manifestBytes()) throw new IOException("Missing/oversized manifest");
        var root = PackJson.read(manifest).getAsJsonObject();
        fields(root, "schemaVersion", "apiVersion", "id", "version", "priority", "dependencies", "overrides", "provenance", "assets", "definitions");
        if (integer(root,"schemaVersion") != 1 || integer(root,"apiVersion") != 1) throw new IOException("Unsupported pack schema/API");
        String id = id(string(root,"id",128)); String version = version(string(root,"version",64));
        int priority = integer(root,"priority"); if (priority < -1000 || priority > 1000) throw new IOException("Pack priority");
        var deps = new TreeMap<String,String>();
        for (var entry : root.getAsJsonObject("dependencies").entrySet()) deps.put(id(entry.getKey()),version(entry.getValue().getAsString()));
        if (deps.size() > 64 || deps.containsKey(id)) throw new IOException("Invalid dependencies");
        var overrides = new HashSet<String>();
        for (var entry : root.getAsJsonArray("overrides")) {
            String dependency = id(entry.getAsString());
            if (!deps.containsKey(dependency) || !overrides.add(dependency)) throw new IOException("Override must name direct dependency");
        }
        var p = root.getAsJsonObject("provenance"); fields(p,"source","author","license","rights");
        String source = string(p,"source",2048), author = string(p,"author",256), license = string(p,"license",1024);
        var rights = ContentPack.Rights.valueOf(string(p,"rights",16));
        if (!source.matches("https://[^\\s]+") && !source.startsWith("local:")) throw new IOException("Provenance source");
        if (rights == ContentPack.Rights.ALLOWED && (license.equalsIgnoreCase("unknown") || source.startsWith("local:")))
            throw new IOException("Redistribution needs public provenance and explicit license");
        var provenance = new ContentPack.Provenance(source,author,license,rights);
        var assets = new LinkedHashMap<String,ContentPack.Asset>(); var used = new HashSet<String>(); used.add("manifest.json");
        for (var value : root.getAsJsonArray("assets")) {
            var a = value.getAsJsonObject(); fields(a,"id","path","format","sha256");
            String assetId=string(a,"id",128), path=string(a,"path",200), format=string(a,"format",16), hash=string(a,"sha256",64);
            path(path); byte[] bytes=files.get(path);
            if (!assetId.startsWith(id+":") || !assetId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IOException("Asset namespace must match pack ID");
            if (bytes == null || !hash.matches("[0-9a-f]{64}") || !sha256(bytes).equals(hash) || !used.add(path))
                throw new IOException("Missing/duplicate asset or checksum mismatch");
            signature(format,path,bytes);
            if (assets.putIfAbsent(assetId,new ContentPack.Asset(assetId,path,format,hash,bytes)) != null) throw new IOException("Duplicate asset ID");
        }
        if (used.size()!=files.size()) throw new IOException("Undeclared files in pack");
        long decodedPixels=0;
        for(var asset:assets.values())if(asset.format().equals("png")) {
            byte[] image=asset.bytes();decodedPixels+=(long)ByteBuffer.wrap(image).getInt(16)*ByteBuffer.wrap(image).getInt(20);
        }
        if(decodedPixels>32L*1024*1024)throw new IOException("Pack decoded texture budget (128MiB RGBA)");
        for (var asset : assets.values()) if (asset.format().equals("glb")) glb(asset, assets);
        var definitions=new ArrayList<ContentPack.Definition>(); var ids=new HashSet<String>();
        for (var value : root.getAsJsonArray("definitions")) {
            var d=value.getAsJsonObject(); fields(d,"id","revision","displayName","prompt","model","rig");
            var character=new CharacterDefinition(string(d,"id",128),1,integer(d,"revision"),string(d,"displayName",128));
            if (!ids.add(character.id())) throw new IOException("Duplicate definition ID");
            String prompt=string(d,"prompt",8192), model=string(d,"model",128), rig=string(d,"rig",128);
            reference(assets,model,"glb"); reference(assets,rig,"json");
            character=new CharacterDefinition(character.id(),1,character.revision(),character.displayName(),prompt);
            definitions.add(new ContentPack.Definition(character,prompt,model,rig));
        }
        if (definitions.isEmpty() || definitions.size()>64) throw new IOException("Definition count");
        // Canonical inventory identity: manifest bytes and every verified payload, independent of ZIP layout.
        var inventory=new StringBuilder(sha256(manifest));
        new TreeMap<>(files).forEach((name, bytes)->inventory.append('\n').append(name).append(':').append(sha256(bytes)));
        return new ContentPack(id,version,priority,deps,overrides,provenance,assets,definitions,sha256(inventory.toString().getBytes(StandardCharsets.UTF_8)),manifest);
    }
    private static void reference(Map<String,ContentPack.Asset> assets,String id,String format) throws IOException {
        if (!id.isEmpty() && (!assets.containsKey(id) || !assets.get(id).format().equals(format))) throw new IOException("Invalid asset reference");
    }
    private static void signature(String format,String path,byte[] bytes) throws IOException {
        boolean valid=switch(format) {
            case "json" -> { if (!path.endsWith(".json")) yield false; PackJson.read(bytes); yield true; }
            case "text" -> {
                if (!path.endsWith(".txt") || bytes.length>65536) yield false;
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                yield text.chars().noneMatch(c->c==0 || (c<32 && c!=9 && c!=10 && c!=13));
            }
            case "png" -> path.endsWith(".png") && bytes.length>=24 && ByteBuffer.wrap(bytes).getLong()==0x89504e470d0a1a0aL &&
                ByteBuffer.wrap(bytes,12,4).getInt()==0x49484452 && dimension(bytes,16) && dimension(bytes,20);
            case "glb" -> path.endsWith(".glb") && bytes.length>=20 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt()==0x46546c67 &&
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4)==2 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(8)==bytes.length;
            default -> false;
        };
        if (!valid) throw new IOException("Unsupported format or invalid file signature");
    }
    private static boolean dimension(byte[] bytes,int offset) { int value=ByteBuffer.wrap(bytes).getInt(offset); return value>0 && value<=8192; }
    private static void glb(ContentPack.Asset asset, Map<String,ContentPack.Asset> assets) throws IOException {
        var buffer=ByteBuffer.wrap(asset.bytes()).order(ByteOrder.LITTLE_ENDIAN); buffer.position(12);
        JsonObject document=null; int chunks=0, binaryBytes=0;
        while(buffer.hasRemaining()) {
            if(buffer.remaining()<8) throw new IOException("Truncated GLB chunk");
            int length=buffer.getInt(), type=buffer.getInt();
            if(length<0 || length%4!=0 || length>buffer.remaining()) throw new IOException("GLB chunk size");
            if(chunks==0) {
                if(type!=0x4e4f534a || length>4*1024*1024) throw new IOException("GLB JSON chunk");
                byte[] json=new byte[length]; buffer.get(json); document=PackJson.read(json).getAsJsonObject();
            } else {
                if(chunks!=1 || type!=0x004e4942) throw new IOException("Unsupported GLB chunk");
                binaryBytes=length;
                buffer.position(buffer.position()+length);
            }
            chunks++;
        }
        if(document==null || !document.getAsJsonObject("asset").get("version").getAsString().equals("2.0")) throw new IOException("GLTF version");
        if(document.has("extensionsRequired"))for(var extension:document.getAsJsonArray("extensionsRequired"))
            if(!extension.getAsString().equals("KHR_materials_unlit"))throw new IOException("Unsupported required GLB extension");
        geometryLimits(document,binaryBytes);
        if(document.has("buffers")) for(var entry:document.getAsJsonArray("buffers"))
            if(entry.getAsJsonObject().has("uri")) throw new IOException("External GLB buffers forbidden");
        if(document.has("images")) for(var entry:document.getAsJsonArray("images")) {
            var image=entry.getAsJsonObject();
            if(!image.has("uri")||image.has("mimeType")&&!image.get("mimeType").getAsString().equals("image/png"))throw new IOException("Schema v1 textures require declared external PNG");
            String uri=image.get("uri").getAsString(); path(uri);
            int slash=asset.path().lastIndexOf('/'); String resolved=(slash<0?"":asset.path().substring(0,slash+1))+uri;
            if(assets.values().stream().noneMatch(a->a.path().equals(resolved) && a.format().equals("png")))
                throw new IOException("Image must reference declared in-pack PNG");
        }
    }
    private static void geometryLimits(JsonObject document,int binaryBytes) throws IOException {
        for(String collection:List.of("nodes","meshes","materials","skins","accessors","bufferViews","images","textures","animations"))
            if(document.has(collection)&&document.getAsJsonArray(collection).size()>4096)throw new IOException("GLB collection limit");
        if(document.has("buffers")) {
            var buffers=document.getAsJsonArray("buffers");
            if(buffers.size()!=1||integer(buffers.get(0).getAsJsonObject(),"byteLength")<0||integer(buffers.get(0).getAsJsonObject(),"byteLength")>binaryBytes)throw new IOException("GLB buffer bounds");
        }
        var views=document.has("bufferViews")?document.getAsJsonArray("bufferViews"):new JsonArray();
        for(var element:views) {
            var view=element.getAsJsonObject();long offset=view.has("byteOffset")?integer(view,"byteOffset"):0,length=integer(view,"byteLength");
            if(integer(view,"buffer")!=0||offset<0||length<0||offset+length>binaryBytes)throw new IOException("GLB buffer view bounds");
            if(view.has("byteStride")&&(integer(view,"byteStride")<4||integer(view,"byteStride")>252))throw new IOException("GLB stride");
        }
        long total=0;
        if(document.has("accessors"))for(var element:document.getAsJsonArray("accessors")) {
            var accessor=element.getAsJsonObject();int count=integer(accessor,"count"),component=integer(accessor,"componentType");
            if(count<0||count>2_000_000||(total+=count)>16_000_000||accessor.has("sparse"))throw new IOException("GLB accessor budget/sparse unsupported");
            int width=switch(component){case 5120,5121->1;case 5122,5123->2;case 5125,5126->4;default->throw new IOException("GLB component type");};
            int components=switch(string(accessor,"type",8)){case "SCALAR"->1;case "VEC2"->2;case "VEC3"->3;case "VEC4","MAT2"->4;case "MAT3"->9;case "MAT4"->16;default->throw new IOException("GLB accessor type");};
            if(!accessor.has("bufferView"))throw new IOException("GLB accessor buffer view required");
            int index=integer(accessor,"bufferView");if(index<0||index>=views.size())throw new IOException("GLB accessor view index");
            var view=views.get(index).getAsJsonObject();long offset=accessor.has("byteOffset")?integer(accessor,"byteOffset"):0;
            long size=(long)width*components,stride=view.has("byteStride")?integer(view,"byteStride"):size;
            if(offset<0||stride<size||offset+(count==0?0:(count-1)*stride+size)>integer(view,"byteLength"))throw new IOException("GLB accessor byte bounds");
        }
        if(document.has("nodes")) {
            var nodes=document.getAsJsonArray("nodes");int[] parents=new int[nodes.size()],depth=new int[nodes.size()];
            for(var element:nodes) {var node=element.getAsJsonObject();if(node.has("children"))for(var child:node.getAsJsonArray("children")) {
                int index=child.getAsBigDecimal().intValueExact();if(index<0||index>=nodes.size()||++parents[index]>1)throw new IOException("GLB node parent/index");
            }}
            var queue=new ArrayDeque<Integer>();for(int i=0;i<parents.length;i++)if(parents[i]==0)queue.add(i);int visited=0;
            while(!queue.isEmpty()) {
                int index=queue.remove();visited++;if(depth[index]>128)throw new IOException("GLB hierarchy depth");
                var node=nodes.get(index).getAsJsonObject();if(node.has("children"))for(var child:node.getAsJsonArray("children")) {
                    int c=child.getAsInt();depth[c]=depth[index]+1;queue.add(c);
                }
            }
            if(visited!=nodes.size())throw new IOException("GLB node cycle");
        }
    }
    static void fields(JsonObject object,String... allowed) throws IOException {
        if (!object.keySet().equals(Set.of(allowed))) throw new IOException("Missing/unknown metadata fields");
    }
    static String string(JsonObject object,String field,int max) throws IOException {
        var value=object.get(field);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Expected string");
        String result=value.getAsString(); if(result.length()>max || result.indexOf('\0')>=0) throw new IOException("Metadata string limit");
        if (result.isBlank() && !Set.of("prompt","model","rig").contains(field)) throw new IOException("Empty metadata string");
        return result;
    }
    private static int integer(JsonObject object,String field) throws IOException {
        var value=object.get(field);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Expected integer");
        try { return value.getAsBigDecimal().intValueExact(); } catch(ArithmeticException e) { throw new IOException("Integer range",e); }
    }
    private static String id(String value) throws IOException { if(!value.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IOException("Pack/dependency ID"); return value; }
    private static String version(String value) throws IOException { if(!value.matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")) throw new IOException("Exact semantic version required"); return value; }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
