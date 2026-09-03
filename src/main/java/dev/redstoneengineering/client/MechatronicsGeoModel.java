package dev.redstoneengineering.client;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model that projects synchronized simulation snapshots onto bones.
 * Bone transforms are display-only: this class has no path back to physics.
 */
public final class MechatronicsGeoModel extends GeoModel<MechatronicsVisualBlockEntity> {
    private static final ResourceLocation SERVO_MODEL = id("geo/block/servo_actuator.geo.json");
    private static final ResourceLocation CYLINDER_MODEL = id("geo/block/pneumatic_cylinder.geo.json");
    private static final ResourceLocation VALVE_MODEL = id("geo/block/pneumatic_proportional_valve.geo.json");
    private static final ResourceLocation ANIMATIONS = id("animations/block/mechatronics.animation.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/iron_block.png");

    @Override
    public ResourceLocation getModelResource(MechatronicsVisualBlockEntity animatable) {
        String path = BuiltInRegistries.BLOCK.getKey(animatable.getBlockState().getBlock()).getPath();
        if (path.equals("servo_actuator")) return SERVO_MODEL;
        if (path.equals("pneumatic_cylinder")) return CYLINDER_MODEL;
        return VALVE_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MechatronicsVisualBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MechatronicsVisualBlockEntity animatable) {
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(
            MechatronicsVisualBlockEntity animatable,
            long instanceId,
            AnimationState<MechatronicsVisualBlockEntity> animationState
    ) {
        MechatronicsVisualState state = animatable.visualState();

        GeoBone shaft = getAnimationProcessor().getBone("shaft");
        if (shaft != null) {
            shaft.setRotY((float) (state.position01() * Math.PI * 1.5));
        }

        GeoBone rod = getAnimationProcessor().getBone("rod");
        if (rod != null) {
            rod.setPosZ((float) (state.position01() * 7.0));
        }

        GeoBone spool = getAnimationProcessor().getBone("spool");
        if (spool != null) {
            spool.setPosX((float) ((state.opening01() - 0.5) * 6.0));
        }

        GeoBone pressureIndicator = getAnimationProcessor().getBone("pressure_indicator");
        if (pressureIndicator != null) {
            pressureIndicator.setScaleY((float) Math.max(0.05, state.pressure01()));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, path);
    }
}
