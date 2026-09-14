package dev.redstoneengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.robotics.RobotOperatingState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Low-complexity first AMR renderer. Geometry is deliberately block-derived so
 * robotics runtime can mature before RSE commits to a bespoke animated model.
 */
public final class EngineeringMobileRobotRenderer extends EntityRenderer<EngineeringMobileRobotEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public EngineeringMobileRobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.48F;
    }

    @Override
    public void render(
            EngineeringMobileRobotEntity robot,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // Main chassis: synchronized robot state selects only the visual material.
        poseStack.pushPose();
        poseStack.translate(-0.45D, 0.05D, -0.45D);
        poseStack.scale(0.90F, 0.48F, 0.90F);
        blockRenderer.renderSingleBlock(chassisState(robot.robotState()), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // Raised front sensor bar makes heading readable without opening an HMI.
        poseStack.pushPose();
        poseStack.translate(-0.30D, 0.47D, -0.43D);
        poseStack.scale(0.60F, 0.16F, 0.18F);
        blockRenderer.renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
        super.render(robot, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static BlockState chassisState(RobotOperatingState state) {
        return switch (state) {
            case FAULT, SAFE_STOP -> Blocks.REDSTONE_BLOCK.defaultBlockState();
            case WAITING, REPLANNING, DEGRADED -> Blocks.EXPOSED_COPPER.defaultBlockState();
            case COMPLETE, IDLE -> Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
            default -> Blocks.COPPER_BLOCK.defaultBlockState();
        };
    }

    @Override
    public ResourceLocation getTextureLocation(EngineeringMobileRobotEntity robot) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
