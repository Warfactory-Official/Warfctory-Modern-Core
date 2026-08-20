package com.norwood.wfcore.client.render.item;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.SlotModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.common.item.PackagedVehicleItem;
import org.jetbrains.annotations.NotNull;
import org.ywzj.vehicle.client.resource.ClientAssetsManager;

/**
 * Draws a packaged vehicle as the Limitless "slot" card for the vehicle it encodes, so a factory's output
 * is identifiable in an inventory. Modelled on Limitless's own {@code VehicleSpawnItemRenderer}; the vehicle
 * id comes from our data component rather than its {@code CUSTOM_DATA} tag.
 */
public class PackagedVehicleRenderer extends BlockEntityWithoutLevelRenderer {

    private static final SlotModel MODEL = new SlotModel();

    public PackagedVehicleRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context,
                             @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        ResourceLocation vehicleId = PackagedVehicleItem.getVehicleId(stack);
        ResourceLocation texture = vehicleId == null ? null : ClientAssetsManager.INSTANCE
                .getVehicleDisplay(vehicleId)
                .map(display -> display.getSlotTexture())
                .orElse(null);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (texture != null && context != ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.YN.rotationDegrees(-45f));
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(
                texture != null ? texture : MissingTextureAtlasSprite.getLocation()));
        MODEL.renderToBuffer(poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
