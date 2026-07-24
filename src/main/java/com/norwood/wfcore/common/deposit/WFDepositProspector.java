package com.norwood.wfcore.common.deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import com.norwood.wfcore.common.machine.DepositBlockEntity;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Map;


public final class WFDepositProspector {

    // The generic key GregTech's scan emits for the deposit block itself
    private static final String DEPOSIT_BLOCK_KEY = "wfcore:deposit";

    private WFDepositProspector() {}

    public static void labelDeposits(String[][][] storage, LevelChunk chunk) {
        int beCount = chunk.getBlockEntities().size();
        int labeled = 0;
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (!(entry.getValue() instanceof DepositBlockEntity deposit)) {
                continue;
            }
            DepositType type = deposit.getDepositType();
            if (type == null) {
                continue;
            }
            String material = type.prospectorMaterial();
            if (material == null || material.isEmpty()) {
                continue;
            }

            labeled++;
            BlockPos pos = entry.getKey();
            int localX = pos.getX() & 15;
            int localZ = pos.getZ() & 15;
            String key = "material_" + material;

            String[] column = storage[localX][localZ];
            boolean present = false;
            if (column != null) {
                for (int i = 0; i < column.length; i++) {
                    if (DEPOSIT_BLOCK_KEY.equals(column[i])) {
                        column[i] = key;
                        present = true;
                    } else if (key.equals(column[i])) {
                        present = true;
                    }
                }
            }
            if (!present) {
                storage[localX][localZ] = ArrayUtils.add(column, key);
            }
        }
        if (labeled > 0) {
            com.norwood.wfcore.WFCore.LOGGER.info("[deposit-prospector] chunk {} labeled {} deposit(s) ({} block entities total)",
                    chunk.getPos(), labeled, beCount);
        }
    }
}
