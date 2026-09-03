package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Registers RSE runtime engineering tests when NeoForge enables the GameTest system. */
@EventBusSubscriber(modid = RedstoneEngineering.MOD_ID, bus = Bus.MOD)
public final class RseGameTestRegistration {
    private RseGameTestRegistration() {}

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseTopologyGameTests.class);
        event.register(RseCopperGameTests.class);
    }
}
