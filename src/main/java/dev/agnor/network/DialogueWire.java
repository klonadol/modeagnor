package dev.agnor.network;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Versioned private DTO. It has no client-supplied player/conversation identity or provider settings. */
public final class DialogueWire {
    public static final int VERSION=1, MAX_REQUEST=4200, MAX_RESPONSE=16600;
    public enum Action { SAY, RETRY, HISTORY, CANCEL }
    public enum Presentation { LISTENING, THINKING, SPEAKING, ERROR }
    public enum Status { ACCEPTED, FINAL, HISTORY, FAILED, CANCELLED, BUSY, DENIED, INVALID, VERSION_MISMATCH, UNAVAILABLE, PENDING, INTERRUPTED }
    public record Request(int version,long sequence,Action action,UUID world,UUID npc,UUID request,String text) {
        @Override public String toString(){return "DialogueRequest[request="+request+",redacted]";}
    }
    public record Response(int version,long sequence,UUID npc,UUID request,Status status,UUID message,long messageSequence,String role,String text,String error) {
        public Presentation presentation(){return status==Status.FINAL?Presentation.SPEAKING:status==Status.ACCEPTED||status==Status.PENDING?Presentation.THINKING:status==Status.HISTORY?Presentation.LISTENING:Presentation.ERROR;}
        @Override public String toString(){return "DialogueResponse[request="+request+",status="+status+",redacted]";}
    }
    public static boolean valid(Request r){
        if(r==null||r.action()==null||r.sequence()<0||r.world()==null||r.npc()==null||r.request()==null||r.text()==null
                ||r.npc().equals(NpcWire.NIL)||r.request().equals(NpcWire.NIL))return false;
        if(r.action()!=Action.SAY)return r.text().isEmpty();
        try{dev.agnor.storage.HistoryRepository.validateText(r.text(),1024);return true;}catch(IllegalArgumentException e){return false;}
    }
    public static byte[] encode(Request r){return write(out->{out.writeInt(r.version());out.writeLong(r.sequence());out.writeByte(r.action().ordinal());uuid(out,r.world());uuid(out,r.npc());uuid(out,r.request());string(out,r.text(),4096);},MAX_REQUEST);}
    public static Request request(byte[] bytes){return read(bytes,MAX_REQUEST,in->{int version=in.readInt();long seq=in.readLong();int action=in.readUnsignedByte();if(action>=Action.values().length)throw new IOException();return new Request(version,seq,Action.values()[action],uuid(in),uuid(in),uuid(in),string(in,4096));});}
    public static byte[] encode(Response r){return write(out->{
        if(!valid(r))throw new IOException();out.writeInt(r.version());out.writeLong(r.sequence());uuid(out,r.npc());uuid(out,r.request());out.writeByte(r.status().ordinal());uuid(out,r.message());out.writeLong(r.messageSequence());string(out,r.role(),9);string(out,r.text(),16384);string(out,r.error(),40);
    },MAX_RESPONSE);}
    public static Response response(byte[] bytes){return read(bytes,MAX_RESPONSE,in->{
        int version=in.readInt();long seq=in.readLong();UUID npc=uuid(in),request=uuid(in);int status=in.readUnsignedByte();if(status>=Status.values().length)throw new IOException();
        var r=new Response(version,seq,npc,request,Status.values()[status],uuid(in),in.readLong(),string(in,9),string(in,16384),string(in,40));if(!valid(r))throw new IOException();return r;
    });}
    private static boolean valid(Response r){
        if(r.version()!=VERSION||r.sequence()<0||r.messageSequence()<0||!Set.of("","user","assistant").contains(r.role())||!r.error().matches("[A-Z_]{0,40}")||r.text().length()>4096)return false;
        boolean message=r.status()==Status.FINAL||r.status()==Status.HISTORY;
        return message? !r.message().equals(NpcWire.NIL)&&r.messageSequence()>0&&!r.text().isBlank()&&!r.role().isEmpty()
                :r.message().equals(NpcWire.NIL)&&r.messageSequence()==0&&r.role().isEmpty()&&r.text().isEmpty();
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void string(DataOutputStream out,String value,int max)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);if(b.length>max)throw new IOException();out.writeInt(b.length);out.write(b);}
    private static String string(DataInputStream in,int max)throws IOException{int length=in.readInt();if(length<0||length>max||length>in.available())throw new IOException();return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();}
    private static byte[] write(Writer writer,int max){try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.run(out);}if(bytes.size()>max)throw new IOException();return bytes.toByteArray();}catch(Exception e){throw new IllegalArgumentException("Invalid dialogue wire");}}
    private static <T>T read(byte[] bytes,int max,Reader<T> reader){try{if(bytes.length>max)throw new IOException();var in=new DataInputStream(new ByteArrayInputStream(bytes));T value=reader.run(in);if(in.available()!=0)throw new IOException();return value;}catch(Exception e){throw new IllegalArgumentException("Invalid dialogue wire");}}
    @FunctionalInterface private interface Writer{void run(DataOutputStream out)throws Exception;}
    @FunctionalInterface private interface Reader<T>{T run(DataInputStream in)throws Exception;}
}
