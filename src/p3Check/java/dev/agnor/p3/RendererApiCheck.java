package dev.agnor.p3;

import com.wf.gemrender.gltf.*;
import com.wf.gemrender.gltf.morph.*;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import org.joml.Matrix4f;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deliberately runs without Minecraft, Fabric, LWJGL or a graphics context. */
public final class RendererApiCheck {
    public static void main(String[] args) throws Exception {
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            var skeleton = GltfSkeletonReader.read(input, null);
            var palette = skeleton.newPalette();
            var clip = skeleton.animations().get("probe");
            require(clip != null, "clip import");
            GltfPose.evaluate(skeleton.layout(), clip, .5f, palette);
            close(palette[skeleton.layout().skins().getFirst().base() + 1].m31(), .5f, "clip interpolation");
            System.out.println("P3_SKELETON_READER_PASS clip=probe time=0.5 translation=0.5");
        }
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            var model = new GltfModelReader().readWithoutReferences(input);
            var layout = GltfPaletteLayout.of(model);
            var table = layout.nodeTable();
            require(table.nodeCount() == 3 && layout.skins().size() == 1, "rig import");
            var meshNode = model.getNodeModels().get(2);
            var mesh = meshNode.getMeshModels().getFirst();
            var primitive = mesh.getMeshPrimitiveModels().getFirst();
            var targets = MorphTargets.of(primitive, 3);
            var builder = GltfMorphLayout.builder();
            builder.add(table, meshNode, mesh, targets, 0);
            var morphs = builder.build();
            var state = table.newScratch();
            var palette = new Matrix4f[layout.size()];
            for (int i = 0; i < palette.length; i++) palette[i] = new Matrix4f();
            var weights = new float[morphs.blockFloats()];
            var scratch = new GltfPose.Scratch();
            GltfPose.evaluate(layout, state, palette, morphs, weights, scratch);
            int child = table.slotOfName("tip");
            close(palette[child].m31(), 1, "rest global transform");
            int skinBase = layout.skins().getFirst().base();
            close(palette[skinBase + 1].m31(), 0, "inverse bind cancellation");
            table.setTranslation(state, child, 0, 2, 0);
            state[table.weightBase(table.slotOfName("mesh"))] = 0.75f;
            GltfPose.evaluate(layout, state, palette, morphs, weights, scratch);
            close(palette[skinBase + 1].m31(), 1, "external pose override");
            close(weights[4], .75f, "external morph override");
            table.resetToRest(state);
            GltfPose.evaluate(layout, state, palette, morphs, weights, scratch);
            close(weights[4], 0, "morph reset");
            close(palette[skinBase + 1].m31(), 0, "pose reset");
            System.out.println("P3_HEADLESS_PASS nodes=3 joints=2 vertices=3 morphs=1 externalPose=PASS morphBlock=PASS reset=PASS");
        }
    }
    private static void close(float actual, float expected, String label) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) < 0.00001f, label + ": " + actual);
    }
    private static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
