package dev.agnor.check;
import com.google.gson.*;
import java.nio.file.*;
final class P5Files {
    static final Path ROOT=Path.of(System.getProperty("agnor.p5.root"));static final String STAGE=System.getProperty("agnor.p5.stage","crud");
    static void write(String name,JsonObject value){try{Files.createDirectories(ROOT);Path p=ROOT.resolve(name),t=ROOT.resolve(name+".tmp");Files.writeString(t,value.toString());Files.move(t,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new RuntimeException(e);}}
    static JsonObject read(String name){try{var p=ROOT.resolve(name);return Files.exists(p)?JsonParser.parseString(Files.readString(p)).getAsJsonObject():null;}catch(Exception e){throw new RuntimeException(e);}}
    static void mark(String name,String status){var o=new JsonObject();o.addProperty("status",status);write(name,o);}
}
