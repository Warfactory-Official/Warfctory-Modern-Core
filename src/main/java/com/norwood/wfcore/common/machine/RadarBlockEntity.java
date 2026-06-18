package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Custom block entity for the radar so the controller can be rendered with GeckoLib (GTCEu has no
 * GeckoLib integration of its own). The animation follows the machine's scanning state; the model,
 * texture and animation assets are authored by hand (see {@code RadarGeoModel}).
 */
public class RadarBlockEntity extends MetaMachineBlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", state -> {
            boolean scanning = getMetaMachine() instanceof RadarMachine radar && radar.isScanning();
            state.getController().setAnimation(scanning ? RUNNING : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
