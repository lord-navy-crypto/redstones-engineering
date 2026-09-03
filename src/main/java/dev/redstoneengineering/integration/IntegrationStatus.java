package dev.redstoneengineering.integration;

import net.neoforged.fml.ModList;

/**
 * Centralized runtime diagnostics for the required RSE ecosystem platform.
 *
 * <p>Alpha 1.0.9 promotes JEI, Jade, GeckoLib, Cloth Config and Fusion from
 * optional integrations into explicit RSE runtime requirements. NeoForge will
 * reject startup when a required dependency is missing on the side where that
 * dependency is declared.</p>
 */
public final class IntegrationStatus {
    public static final String JEI_MOD_ID = "jei";
    public static final String JADE_MOD_ID = "jade";
    public static final String GECKOLIB_MOD_ID = "geckolib";
    public static final String CLOTH_CONFIG_MOD_ID = "cloth_config";
    public static final String FUSION_MOD_ID = "fusion";

    private IntegrationStatus() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isJeiLoaded() {
        return isLoaded(JEI_MOD_ID);
    }

    public static boolean isJadeLoaded() {
        return isLoaded(JADE_MOD_ID);
    }

    public static boolean isGeckoLibLoaded() {
        return isLoaded(GECKOLIB_MOD_ID);
    }

    public static boolean isClothConfigLoaded() {
        return isLoaded(CLOTH_CONFIG_MOD_ID);
    }

    public static boolean isFusionLoaded() {
        return isLoaded(FUSION_MOD_ID);
    }

    public static String summary() {
        return "requiredPlatform{jei=" + status(isJeiLoaded())
                + ", jade=" + status(isJadeLoaded())
                + ", geckolib=" + status(isGeckoLibLoaded())
                + ", cloth_config=" + status(isClothConfigLoaded())
                + ", fusion=" + status(isFusionLoaded()) + "}";
    }

    private static String status(boolean loaded) {
        return loaded ? "LOADED" : "MISSING";
    }
}
