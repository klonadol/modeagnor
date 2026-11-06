package dev.agnor.check;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
public final class P32Client implements ClientModInitializer {
    int ticks;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.player==null)return;
            ticks++;
            client.options.hudHidden=true;
            if(ticks==220 || ticks==420) {
                try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                    image.writeTo(Path.of(System.getProperty("agnor.p32.root"),"npc-"+ticks+".png"));
                    System.out.println("P32_CAPTURE "+ticks);
                }catch(Exception e){throw new RuntimeException(e);}
            }
            if(ticks==260)client.reloadResources();
            if(ticks==460) {
                try{Files.writeString(Path.of(System.getProperty("agnor.p32.root"),"client-done"),"done");}catch(Exception e){throw new RuntimeException(e);}
                client.scheduleStop();
            }
        });
    }
}
