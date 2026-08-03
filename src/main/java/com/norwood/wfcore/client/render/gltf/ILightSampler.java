package com.norwood.wfcore.client.render.gltf;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface ILightSampler<T extends BlockEntity> {
    int getLightLevel(T be);
}
