package com.norwood.wfcore.mixin;

import com.atsuishio.superbwarfare.item.ammo.AmmoBoxItem;
import com.atsuishio.superbwarfare.item.ammo.AmmoSupplierItem;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables Superb Warfare's right-click "resupply" that tops up the per-player ammo capability pool
 * ({@code PlayerVariable.ammo}).
 *
 * <p>Two item families feed that pool on use:
 * <ul>
 *   <li>{@link AmmoSupplierItem} — the base ammo items ({@code handgun_ammo} … {@code heavy_ammo}) and,
 *       via subclasses that inherit this {@code use}, the {@code *_ammo_box} variants. Right-click adds
 *       {@code ammoToAdd} to the pool.</li>
 *   <li>{@link AmmoBoxItem} — the configurable {@code ammo_box}. Right-click deposits/withdraws pool ammo.</li>
 * </ul>
 *
 * <p>WFCore reroutes guns to consume ammo <em>items</em> straight from the inventory
 * ({@code AmmoConsumerToItemMixin} flips {@code PLAYER_AMMO} → {@code ITEM}), so the pool is orphaned and
 * refilling it in the field is a logistics bypass. Cancelling {@code use} makes the right-click a no-op.
 * Gun feeding is unaffected: it consumes the ammo items via {@code InventoryTool.consumeItem}, never through
 * {@code use()}.
 */
@Mixin({AmmoSupplierItem.class, AmmoBoxItem.class})
public abstract class AmmoResupplyDisableMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void wfcore$blockPlayerAmmoResupply(Level level, Player player, InteractionHand hand,
                                                CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(hand)));
    }
}
