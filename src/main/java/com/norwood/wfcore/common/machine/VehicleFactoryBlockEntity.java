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
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

/**
 * GeckoLib block entity for the vehicle factories. Animation follows the machine's working state via a
 * {@link MachineAnimator}: the working loop freezes in place when the recipe stalls for power and only
 * settles to idle once the loop completes. The geo model/texture/animation assets are authored by hand.
 */
public class VehicleFactoryBlockEntity extends MetaMachineBlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WORKING = RawAnimation.begin().thenLoop("working");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MachineAnimator animator = new MachineAnimator();

    public VehicleFactoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", state -> {
            boolean crafting = getMetaMachine() instanceof AbstractVehicleFactoryMachine factory &&
                    factory.isCrafting();
            return animator.handle(state,
                    crafting ? "working" : "idle",
                    crafting ? WORKING : IDLE,
                    crafting ? MachineAnimator.Transition.SNAP : MachineAnimator.Transition.FINISH_LOOP);
        }));
    }

    @Override
    public double getTick(Object entity) {
        boolean advancing = !(getMetaMachine() instanceof AbstractVehicleFactoryMachine factory) ||
                factory.isAnimAdvancing();
        return animator.tick(RenderUtils.getCurrentTick(), advancing);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
