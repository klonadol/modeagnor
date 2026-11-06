package dev.agnor.check;
import java.nio.file.*;
final class P2Files {
    static final Path ROOT=Path.of(System.getProperty("agnor.p2.root"));
    static final String WORLD=System.getProperty("agnor.p2.world"),STAGE=System.getProperty("agnor.p2.stage");
    static String key(String s){return WORLD+STAGE+"-"+s;}
    static boolean exists(String s){return Files.exists(ROOT.resolve(s));}
    static void write(String s,String text){try{Files.createDirectories(ROOT);Files.writeString(ROOT.resolve(s),text);}catch(Exception e){throw new IllegalStateException(e);}}
    static String read(String s){try{return Files.readString(ROOT.resolve(s)).trim();}catch(Exception e){throw new IllegalStateException(e);}}
}
