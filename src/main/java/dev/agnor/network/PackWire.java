package dev.agnor.network;

import dev.agnor.content.PackTransfer;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Public catalog and explicit pull transfer; no player-supplied permissions, paths, or URLs. */
public final class PackWire {
    public static final int VERSION=1,MAX_REQUEST=256,MAX_RESPONSE=31*1024;
    public enum Action { LIST,FETCH,NEXT,CANCEL }
    public enum Status { CATALOG,CHUNK,DENIED,STALE,INVALID,VERSION_MISMATCH,BUSY,UNAVAILABLE,CANCELLED }
    public record Request(int version,UUID token,Action action,String id,String digest,int offset) {}
    public record Offer(String id,String version,String digest,boolean allowed) {}
    public record Response(int version,UUID token,Status status,List<Offer> offers,String id,String digest,int offset,int total,String archiveHash,byte[] bytes) {
        public Response {offers=List.copyOf(offers);bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    public static boolean valid(Request r) {
        if(r==null||r.token()==null||r.token().equals(new UUID(0,0))||r.action()==null||r.id()==null||r.digest()==null||r.offset()<0||r.offset()>PackTransfer.MAX_BYTES)return false;
        return switch(r.action()) {
            case LIST,CANCEL -> r.id().isEmpty()&&r.digest().isEmpty()&&r.offset()==0;
            case FETCH -> id(r.id())&&hash(r.digest())&&r.offset()==0;
            case NEXT -> id(r.id())&&hash(r.digest())&&r.offset()>0;
        };
    }
    public static Response status(UUID token,Status status) {return new Response(VERSION,token,status,List.of(),"","",0,0,"",new byte[0]);}
    public static byte[] encode(Request r) {return write(out->{if(!valid(r))throw new IOException();out.writeInt(r.version());uuid(out,r.token());out.writeByte(r.action().ordinal());string(out,r.id(),64);string(out,r.digest(),64);out.writeInt(r.offset());},MAX_REQUEST);}
    public static Request request(byte[] bytes) {return read(bytes,MAX_REQUEST,in->{int v=in.readInt();UUID token=uuid(in);int action=in.readUnsignedByte();if(action>=Action.values().length)throw new IOException();var r=new Request(v,token,Action.values()[action],string(in,64),string(in,64),in.readInt());if(!valid(r))throw new IOException();return r;});}
    public static byte[] encode(Response r) {return write(out->{
        validate(r);out.writeInt(r.version());uuid(out,r.token());out.writeByte(r.status().ordinal());out.writeByte(r.offers().size());
        for(var offer:r.offers()){string(out,offer.id(),64);string(out,offer.version(),32);string(out,offer.digest(),64);out.writeBoolean(offer.allowed());}
        string(out,r.id(),64);string(out,r.digest(),64);out.writeInt(r.offset());out.writeInt(r.total());string(out,r.archiveHash(),64);out.writeInt(r.bytes.length);out.write(r.bytes);
    },MAX_RESPONSE);}
    public static Response response(byte[] bytes) {return read(bytes,MAX_RESPONSE,in->{
        int v=in.readInt();UUID token=uuid(in);int status=in.readUnsignedByte(),count=in.readUnsignedByte();if(status>=Status.values().length||count>32)throw new IOException();
        var offers=new ArrayList<Offer>();for(int i=0;i<count;i++)offers.add(new Offer(string(in,64),string(in,32),string(in,64),in.readBoolean()));
        String id=string(in,64),digest=string(in,64);int offset=in.readInt(),total=in.readInt();String hash=string(in,64);int length=in.readInt();
        if(length<0||length>PackTransfer.CHUNK_BYTES||length>in.available())throw new IOException();
        var r=new Response(v,token,Status.values()[status],offers,id,digest,offset,total,hash,in.readNBytes(length));validate(r);return r;
    });}
    private static void validate(Response r)throws IOException {
        if(r.version()!=VERSION||r.token()==null||r.status()==null||r.offers().size()>32)throw new IOException();
        var seen=new HashSet<String>();for(var offer:r.offers())if(!id(offer.id())||!hash(offer.digest())||!offer.version().matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")||!seen.add(offer.id()))throw new IOException();
        if(r.status()==Status.CHUNK) {
            if(!r.offers().isEmpty()||!id(r.id())||!hash(r.digest())||!hash(r.archiveHash())||r.offset()<0||r.total()<1||r.total()>PackTransfer.MAX_BYTES||r.bytes.length<1||r.bytes.length>PackTransfer.CHUNK_BYTES||(long)r.offset()+r.bytes.length>r.total())throw new IOException();
        } else if(!r.id().isEmpty()||!r.digest().isEmpty()||!r.archiveHash().isEmpty()||r.offset()!=0||r.total()!=0||r.bytes.length!=0||r.status()!=Status.CATALOG&&!r.offers().isEmpty())throw new IOException();
    }
    private static boolean id(String s){return s!=null&&s.matches("[a-z][a-z0-9_.-]{0,63}");}
    private static boolean hash(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void string(DataOutputStream out,String s,int max)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);if(bytes.length>max)throw new IOException();out.writeInt(bytes.length);out.write(bytes);}
    private static String string(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max||n>in.available())throw new IOException();return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(n))).toString();}
    private static byte[] write(Writer writer,int max){try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.run(out);}if(bytes.size()>max)throw new IOException();return bytes.toByteArray();}catch(Exception e){throw new IllegalArgumentException("Invalid pack wire");}}
    private static <T>T read(byte[] bytes,int max,Reader<T> reader){try{if(bytes.length>max)throw new IOException();var in=new DataInputStream(new ByteArrayInputStream(bytes));T value=reader.run(in);if(in.available()!=0)throw new IOException();return value;}catch(Exception e){throw new IllegalArgumentException("Invalid pack wire");}}
    @FunctionalInterface private interface Writer{void run(DataOutputStream out)throws Exception;}
    @FunctionalInterface private interface Reader<T>{T run(DataInputStream in)throws Exception;}
}
