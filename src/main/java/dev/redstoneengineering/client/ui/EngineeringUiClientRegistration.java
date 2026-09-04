package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Physical-client-only screen registration for the shared engineering UI framework. */
@Mod(value = RedstoneEngineering.MOD_ID, dist = Dist.CLIENT)
public final class EngineeringUiClientRegistration {
    public EngineeringUiClientRegistration(IEventBus modBus) {
        modBus.addListener(EngineeringUiClientRegistration::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EngineeringUiRegistration.SIGNAL_CONDITIONER.get(), SignalConditionerScreen::new);
        event.register(EngineeringUiRegistration.PID_CONTROLLER.get(), PidControllerScreen::new);
    }
}
