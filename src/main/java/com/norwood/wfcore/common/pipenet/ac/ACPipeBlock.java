package com.norwood.wfcore.common.pipenet.ac;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.client.model.pipe.ActivablePipeModel;
import com.gregtechceu.gtceu.client.model.pipe.PipeModel;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.norwood.wfcore.common.capability.WFCapabilities;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.pipenet.ac.net.LevelACPipeNet;
import org.jetbrains.annotations.Nullable;

/** AC cable block (one per {@link ACPipeType} thickness). Steel-wire base carries 512 EU/t at SINGLE. */
public class ACPipeBlock extends PipeBlock<ACPipeType, ACPipeProperties, LevelACPipeNet> {

    private static final long STEEL_BASE_THROUGHPUT = 512L;

    private final ACPipeProperties baseProperties;

    public ACPipeBlock(Properties properties, ACPipeType type) {
        super(properties, type);
        this.baseProperties = new ACPipeProperties(STEEL_BASE_THROUGHPUT);
        registerDefaultState(defaultBlockState().setValue(GTBlockStateProperties.ACTIVE, false));
    }

    @OnlyIn(Dist.CLIENT)
    public static BlockColor tintedColor() {
        return (state, level, pos, index) -> {
            if (pos != null && level != null &&
                    level.getBlockEntity(pos) instanceof PipeBlockEntity<?, ?> pipe) {
                if (!pipe.getFrameMaterial().isNull()) {
                    if (index == 3) {
                        return pipe.getFrameMaterial().getMaterialRGB();
                    } else if (index == 4) {
                        return pipe.getFrameMaterial().getMaterialSecondaryRGB();
                    }
                }
                if (pipe.isPainted()) {
                    return pipe.getRealColor();
                }
            }
            return -1;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GTBlockStateProperties.ACTIVE);
    }

    @Override
    public LevelACPipeNet getWorldPipeNet(ServerLevel world) {
        return LevelACPipeNet.getOrCreate(world);
    }

    @Override
    public BlockEntityType<? extends PipeBlockEntity<ACPipeType, ACPipeProperties>> getBlockEntityType() {
        return WFBlocks.AC_PIPE_BE.get();
    }

    @Override
    public ACPipeProperties createRawData(BlockState pState, @Nullable ItemStack pStack) {
        return this.pipeType.modifyProperties(baseProperties);
    }

    @Override
    public ACPipeProperties createProperties(IPipeNode<ACPipeType, ACPipeProperties> pipeTile) {
        ACPipeType type = pipeTile.getPipeType();
        if (type == null) return getFallbackType();
        return this.pipeType.modifyProperties(baseProperties);
    }

    @Override
    public ACPipeProperties getFallbackType() {
        return baseProperties;
    }

    @Override
    public PipeModel createPipeModel(GTBlockstateProvider provider) {
        ActivablePipeModel model = new ActivablePipeModel(this, this.pipeType.getThickness(),
                GTCEu.id("block/pipe/pipe_laser_side"), GTCEu.id("block/pipe/pipe_laser_in"), provider);
        model.setSideOverlay(GTCEu.id("block/pipe/pipe_laser_side_overlay"));
        model.setSideOverlayActive(GTCEu.id("block/pipe/pipe_laser_side_overlay_emissive"));
        return model;
    }

    @Override
    public boolean canPipesConnect(IPipeNode<ACPipeType, ACPipeProperties> selfTile, Direction side,
                                   IPipeNode<ACPipeType, ACPipeProperties> sideTile) {
        return selfTile instanceof ACPipeBlockEntity && sideTile instanceof ACPipeBlockEntity;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeNode<ACPipeType, ACPipeProperties> selfTile, Direction side,
                                         @Nullable BlockEntity tile) {
        return tile != null && tile.getCapability(WFCapabilities.CAPABILITY_AC_ENERGY, side.getOpposite()).isPresent();
    }

    @Override
    public GTToolType getPipeTuneTool() {
        return GTToolType.WIRE_CUTTER;
    }
}
