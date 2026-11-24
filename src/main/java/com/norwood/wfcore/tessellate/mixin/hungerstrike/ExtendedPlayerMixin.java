package com.norwood.wfcore.tessellate.mixin.hungerstrike;

import com.norwood.wfcore.tessellate.hungerstrike.StartHungerView;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/** Static shared map => per-player slot. Constructor: {@code containsKey} true => the {@code put(player, 0)} reset is skipped. */
@Mixin(targets = "com.texelsaurus.minecraft.hungerstrike.ExtendedPlayer", remap = false)
public abstract class ExtendedPlayerMixin {

    @Redirect(method = {"<init>", "tickStart", "tickEnd"}, at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lcom/texelsaurus/minecraft/hungerstrike/ExtendedPlayer;startHungerMap:Ljava/util/Map;"))
    private static Map<Player, Integer> wfcore$perPlayerStartHunger() {
        return StartHungerView.INSTANCE;
    }
}
