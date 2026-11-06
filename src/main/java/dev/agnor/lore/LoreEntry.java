package dev.agnor.lore;

import java.text.Normalizer;
import java.util.*;

/** Passive knowledge. Scope and provenance are assigned by the repository/pack source, never its text. */
public record LoreEntry(String id,String title,String content,Mode mode,List<String> triggers,int priority,
                        String role,boolean enabled,String definition,String dimension) {
    public enum Mode { ALWAYS,EXACT,KEYWORD }
    public LoreEntry {
        if(id==null||id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Lore ID");
        text(title,128);text(content,4096);Objects.requireNonNull(mode);triggers=List.copyOf(triggers);
        if(triggers.size()>16||priority< -1000||priority>1000||!Set.of("system","user","assistant").contains(role))throw new IllegalArgumentException("Lore metadata");
        for(String trigger:triggers)text(trigger,128);
        if(mode!=Mode.ALWAYS&&triggers.isEmpty())throw new IllegalArgumentException("Lore activation needs triggers");
        for(String constraint:List.of(definition,dimension))if(!constraint.equals("*")&&!constraint.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Lore constraints");
    }
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException("Lore text");}
    public boolean matches(String query,String definition,String dimension) {
        if(!enabled||!this.definition.equals("*")&&!this.definition.equals(definition)||!this.dimension.equals("*")&&!this.dimension.equals(dimension))return false;
        if(mode==Mode.ALWAYS)return true;
        String normalized=normalize(query);
        for(String trigger:triggers) {
            String key=normalize(trigger);
            if(mode==Mode.EXACT&&normalized.equals(key))return true;
            if(mode==Mode.KEYWORD)for(int at=normalized.indexOf(key);at>=0;at=normalized.indexOf(key,at+1)) {
                int end=at+key.length();
                if((at==0||!Character.isLetterOrDigit(normalized.codePointBefore(at)))&&(end==normalized.length()||!Character.isLetterOrDigit(normalized.codePointAt(end))))return true;
            }
        }
        return false;
    }
    private static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip().replaceAll("\\s+"," ");}
    @Override public String toString(){return "LoreEntry[id="+id+",mode="+mode+",redacted]";}
}
