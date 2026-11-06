package dev.agnor.provider;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Reads only the explicitly named section in the external host-owned file. Never logs or caches values. */
final class HostSecrets {
    static String resolve(DialoguePreset preset,String env) {
        if(preset.secretFile().isEmpty())return System.getenv(env);
        if(!preset.endpoint().getScheme().equals("https")||!preset.endpoint().getHost().equals("openrouter.ai"))
            throw new IllegalArgumentException("Credential target is not allowed");
        Path file=Path.of(preset.secretFile());
        try {
            if(!Files.isRegularFile(file)||Files.size(file)>65536)throw new java.io.IOException();
            try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
                boolean selected=false;String line;
                while((line=reader.readLine())!=null){
                    if(!selected){if(line.replaceAll("[^a-zA-Z]","").equalsIgnoreCase(preset.secretSection()))selected=true;continue;}
                    if(line.isBlank())continue;
                    String value=line.strip();if(!value.matches("sk-or-v1-[A-Za-z0-9_-]{16,160}"))throw new java.io.IOException();
                    return value;
                }
            }
        }catch(Exception e){throw new IllegalArgumentException("Host credential unavailable");}
        throw new IllegalArgumentException("Host credential section unavailable");
    }
}
