package com.norwood.wfcore.common.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

import com.norwood.wfcore.api.capability.IACEnergyContainer;

/** WFCore-registered Forge capabilities. Mirrors GregTech's {@code GTCapability} registration style. */
public final class WFCapabilities {

    public static final Capability<IACEnergyContainer> CAPABILITY_AC_ENERGY = CapabilityManager
            .get(new CapabilityToken<>() {});

    private WFCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IACEnergyContainer.class);
    }
}
