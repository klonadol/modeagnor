package dev.agnor.client.mixin;

import net.minecraft.resource.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(ResourcePackManager.class)
public interface ContentProviderAccess {
    @Accessor("providers") Set<ResourcePackProvider> agnor$getProviders();
    @Mutable @Accessor("providers") void agnor$setProviders(Set<ResourcePackProvider> providers);
}
