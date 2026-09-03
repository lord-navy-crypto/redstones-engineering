package dev.redstoneengineering.client;

import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Shared GeckoLib renderer for Servo, Pneumatic Cylinder and Proportional Valve. */
public final class MechatronicsGeoRenderer extends GeoBlockRenderer<MechatronicsVisualBlockEntity> {
    public MechatronicsGeoRenderer() {
        super(new MechatronicsGeoModel());
    }
}
