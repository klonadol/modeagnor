package dev.agnor.p3;

import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.joml.Matrix4f;
import java.nio.file.*;

/** Synthetic neutral descriptor evaluator, not a general GLB importer. No GemRender dependency. */
public final class HostPaletteCheck implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                JsonObject rig;
                try(var input = new java.io.InputStreamReader(HostPaletteCheck.class.getResourceAsStream("/rig-probe.json"), java.nio.charset.StandardCharsets.UTF_8)) {
                    rig = JsonParser.parseReader(input).getAsJsonObject();
                }
                var nodes = rig.getAsJsonArray("nodes");
                var joints = rig.getAsJsonArray("joints");
                var result = new JsonArray();
                for (int copy = 0; copy < 2; copy++) {
                    var matrices = new JsonArray();
                    var palette = new java.util.ArrayList<Matrix4f>();
                    for(int i=0;i<nodes.size();i++) {
                        var node = nodes.get(i).getAsJsonObject();
                        var local = translation(node.getAsJsonArray("translation"));
                        if(copy==1 && node.get("name").getAsString().equals("tip")) local.translate(0,.4f,0);
                        int parent=node.get("parent").getAsInt();
                        if(parent < -1 || parent >= i) throw new IllegalArgumentException("Non-topological rig");
                        palette.add(parent < 0 ? local : new Matrix4f(palette.get(parent)).mul(local));
                    }
                    for(var entry : joints) {
                        var joint=entry.getAsJsonObject();
                        int index=joint.get("node").getAsInt();
                        if(index<0 || index>=nodes.size()) throw new IllegalArgumentException("Joint index");
                        palette.add(new Matrix4f(palette.get(index)).mul(translation(joint.getAsJsonArray("inverseBindTranslation"))));
                    }
                    for (var matrix : palette) {
                        var values = new JsonArray();
                        for(float f : matrix.get(new float[16])) values.add(f);
                        matrices.add(values);
                    }
                    var pose = new JsonObject(); pose.add("matrices", matrices);
                    pose.addProperty("weight", copy);
                    result.add(pose);
                }
                var output = Path.of(System.getProperty("agnor.p3.hostOutput"));
                Files.writeString(output, result.toString());
                System.out.println("P3_HOST_PALETTE_PASS copies=2 gpuDependencies=none output=" + output);
                server.stop(false);
            } catch(Exception error) { throw new RuntimeException(error); }
        });
    }
    private static Matrix4f translation(JsonArray values) {
        if(values.size()!=3) throw new IllegalArgumentException("Translation shape");
        float[] v=new float[3];
        for(int i=0;i<3;i++) { v[i]=values.get(i).getAsFloat(); if(!Float.isFinite(v[i])) throw new IllegalArgumentException("Non-finite translation"); }
        return new Matrix4f().translation(v[0],v[1],v[2]);
    }
}
