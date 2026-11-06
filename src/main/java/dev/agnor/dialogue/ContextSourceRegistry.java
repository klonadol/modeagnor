package dev.agnor.dialogue;

import dev.agnor.lore.*;
import java.util.*;
import static dev.agnor.dialogue.ContextBuilder.Status;

/** Typed snapshot admission for Main Dialogue, not a second prompt engine. */
public final class ContextSourceRegistry {
    public record Profile(Set<String> allowlist,int loreBytes,long maxAgeMillis) {
        public Profile {allowlist=Set.copyOf(allowlist);if(!allowlist.containsAll(Set.of("definition","runtime","relationship","history"))||loreBytes<0||loreBytes>8192||maxAgeMillis<1||maxAgeMillis>300_000)throw new IllegalArgumentException("Context profile");}
        public static Profile mainDialogue(){return new Profile(Set.of("definition","runtime","relationship","history","lorebook"),2048,30_000);}
    }
    public record Snapshot(ConversationScope scope,Status status,long capturedAt,List<LoreRecord> rows) {
        public Snapshot {Objects.requireNonNull(scope);Objects.requireNonNull(status);rows=List.copyOf(rows);if(rows.size()>4096||status!=Status.AVAILABLE_WITH_DATA&&!rows.isEmpty())throw new IllegalArgumentException("Context snapshot data/status");}
        public static Snapshot available(ConversationScope scope,List<LoreRecord> rows){return new Snapshot(scope,rows.isEmpty()?Status.AVAILABLE_EMPTY:Status.AVAILABLE_WITH_DATA,System.currentTimeMillis(),rows);}
    }
    public static Snapshot admit(Snapshot input,ConversationScope scope,Profile profile,String query,String definition,String dimension,long now) {
        if(!profile.allowlist().contains("lorebook")||!input.scope().equals(scope))return new Snapshot(scope,Status.FORBIDDEN,now,List.of());
        if(input.status()!=Status.AVAILABLE_EMPTY&&input.status()!=Status.AVAILABLE_WITH_DATA)return new Snapshot(scope,input.status(),now,List.of());
        if(now<input.capturedAt()||now-input.capturedAt()>profile.maxAgeMillis())return new Snapshot(scope,Status.TEMPORARILY_UNAVAILABLE,now,List.of());
        var matches=input.rows().stream().filter(row->!row.deleted()&&row.entry().matches(query,definition,dimension))
            .sorted(Comparator.comparingInt((LoreRecord row)->row.entry().priority()).reversed().thenComparing(row->row.entry().id())).toList();
        return new Snapshot(scope,matches.isEmpty()?Status.AVAILABLE_EMPTY:Status.AVAILABLE_WITH_DATA,now,matches);
    }
}
