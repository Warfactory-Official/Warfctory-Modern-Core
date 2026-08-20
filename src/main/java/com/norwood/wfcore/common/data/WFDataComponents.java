package com.norwood.wfcore.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.serialization.Codec;
import com.norwood.wfcore.WFCore;

import java.util.List;
import java.util.UUID;

/**
 * WFCore's item data components.
 *
 * <p>1.20.5 replaced free-form item NBT with typed, registered components, so every place wfcore used to
 * call {@code stack.getOrCreateTag().putX(...)} now needs a declared type. Vanilla concepts that used to be
 * NBT keys (custom name, lore, written-book pages) have their own components and are not redeclared here —
 * only the two genuinely wfcore-specific payloads are.
 */
public final class WFDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
            .createDataComponents(Registries.DATA_COMPONENT_TYPE, WFCore.MOD_ID);

    /**
     * The research blueprint ids written onto a data stick / module / orb / paper. Was the {@code Research}
     * NBT key, stored as either a single string or a list; the component is always a list.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> RESEARCH_BLUEPRINTS =
            DATA_COMPONENTS.registerComponentType("research_blueprints",
                    builder -> builder
                            .persistent(Codec.STRING.listOf())
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    /**
     * The scan id a radar data stick carries, indexing into {@code RadarScanData}. Replaces the
     * {@code TargetUUID} NBT key; the old {@code is_analyzed} flag is redundant — presence of the id
     * <em>is</em> "analyzed", so it is not carried over.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> RADAR_SCAN_ID =
            DATA_COMPONENTS.registerComponentType("radar_scan_id",
                    builder -> builder
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC));

    /**
     * Block positions of the mining charges a detonator is wired to. Was a {@code ListTag} of longs under the
     * {@code Charges} NBT key.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BlockPos>>> DETONATOR_CHARGES =
            DATA_COMPONENTS.registerComponentType("detonator_charges",
                    builder -> builder
                            .persistent(BlockPos.CODEC.listOf())
                            .networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /**
     * The missile factory a data stick has copied, for linking it to a launch silo. Replaces the
     * {@code wfcore.missile_factory_link} compound, which held a {@code Pos} and a {@code Dim} string —
     * exactly a {@link GlobalPos}, so vanilla's codec covers both halves.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> MISSILE_FACTORY_LINK =
            DATA_COMPONENTS.registerComponentType("missile_factory_link",
                    builder -> builder
                            .persistent(GlobalPos.CODEC)
                            .networkSynchronized(GlobalPos.STREAM_CODEC));

    /**
     * The entity a packaged vehicle will spawn as. Replaces the {@code entity} NBT key, which held the
     * entity type's id as a string.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PACKAGED_VEHICLE =
            DATA_COMPONENTS.registerComponentType("packaged_vehicle",
                    builder -> builder
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private WFDataComponents() {}

    /** Convenience for the datagen/lang side, mirroring {@link WFCore#id}. */
    public static ResourceLocation id(String path) {
        return WFCore.id(path);
    }
}
