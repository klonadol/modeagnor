package dev.agnor.motion;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Vector3f;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionRigTest {
    @TempDir Path directory;
    JsonObject asset(){
        return JsonParser.parseString("""
            {"sha256":"%s","nodes":[{"name":"root","children":[1]},{"name":"tip","translation":[0,1,0]}],
            "skin":{"joints":[0,1]},"inverseBindMatrices":[[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]],
            "parts":[{"file":"part-000.glb","sourceJointIndices":[0,1],"sourceMeshNode":0,"slots":5,"morphCount":0}],"morphTargetNames":[]}
            """.formatted("a".repeat(64))).getAsJsonObject();
    }
    Path write(JsonObject a)throws Exception{var p=directory.resolve("capabilities.json");Files.writeString(p,a.toString());return p;}
    @Test void validatedRigProducesFreshIndependentPalettes()throws Exception{
        var r=MotionRig.read(write(asset()),"models/face");assertEquals(2,r.rig().size());assertEquals(1,r.parts().size());assertEquals(0,r.morphCount());assertEquals(-1,r.bone("hips"));
        var palette=r.palettes(r.rig().rest());assertEquals(5,palette[0].length);assertEquals(1,palette[0][4].getTranslation(new Vector3f()).y);
        palette[0][4].identity();assertEquals(1,r.palettes(r.rig().rest())[0][4].getTranslation(new Vector3f()).y);
        assertThrows(IllegalArgumentException.class,r::locomotion);
        assertEquals("content/example.pack/models/face",MotionRig.read(write(asset()),"content/example.pack/models/face").prefix());
    }
    @Test void cyclesSkinBoundsAndResourcePathsFailClosed()throws Exception{
        var a=asset();a.getAsJsonArray("nodes").get(1).getAsJsonObject().add("children",JsonParser.parseString("[0]"));var p=write(a);assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"models/face"));
        a=asset();a.getAsJsonArray("parts").get(0).getAsJsonObject().add("sourceJointIndices",JsonParser.parseString("[0,2]"));write(a);assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"models/face"));
        a=asset();a.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("file","../other.glb");write(a);assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"models/face"));
        assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"../models"));
    }
    @Test void metadataAndMorphLayoutBudgetsAreExplicit()throws Exception{
        var a=asset();a.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("morphCount",1);var p=write(a);assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"models/face"));
        Files.writeString(p," ".repeat(4_194_305));assertThrows(IllegalArgumentException.class,()->MotionRig.read(p,"models/face"));
    }
}
