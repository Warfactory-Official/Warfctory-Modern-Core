package com.norwood.wfcore.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Casing with a {@link #BOLTED} state. The radar structure requires the bolted variant; the
 * {@link com.norwood.wfcore.common.item.BoltToolItem bolt tool} converts the unbolted casing into the
 * bolted one (consuming bolts). This replaces the 1.12.2 HBM {@code IToolable}/boltgun mechanic with
 * a native, render-free equivalent.
 */
public class BoltableCasingBlock extends Block {

    public static final BooleanProperty BOLTED = BooleanProperty.create("bolted");

    public BoltableCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BOLTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOLTED);
    }
}
