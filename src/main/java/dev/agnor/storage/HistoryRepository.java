package dev.agnor.storage;

import dev.agnor.dialogue.*;
import java.sql.*;
import java.util.*;

/** Typed History writer/reader, using the same WorldStorage transaction boundary as NPCs. */
public final class HistoryRepository {
    public enum State { ACCEPTED, RUNNING, FINALIZED, FAILED, CANCELLED, INTERRUPTED }
    public record Turn(ConversationScope scope, UUID request, State state, HistoryMessage inbound, HistoryMessage reply, String error) {
        @Override public String toString() { return "Turn[request=" + request + ",state=" + state + ",redacted]"; }
    }
    public record Event(long sequence, UUID id, ConversationScope scope, UUID message, long messageSequence, String kind) { }
    private final WorldStorage storage;
    public HistoryRepository(WorldStorage storage) { this.storage = storage; }

    public Turn accept(ConversationScope scope, UUID request, String text) throws SQLException {
        validateText(text, 1024); Objects.requireNonNull(request);
        Turn[] out = new Turn[1];
        storage.transaction(scope.world(), c -> {
            requireNpc(c, scope);
            try (var s = c.prepareStatement("INSERT INTO conversation(id,npc,player) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING")) {
                s.setString(1, scope.conversation().toString()); s.setString(2, scope.npc().toString()); s.setString(3, scope.player().toString()); s.executeUpdate();
            }
            verifyScope(c, scope);
            Turn existing = turn(c, scope, request);
            if (existing != null) {
                if (!existing.inbound().text().equals(text)) throw new SQLException("Request ID payload conflict");
                out[0] = existing; return;
            }
            try (var s = c.prepareStatement("SELECT count(*) FROM dialogue_turn WHERE conversation=? AND state IN ('ACCEPTED','RUNNING')")) {
                s.setString(1, scope.conversation().toString());
                try (var r = s.executeQuery()) { r.next(); if (r.getInt(1) != 0) throw new SQLException("Conversation busy"); }
            }
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT (SELECT count(*) FROM history_message) + (SELECT count(*) FROM dialogue_turn WHERE state IN ('ACCEPTED','RUNNING'))")) {
                r.next(); if (r.getInt(1) >= 99999) throw new SQLException("Protected History capacity reached");
            }
            try (var s = c.prepareStatement("INSERT INTO dialogue_turn VALUES (?,?,'ACCEPTED','')")) {
                s.setString(1, scope.conversation().toString()); s.setString(2, request.toString()); s.executeUpdate();
            }
            insertMessage(c, scope, request, "user", text, "");
            out[0] = turn(c, scope, request);
        }); return out[0];
    }
    public boolean claim(ConversationScope scope, UUID request) throws SQLException {
        boolean[] claimed = {false};
        storage.transaction(scope.world(), c -> {
            verifyScope(c, scope); requireNpc(c, scope);
            try (var s = c.prepareStatement("UPDATE dialogue_turn SET state='RUNNING' WHERE conversation=? AND request=? AND state='ACCEPTED'")) {
                key(s, scope, request); claimed[0] = s.executeUpdate() == 1;
            }
        }); return claimed[0];
    }
    public Turn finalizeReply(ConversationScope scope, UUID request, String text, String model) throws SQLException {
        validateText(text, 4096);
        if (model == null || !model.matches("[a-zA-Z0-9_./:-]{1,128}")) throw new IllegalArgumentException("Invalid model metadata");
        Turn[] out = new Turn[1];
        storage.transaction(scope.world(), c -> {
            verifyScope(c, scope); requireNpc(c, scope);
            Turn existing = turn(c, scope, request);
            if (existing == null) throw new SQLException("Unknown turn");
            if (existing.state() == State.FINALIZED) {
                if (!existing.reply().text().equals(text) || !existing.reply().model().equals(model)) throw new SQLException("Final reply conflict");
                out[0] = existing; return;
            }
            if (existing.state() != State.RUNNING) throw new SQLException("Turn is terminal or not claimed");
            insertMessage(c, scope, request, "assistant", text, model);
            try (var s = c.prepareStatement("UPDATE dialogue_turn SET state='FINALIZED',error='' WHERE conversation=? AND request=?")) {
                key(s, scope, request); s.executeUpdate();
            }
            out[0] = turn(c, scope, request);
        }); return out[0];
    }
    public void terminate(ConversationScope scope, UUID request, State state, String error) throws SQLException {
        if (!Set.of(State.FAILED, State.CANCELLED, State.INTERRUPTED).contains(state) || error == null || !error.matches("[A-Z_]{0,40}"))
            throw new IllegalArgumentException("Invalid terminal status");
        storage.transaction(scope.world(), c -> {
            verifyScope(c, scope);
            try (var s = c.prepareStatement("UPDATE dialogue_turn SET state=?,error=? WHERE conversation=? AND request=? AND state IN ('ACCEPTED','RUNNING')")) {
                s.setString(1, state.name()); s.setString(2, error); s.setString(3, scope.conversation().toString()); s.setString(4, request.toString()); s.executeUpdate();
            }
        });
    }
    /** A previous process may have sent inference. Never repeat uncertain jobs automatically. */
    public void recoverInterrupted(UUID world) throws SQLException {
        storage.transaction(world, c -> c.createStatement().executeUpdate(
                "UPDATE dialogue_turn SET state='INTERRUPTED',error='RESTART' WHERE state IN ('ACCEPTED','RUNNING')"));
    }
    public Turn find(ConversationScope scope, UUID request) throws SQLException {
        Turn[] out = new Turn[1]; storage.transaction(scope.world(), c -> { if (hasConversation(c, scope)) out[0] = turn(c, scope, request); }); return out[0];
    }
    public List<HistoryMessage> recent(ConversationScope scope, int limit) throws SQLException {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("History window must be 1..100");
        List<HistoryMessage> out = new ArrayList<>();
        storage.transaction(scope.world(), c -> {
            if (!hasConversation(c, scope)) { requireNpc(c, scope); return; }
            try (var s = c.prepareStatement("SELECT * FROM history_message WHERE conversation=? ORDER BY sequence DESC LIMIT ?")) {
                s.setString(1, scope.conversation().toString()); s.setInt(2, limit);
                try (var r = s.executeQuery()) { while (r.next()) out.add(message(r, scope)); }
            }
        }); Collections.reverse(out); return List.copyOf(out);
    }
    public List<Event> events(ConversationScope scope, long after, int limit) throws SQLException {
        if (after < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid event page");
        List<Event> out = new ArrayList<>();
        storage.transaction(scope.world(), c -> {
            verifyScope(c, scope);
            try (var s = c.prepareStatement("SELECT * FROM dialogue_event WHERE conversation=? AND event_sequence>? ORDER BY event_sequence LIMIT ?")) {
                s.setString(1, scope.conversation().toString()); s.setLong(2, after); s.setInt(3, limit);
                try (var r = s.executeQuery()) { while (r.next()) out.add(new Event(r.getLong("event_sequence"), UUID.fromString(r.getString("id")), scope,
                        UUID.fromString(r.getString("message")), r.getLong("message_sequence"), r.getString("kind"))); }
            }
        }); return List.copyOf(out);
    }
    /** Stable before-sequence pagination; new messages do not shift older pages. */
    public List<HistoryMessage> page(ConversationScope scope,long before,int limit)throws SQLException {
        if(before<1||limit<1||limit>20)throw new IllegalArgumentException("History page");var result=new ArrayList<HistoryMessage>();
        storage.transaction(scope.world(),c->{if(!hasConversation(c,scope)){requireNpc(c,scope);return;}
            try(var s=c.prepareStatement("SELECT * FROM history_message WHERE conversation=? AND sequence<? ORDER BY sequence DESC LIMIT ?")) {
                s.setString(1,scope.conversation().toString());s.setLong(2,before);s.setInt(3,limit);try(var r=s.executeQuery()){while(r.next())result.add(message(r,scope));}
            }
        });Collections.reverse(result);return List.copyOf(result);
    }
    public record Retention(long total,long candidates,long protectedSources,long deleted,String reason){}
    /** No consumer is enabled in P5: absence of a release watermark is never evidence of processing. */
    public Retention retention(ConversationScope scope,int keep,boolean apply)throws SQLException {
        if(keep<2||keep>100_000)throw new IllegalArgumentException("Retention target");long[] total={0};
        storage.transaction(scope.world(),c->{if(!hasConversation(c,scope)){requireNpc(c,scope);return;}
            try(var s=c.prepareStatement("SELECT count(*) FROM history_message WHERE conversation=?")){s.setString(1,scope.conversation().toString());try(var r=s.executeQuery()){r.next();total[0]=r.getLong(1);}}
        });long candidates=Math.max(0,total[0]-keep);
        return new Retention(total[0],candidates,candidates,0,candidates>0?"PROTECTED_NO_CONSUMER_WATERMARK":"WITHIN_RETENTION_TARGET");
    }
    private static void insertMessage(Connection c, ConversationScope scope, UUID request, String role, String text, String model) throws SQLException {
        long sequence;
        try (var s = c.prepareStatement("SELECT next_sequence FROM conversation WHERE id=?")) {
            s.setString(1, scope.conversation().toString()); try (var r = s.executeQuery()) { r.next(); sequence = r.getLong(1); }
        }
        UUID id = UUID.randomUUID();
        try (var s = c.prepareStatement("INSERT INTO history_message(id,conversation,request,sequence,role,text,created_at,model) VALUES (?,?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setString(2, scope.conversation().toString()); s.setString(3, request.toString()); s.setLong(4, sequence);
            s.setString(5, role); s.setString(6, text); s.setLong(7, System.currentTimeMillis()); s.setString(8, model); s.executeUpdate();
        }
        try (var s = c.prepareStatement("INSERT INTO dialogue_event(id,message,conversation,message_sequence,kind) VALUES (?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, id.toString()); s.setString(3, scope.conversation().toString());
            s.setLong(4, sequence); s.setString(5, role.equals("user") ? "INBOUND_ACCEPTED" : "REPLY_FINALIZED"); s.executeUpdate();
        }
        try (var s = c.prepareStatement("UPDATE conversation SET next_sequence=next_sequence+1 WHERE id=?")) { s.setString(1, scope.conversation().toString()); s.executeUpdate(); }
    }
    private static Turn turn(Connection c, ConversationScope scope, UUID request) throws SQLException {
        State state; String error;
        try (var s = c.prepareStatement("SELECT state,error FROM dialogue_turn WHERE conversation=? AND request=?")) {
            key(s, scope, request); try (var r = s.executeQuery()) { if (!r.next()) return null; state = State.valueOf(r.getString(1)); error = r.getString(2); }
        }
        HistoryMessage inbound = null, reply = null;
        try (var s = c.prepareStatement("SELECT * FROM history_message WHERE conversation=? AND request=? ORDER BY sequence")) {
            key(s, scope, request); try (var r = s.executeQuery()) { while (r.next()) {
                HistoryMessage m = message(r, scope); if (m.role().equals("user")) inbound = m; else reply = m;
            } }
        }
        if (inbound == null || (state == State.FINALIZED && reply == null)) throw new SQLException("Incomplete committed turn");
        return new Turn(scope, request, state, inbound, reply, error);
    }
    private static HistoryMessage message(ResultSet r, ConversationScope scope) throws SQLException {
        return new HistoryMessage(scope, UUID.fromString(r.getString("id")), UUID.fromString(r.getString("request")), r.getLong("sequence"),
                r.getString("role"), r.getString("text"), r.getLong("created_at"), r.getString("model"));
    }
    private static void verifyScope(Connection c, ConversationScope scope) throws SQLException {
        if (!hasConversation(c,scope)) throw new SQLException("Conversation scope unavailable");
    }
    private static boolean hasConversation(Connection c, ConversationScope scope) throws SQLException {
        try (var s = c.prepareStatement("SELECT npc,player FROM conversation WHERE id=?")) {
            s.setString(1, scope.conversation().toString()); try (var r = s.executeQuery()) {
                if (!r.next()) return false;
                if (!r.getString(1).equals(scope.npc().toString()) || !r.getString(2).equals(scope.player().toString())) throw new SQLException("Conversation scope unavailable");
                return true;
            }
        }
    }
    private static void requireNpc(Connection c, ConversationScope scope) throws SQLException {
        try (var s = c.prepareStatement("SELECT deleted FROM npc WHERE id=?")) {
            s.setString(1, scope.npc().toString()); try (var r = s.executeQuery()) { if (!r.next() || r.getBoolean(1)) throw new SQLException("NPC unavailable"); }
        }
    }
    private static void key(PreparedStatement s, ConversationScope scope, UUID request) throws SQLException { s.setString(1, scope.conversation().toString()); s.setString(2, request.toString()); }
    public static void validateText(String text, int max) {
        if (text == null || text.isBlank() || text.length() > max || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw new IllegalArgumentException("Invalid dialogue text");
    }
}
