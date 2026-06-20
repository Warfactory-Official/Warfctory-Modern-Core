package com.norwood.wfcore.common.pipenet.ac;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.item.PipeBlockItem;

import net.minecraft.client.color.item.ItemColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ACPipeBlockItem extends PipeBlockItem {

    public ACPipeBlockItem(PipeBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public ACPipeBlock getBlock() {
        return (ACPipeBlock) super.getBlock();
    }

    @OnlyIn(Dist.CLIENT)
    public static ItemColor tintColor() {
        return (itemStack, index) -> {
            if (itemStack.getItem() instanceof ACPipeBlockItem materialBlockItem) {
                return ACPipeBlock.tintedColor().getColor(materialBlockItem.getBlock().defaultBlockState(), null,
                        null, index);
            }
            return -1;
        };
    }
}
