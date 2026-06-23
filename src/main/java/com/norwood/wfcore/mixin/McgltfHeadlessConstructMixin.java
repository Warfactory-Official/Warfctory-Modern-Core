package com.norwood.wfcore.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * McGLTF's {@code @Mod} constructor calls {@code Minecraft.getInstance().execute(...)} unconditionally to
 * set up its skinning GL program. In headless environments (datagen, dedicated server) there is no client,
 * so {@code Minecraft.getInstance()} is null and the call NPEs, failing mcgltf construction and aborting the
 * whole run (GeckoLib guards this; mcgltf does not). Skip the call when there is no client — the skinning
 * program is only needed for actual rendering, which never happens without a client.
 */
@Mixin(targets = "com.modularmods.mcgltf.MCglTF", remap = false)
public class McgltfHeadlessConstructMixin {

    @Redirect(method = "<init>",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void wfcore$skipExecuteWhenNoClient(Minecraft minecraft, Runnable task) {
        if (minecraft != null) {
            minecraft.execute(task);
        }
    }
}
