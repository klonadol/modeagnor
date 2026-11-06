package dev.agnor.network;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.agnor.network.DialogueWire.*;

class DialogueWireTest{
    @Test void roundTripAndStrictBounds(){
        var request=new Request(VERSION,4,Action.SAY,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"я".repeat(1024));
        var bytes=encode(request);assertEquals(request,request(bytes));assertTrue(valid(request));
        assertThrows(IllegalArgumentException.class,()->request(Arrays.copyOf(bytes,bytes.length-1)));
        assertThrows(IllegalArgumentException.class,()->request(Arrays.copyOf(bytes,bytes.length+1)));
        assertThrows(IllegalArgumentException.class,()->request(new byte[MAX_REQUEST+1]));
        bytes[12]=(byte)99;assertThrows(IllegalArgumentException.class,()->request(bytes));
        assertFalse(valid(new Request(VERSION,1,Action.SAY,request.world(),request.npc(),request.request(),"x".repeat(1025))));
        var response=new Response(VERSION,4,request.npc(),request.request(),Status.FINAL,UUID.randomUUID(),2,"assistant","я".repeat(4096),"");
        assertEquals(response,response(encode(response)));assertEquals(Presentation.SPEAKING,response.presentation());
        assertThrows(IllegalArgumentException.class,()->encode(new Response(VERSION,4,request.npc(),request.request(),Status.FAILED,NpcWire.NIL,0,"","secret-provider-payload","")));
        assertFalse(response.toString().contains("яя"));
    }
}
