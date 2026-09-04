package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Registers RSE runtime engineering tests when NeoForge enables the GameTest system. */
@EventBusSubscriber(modid = RedstoneEngineering.MOD_ID)
public final class RseGameTestRegistration {
    private RseGameTestRegistration() {}

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseTopologyGameTests.class);
        event.register(RseCopperGameTests.class);
        event.register(RseMetrologyGameTests.class);
        event.register(RseCommissioningGameTests.class);
        event.register(RseEngineeringUxGameTests.class);
        event.register(RseAcceptanceGameTests.class);
    }
}