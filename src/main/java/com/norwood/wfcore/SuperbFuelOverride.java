package com.norwood.wfcore;

import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

public class SuperbFuelOverride {
    //ID -> data
   public final static Map<String, OverrideData > overrideDataMap = new HashMap<>();
   public record OverrideData(
           int MaxFuel,
           Map<Fluid,Float> fluidConsumptionMap

   ){}
    static {

    }


}
