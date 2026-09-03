package dev.redstoneengineering.integration;

import net.neoforged.fml.ModList;

/**
 * Centralized runtime detection for optional ecosystem integrations.
 *
 * <p>RSE must never require these mods to start. Compatibility modules may
 * query this class before exposing JEI/Jade-specific behavior, while the core
 * engineering simulation remains NeoForge-only.</p>
 */
public final class IntegrationStatus {
    public static final String JEI_MOD_ID = "jei";
    public static final String JADE_MOD_ID = "jade";

    private IntegrationStatus() {}

    public static boolean isJeiLoaded() {
        return ModList.get().isLoaded(JEI_MOD_ID);
    }

    public static boolean isJadeLoaded() {
        return ModList.get().isLoaded(JADE_MOD_ID);
    }

    public static String summary() {
        return "optionalIntegrations{jei=" + status(isJeiLoaded())
                + ", jade=" + status(isJadeLoaded()) + "}";
    }

    private static String status(boolean loaded) {
        return loaded ? "AVAILABLE" : "ABSENT";
    }
}
