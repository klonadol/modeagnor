package dev.agnor.lore;

/** Source IDs carry the read revision; contents never appear in logs/toString. */
public record LoreRecord(LoreEntry entry,long revision,long updatedAt,String source,boolean deleted) {
    @Override public String toString(){return "LoreRecord[id="+entry.id()+",revision="+revision+",deleted="+deleted+",redacted]";}
}
