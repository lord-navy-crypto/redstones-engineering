package dev.redstoneengineering.integration;

import net.neoforged.fml.ModList;

/**
 * Centralized runtime diagnostics for RSE's actual hard platform dependencies.
 *
 * <p>Jade is used directly by the engineering HUD integration and GeckoLib is
 * used directly by the synchronized mechatronics renderer/block-entity layer.
 * Other ecosystem mods are not treated as required unless RSE gains a real
 * implementation dependency on them.</p>
 */
public final class IntegrationStatus {
    public static final String JADE_MOD_ID = "jade";
    public static final String GECKOLIB_MOD_ID = "geckolib";
    public static final String LDLIB2_MOD_ID = "ldlib2";

    private IntegrationStatus() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isJadeLoaded() {
        return isLoaded(JADE_MOD_ID);
    }

    public static boolean isGeckoLibLoaded() {
        return isLoaded(GECKOLIB_MOD_ID);
    }

    public static boolean isLdLib2Loaded() {
        return isLoaded(LDLIB2_MOD_ID);
    }

    public static String summary() {
        return "requiredPlatform{jade=" + status(isJadeLoaded())
                + ", geckolib=" + status(isGeckoLibLoaded())
                + ", ldlib2=" + status(isLdLib2Loaded()) + "}";
    }

    private static String status(boolean loaded) {
        return loaded ? "LOADED" : "MISSING";
    }
}
