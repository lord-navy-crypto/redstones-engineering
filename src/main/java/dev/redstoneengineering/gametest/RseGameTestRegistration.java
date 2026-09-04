package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Registers RSE runtime engineering tests when NeoForge enables the GameTest system. */
@Mod(RedstoneEngineering.MOD_ID)
public final class RseGameTestRegistration {
    public RseGameTestRegistration(IEventBus modBus) {
        modBus.addListener(RseGameTestRegistration::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseTopologyGameTests.class);
        event.register(RseCopperGameTests.class);
        event.register(RseMetrologyGameTests.class);
        event.register(RseCommissioningGameTests.class);
        event.register(RseEngineeringUxGameTests.class);
        event.register(RseAcceptanceGameTests.class);
        event.register(RseFunctionalCorrectnessGameTests.class);
        event.register(RseEngineeringUiGameTests.class);
        event.register(RseFirstEightAcceptanceGameTests.class);
        event.register(RseSecondEightAcceptanceGameTests.class);
        event.register(RseThirdEightAcceptanceGameTests.class);
    }
}
