package dev.agnor.check;
import com.google.gson.*;
import java.nio.file.*;
final class P4Files {
    static final Path ROOT=Path.of(System.getProperty("agnor.p4.root"));
    static void write(String name,JsonObject value){try{Files.createDirectories(ROOT);Path file=ROOT.resolve(name),pending=ROOT.resolve(name+".tmp");Files.writeString(pending,value.toString());Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new RuntimeException(e);}}
    static JsonObject read(String name){try{Path file=ROOT.resolve(name);return Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():null;}catch(Exception e){throw new RuntimeException(e);}}
    static void mark(String name,String status){var value=new JsonObject();value.addProperty("status",status);write(name,value);}
}
