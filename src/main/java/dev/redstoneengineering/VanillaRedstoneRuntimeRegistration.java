package dev.redstoneengineering;

import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneRuntimeTelemetry;
import dev.redstoneengineering.gametest.RseVanillaRedstoneBehaviorGameTests;
import dev.redstoneengineering.gametest.RseVanillaRedstoneRuntimeGameTests;
import dev.redstoneengineering.gametest.RseVanillaRedstoneTimingGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Explicit lifecycle wiring for observer-only Vanilla Redstone runtime/timing/behavior telemetry. */
@Mod(RedstoneEngineering.MOD_ID)
public final class VanillaRedstoneRuntimeRegistration {
    public VanillaRedstoneRuntimeRegistration(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(VanillaRedstoneRuntimeTelemetry::onNeighborNotify);
        modBus.addListener(VanillaRedstoneRuntimeRegistration::registerGameTests);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseVanillaRedstoneRuntimeGameTests.class);
        event.register(RseVanillaRedstoneTimingGameTests.class);
        event.register(RseVanillaRedstoneBehaviorGameTests.class);
    }
}
