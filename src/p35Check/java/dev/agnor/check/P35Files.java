package dev.agnor.check;
import com.google.gson.*;
import java.nio.file.*;
final class P35Files {
    static final Path ROOT=Path.of(System.getProperty("agnor.p35.root"));static final String STAGE=System.getProperty("agnor.p35.stage","smoke");
    private static final java.util.Map<String,String> pending=new java.util.HashMap<>();
    static synchronized void write(String name,JsonObject data){pending.put(name,data.toString());flush();}
    private static void flush(){
        for(var entry:java.util.List.copyOf(pending.entrySet()))try{
            Files.createDirectories(ROOT);var p=ROOT.resolve(entry.getKey());var temp=ROOT.resolve(entry.getKey()+".tmp");Files.writeString(temp,entry.getValue());Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);pending.remove(entry.getKey());
        }catch(AccessDeniedException locked){/* Windows readers can hold the target. Retry on the next fixture tick, never sleep/block the game. */}
        catch(Exception e){throw new RuntimeException(e);}
    }
    static synchronized JsonObject read(String name){flush();try{var p=ROOT.resolve(name);return Files.exists(p)?JsonParser.parseString(Files.readString(p)).getAsJsonObject():null;}catch(Exception e){throw new RuntimeException(e);}}
}
