package dev.agnor.p3;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.*;
import com.wf.gemrender.gltf.*;
import com.wf.gemrender.texture.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.nio.file.Path;

/** Isolated visual proof, no NPC/gameplay integration. */
public final class PaletteScreenCheck implements ClientModInitializer {
    private int ticks;
    private CheckScreen screen;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks == 100) { screen = new CheckScreen(); client.setScreen(screen); }
            if (ticks == 200) capture(client, "host-palette-proof.png");
            if (ticks == 240) client.reloadResources().thenRun(() -> System.out.println("P3_RELOAD_COMPLETE"));
            if (ticks == 360 && screen != null) screen.reset = true;
            if (ticks == 400) {
                try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                    image.writeTo(Path.of("palette-proof.png"));
                    System.out.println("P3_VISUAL_CAPTURE rendered=" + (screen == null ? 0 : screen.frames));
                } catch (Exception error) { throw new RuntimeException(error); }
                client.scheduleStop();
            }
        });
    }
    private static void capture(MinecraftClient client, String name) {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(Path.of(name)); }
        catch(Exception error) { throw new RuntimeException(error); }
    }
    private static final class CheckScreen extends Screen {
        final com.google.gson.JsonArray host;
        boolean reset;
        int frames;
        CheckScreen() {
            super(Text.literal("P3 palette/morph proof"));
            try { host = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of("../host-palette.json"))).getAsJsonArray(); }
            catch(Exception e) { throw new RuntimeException("Run host fixture before client",e); }
        }
        @Override public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0xff18212e);
            context.drawTextWithShadow(textRenderer, "P3.1: left rest; right external pose + morph 1", 12, 12, 0xffffff);
            var model = GemRenderModels.get(Identifier.of("agnor_p3", "rig-morph.gltf"));
            if (model == null) return;
            var table = model.layout().nodeTable();
            for (int copy = 0; copy < 2; copy++) {
                var state = table.newScratch();
                var palette = new Matrix4f[model.jointCount()];
                for (int i=0;i<palette.length;i++) palette[i]=new Matrix4f();
                var morph = new float[model.morphs().blockFloats()];
                if (copy == 1) {
                    state[table.weightBase(table.slotOfName("mesh"))] = 1;
                }
                GltfPose.evaluate(model.layout(), state, palette, model.morphs(), morph, new GltfPose.Scratch());
                var hostPose = host.get(reset ? 0 : copy).getAsJsonObject();
                var hostMatrices = hostPose.getAsJsonArray("matrices");
                if (hostMatrices.size() != palette.length) throw new IllegalStateException("Rig slot mismatch");
                for (int i=0;i<palette.length;i++) {
                    var values = hostMatrices.get(i).getAsJsonArray();
                    float[] matrix = new float[16];
                    if (values.size()!=16) throw new IllegalStateException("Matrix shape");
                    for(int j=0;j<16;j++) {
                        matrix[j]=values.get(j).getAsFloat();
                        if(!Float.isFinite(matrix[j])) throw new IllegalStateException("Non-finite pose");
                    }
                    palette[i].set(matrix);
                }
                morph[4] = hostPose.get("weight").getAsFloat();
                if (frames == 0) System.out.println("P3_MORPH_BLOCK copy=" + copy + " " + java.util.Arrays.toString(morph));
                var transform = new Matrix4f().translation(width*(copy==0?.3f:.7f), height*.8f, 0).scale(80,-80,80);
                DirectRenderer.submit(model, palette, morph, transform, 0xf000f0, 0, -1,
                    DirectPass.GUI, VariantUv.NONE, Paint.NONE, 0, null);
            }
            // GUI-only scenes have no Flywheel world pass to upload imported morph deltas.
            com.wf.gemrender.render.MorphBuffer.getInstance().uploadAndBind();
            DirectRenderer.flush(DirectPass.GUI);
            frames++;
        }
        @Override public boolean shouldPause() { return false; }
    }
}
