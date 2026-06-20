package com.norwood.wfcore.common.machine.compute;

import com.norwood.wfcore.common.compute.CPURegistry;
import org.jetbrains.annotations.Nullable;

public interface ICpuSlot {

    @Nullable
    CPURegistry.CPUEntry getStats();
}
