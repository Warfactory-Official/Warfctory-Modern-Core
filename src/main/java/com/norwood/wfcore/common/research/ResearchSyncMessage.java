package com.norwood.wfcore.common.research;

import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchCategory;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchInput;
import com.norwood.wfcore.api.research.ResearchRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public record ResearchSyncMessage(List<ResearchCategory> categories, List<Research> researches) {

    /** Captures the current server-side registry contents (registration order preserved). */
    public static ResearchSyncMessage snapshot() {
        return new ResearchSyncMessage(
                new ArrayList<>(ResearchCategoryRegistry.all()),
                new ArrayList<>(ResearchRegistry.all()));
    }

    public static void write(ResearchSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.categories.size());
        for (ResearchCategory category : msg.categories) writeCategory(category, buf);
        buf.writeVarInt(msg.researches.size());
        for (Research research : msg.researches) writeResearch(research, buf);
    }

    public static ResearchSyncMessage read(FriendlyByteBuf buf) {
        int categoryCount = buf.readVarInt();
        List<ResearchCategory> categories = new ArrayList<>(categoryCount);
        for (int i = 0; i < categoryCount; i++) categories.add(readCategory(buf));
        int researchCount = buf.readVarInt();
        List<Research> researches = new ArrayList<>(researchCount);
        for (int i = 0; i < researchCount; i++) researches.add(readResearch(buf));
        return new ResearchSyncMessage(categories, researches);
    }

    public static void handle(ResearchSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            Connection connection = context.getNetworkManager();
            if (connection != null && connection.isMemoryConnection()) {
                return;
            }

            if (!msg.researches.isEmpty()) {
                for (Research research : msg.researches) ResearchRegistry.register(research);
                Set<String> live = new HashSet<>();
                for (Research research : msg.researches) live.add(research.getId());
                for (Research existing : new ArrayList<>(ResearchRegistry.all())) {
                    if (!live.contains(existing.getId())) ResearchRegistry.unregister(existing.getId());
                }
            }
            if (!msg.categories.isEmpty()) {
                for (ResearchCategory category : msg.categories) ResearchCategoryRegistry.register(category);
                Set<String> live = new HashSet<>();
                for (ResearchCategory category : msg.categories) live.add(category.getId());
                for (ResearchCategory existing : new ArrayList<>(ResearchCategoryRegistry.all())) {
                    if (!live.contains(existing.getId())) ResearchCategoryRegistry.unregister(existing.getId());
                }
            }
        });
        context.setPacketHandled(true);
    }

    //////////////////// category ////////////////////

    private static void writeCategory(ResearchCategory category, FriendlyByteBuf buf) {
        buf.writeUtf(category.getId());
        buf.writeUtf(category.getNameKey());
        buf.writeItem(category.getIcon());
        ResourceLocation texture = category.getBackgroundTexture();
        buf.writeBoolean(texture != null);
        if (texture != null) buf.writeResourceLocation(texture);
        buf.writeInt(category.getBackgroundColor());
        buf.writeInt(category.getConnectorColor());
    }

    private static ResearchCategory readCategory(FriendlyByteBuf buf) {
        ResearchCategory.Builder builder = ResearchCategory.builder(buf.readUtf())
                .name(buf.readUtf())
                .icon(buf.readItem());
        if (buf.readBoolean()) builder.background(buf.readResourceLocation());
        builder.backgroundColor(buf.readInt());
        builder.connectorColor(buf.readInt());
        return builder.build();
    }

    //////////////////// research ////////////////////

    private static void writeResearch(Research research, FriendlyByteBuf buf) {
        buf.writeUtf(research.getId());
        buf.writeUtf(research.getNameKey());
        buf.writeUtf(research.getDescKey());
        buf.writeItem(research.getIcon());
        buf.writeBoolean(research.hasBlueprint());
        buf.writeUtf(research.getCategory());
        buf.writeInt(research.getGridX());
        buf.writeInt(research.getGridY());
        buf.writeBoolean(research.hasManualPos());
        buf.writeInt(research.getNodeColor());

        List<String> prerequisites = research.getPrerequisites();
        buf.writeVarInt(prerequisites.size());
        for (String id : prerequisites) buf.writeUtf(id);

        List<List<String>> groups = research.getAnyOfGroups();
        buf.writeVarInt(groups.size());
        for (List<String> group : groups) {
            buf.writeVarInt(group.size());
            for (String id : group) buf.writeUtf(id);
        }

        buf.writeVarInt(research.getRunsRequired());
        buf.writeLong(research.getCwuPerRun());
        buf.writeLong(research.getEut());
        buf.writeVarInt(research.getTicksPerRun());

        List<ResearchInput> inputs = research.getItemInputs();
        buf.writeVarInt(inputs.size());
        for (ResearchInput input : inputs) input.writeToNetwork(buf);

        List<FluidStack> fluids = research.getFluidsPerRun();
        buf.writeVarInt(fluids.size());
        for (FluidStack fluid : fluids) fluid.writeToPacket(buf);

        List<ItemStack> unlocks = research.getUnlockedItems();
        buf.writeVarInt(unlocks.size());
        for (ItemStack unlock : unlocks) buf.writeItem(unlock);
    }

    private static Research readResearch(FriendlyByteBuf buf) {
        Research.Builder builder = Research.builder(buf.readUtf())
                .name(buf.readUtf())
                .description(buf.readUtf())
                .icon(buf.readItem());
        if (buf.readBoolean()) builder.blueprint();
        builder.category(buf.readUtf());
        int gridX = buf.readInt();
        int gridY = buf.readInt();
        if (buf.readBoolean()) builder.pos(gridX, gridY);
        builder.nodeColor(buf.readInt());

        int prereqCount = buf.readVarInt();
        for (int i = 0; i < prereqCount; i++) builder.requires(buf.readUtf());

        int groupCount = buf.readVarInt();
        for (int i = 0; i < groupCount; i++) {
            int size = buf.readVarInt();
            String[] group = new String[size];
            for (int j = 0; j < size; j++) group[j] = buf.readUtf();
            builder.anyOf(group);
        }

        builder.runs(buf.readVarInt())
                .cwuPerRun(buf.readLong())
                .eut(buf.readLong())
                .ticksPerRun(buf.readVarInt());

        int inputCount = buf.readVarInt();
        for (int i = 0; i < inputCount; i++) builder.input(ResearchInput.fromNetwork(buf));

        int fluidCount = buf.readVarInt();
        for (int i = 0; i < fluidCount; i++) builder.fluidPerRun(FluidStack.readFromPacket(buf));

        int unlockCount = buf.readVarInt();
        for (int i = 0; i < unlockCount; i++) builder.unlock(buf.readItem());

        return builder.build();
    }
}
