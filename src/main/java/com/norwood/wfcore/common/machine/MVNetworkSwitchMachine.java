package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine;

/**
 * MV-tier variant of GregTech's Network Switch.
 */
public class MVNetworkSwitchMachine extends NetworkSwitchMachine {

    public MVNetworkSwitchMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public int getEnergyUsage() {
        return super.getEnergyUsage() / (GTValues.VA[GTValues.IV] / GTValues.VA[GTValues.MV]);
    }
}
