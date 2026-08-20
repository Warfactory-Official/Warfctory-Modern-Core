package com.norwood.wfcore.common.ballistics;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class SolidityDecoder {

    private static final int SECTION_VOLUME = 16 * 16 * 16;

    private SolidityDecoder() {}

    public static Int2ObjectMap<SectionSolidity> decodeChunk(CompoundTag chunk, HolderGetter<Block> lookup) {
        Int2ObjectMap<SectionSolidity> out = new Int2ObjectOpenHashMap<>();
        if (chunk == null) {
            return out;
        }
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        if (sections.isEmpty()) {
            return out;
        }

        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompound(s);
            if (!section.contains("Y", Tag.TAG_BYTE) && !section.contains("Y", Tag.TAG_INT)) {
                continue;
            }
            int sectionY = section.getInt("Y");

            if (!section.contains("block_states", Tag.TAG_COMPOUND)) {
                out.put(sectionY, SectionSolidity.AIR);
                continue;
            }
            CompoundTag blockStates = section.getCompound("block_states");
            ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) {
                out.put(sectionY, SectionSolidity.AIR);
                continue;
            }

            boolean[] paletteSolid = new boolean[palette.size()];
            boolean anySolid = false;
            for (int p = 0; p < palette.size(); p++) {
                boolean solid = isSolidState(palette.getCompound(p), lookup);
                paletteSolid[p] = solid;
                anySolid |= solid;
            }

            if (palette.size() == 1) {
                out.put(sectionY, paletteSolid[0] ? SectionSolidity.SOLID : SectionSolidity.AIR);
                continue;
            }
            if (!anySolid) {
                out.put(sectionY, SectionSolidity.AIR);
                continue;
            }

            long[] data = blockStates.getLongArray("data");
            if (data.length == 0) {

                out.put(sectionY, SectionSolidity.AIR);
                continue;
            }

            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int entriesPerLong = 64 / bits;
            long mask = (1L << bits) - 1;

            long[] solidBits = new long[64];
            for (int idx = 0; idx < SECTION_VOLUME; idx++) {
                int longIndex = idx / entriesPerLong;
                if (longIndex >= data.length) {
                    break;
                }
                int offset = (idx % entriesPerLong) * bits;
                int palIdx = (int) ((data[longIndex] >>> offset) & mask);
                if (palIdx >= 0 && palIdx < paletteSolid.length && paletteSolid[palIdx]) {
                    solidBits[idx >> 6] |= (1L << (idx & 63));
                }
            }
            out.put(sectionY, SectionSolidity.ofBits(solidBits));
        }

        return out;
    }

    private static boolean isSolidState(CompoundTag paletteEntry, HolderGetter<Block> lookup) {

        BlockState state = NbtUtils.readBlockState(lookup, paletteEntry);
        return state != null && !state.isAir() && state.blocksMotion();
    }
}
