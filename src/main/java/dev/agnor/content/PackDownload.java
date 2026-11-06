package dev.agnor.content;

import dev.agnor.network.PackWire;
import java.io.*;
import java.util.UUID;

/** Consent-scoped assembler. Unsolicited, replayed, reordered or mixed-version chunks are rejected. */
public final class PackDownload {
    private final UUID token;
    private final PackWire.Offer offer;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private int total=-1;
    private String archiveHash;
    public PackDownload(UUID token,PackWire.Offer offer) {if(!offer.allowed())throw new IllegalArgumentException("Download not offered");this.token=token;this.offer=offer;}
    public UUID token(){return token;}
    public PackWire.Offer offer(){return offer;}
    public int offset(){return bytes.size();}
    public boolean accept(PackWire.Response response)throws IOException {
        byte[] chunk=response.bytes();
        if(response.version()!=PackWire.VERSION||response.status()!=PackWire.Status.CHUNK||!response.token().equals(token)||!response.id().equals(offer.id())||!response.digest().equals(offer.digest())||response.offset()!=bytes.size()||response.total()<1||response.total()>PackTransfer.MAX_BYTES||chunk.length<1||chunk.length>PackTransfer.CHUNK_BYTES||(long)bytes.size()+chunk.length>response.total())throw new IOException("Unexpected transfer chunk");
        if(total<0){total=response.total();archiveHash=response.archiveHash();}
        if(response.total()!=total||!response.archiveHash().equals(archiveHash))throw new IOException("Mixed transfer inventory");
        bytes.write(chunk);
        return bytes.size()==total;
    }
    /** Call from the installation IO worker, never from a tick/network callback. */
    public byte[] complete()throws IOException {if(bytes.size()!=total)throw new IOException("Incomplete transfer");byte[] result=bytes.toByteArray();if(!PackLoader.sha256(result).equals(archiveHash))throw new IOException("Archive checksum mismatch");return result;}
}
