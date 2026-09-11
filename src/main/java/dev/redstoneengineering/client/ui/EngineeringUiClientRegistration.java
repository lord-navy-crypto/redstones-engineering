package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.client.diagnostics.RseLogCapture;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Physical-client-only registration for engineering screens and the RSE diagnostics console. */
@Mod(value = RedstoneEngineering.MOD_ID, dist = Dist.CLIENT)
public final class EngineeringUiClientRegistration {
    public EngineeringUiClientRegistration(IEventBus modBus) {
        modBus.addListener(EngineeringUiClientRegistration::registerScreens);
        modBus.addListener(EngineeringUiClientRegistration::hideLegacyJunctionVariants);
        NeoForge.EVENT_BUS.addListener(EngineeringUiClientRegistration::addInventoryDiagnosticsButton);
        RseLogCapture.install();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EngineeringUiRegistration.SIGNAL_CONDITIONER.get(), SignalConditionerScreen::new);
        event.register(EngineeringUiRegistration.PID_CONTROLLER.get(), PidControllerScreen::new);
        event.register(EngineeringUiRegistration.OSCILLOSCOPE.get(), OscilloscopeScreen::new);
        event.register(EngineeringUiRegistration.LOGIC_ANALYZER.get(), LogicAnalyzerScreen::new);
        event.register(EngineeringUiRegistration.SIGNAL_ANALYZER.get(), SignalAnalyzerScreen::new);
        event.register(EngineeringUiRegistration.FIELD_DEVICE.get(), EnhancedFieldDeviceScreen::new);
        event.register(EngineeringUiRegistration.UNIVERSAL_FIELD_DEVICE.get(), UniversalFieldDeviceScreen::new);
        event.register(EngineeringUiRegistration.RANGE_SENSOR.get(), RangeSensorScreen::new);
        event.register(EngineeringUiRegistration.SIGNAL_PROCESSOR.get(), SignalProcessorScreen::new);
        event.register(EngineeringUiRegistration.OPERATIONS_MONITOR.get(), OperationsMonitorScreen::new);
    }

    /**
     * Alpha-3 presents one player-facing Junction Point. The historical optical/copper registry
     * entries remain loaded for old-world compatibility, but they are intentionally not offered
     * as new creative content.
     */
    private static void hideLegacyJunctionVariants(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() != RedstoneEngineering.RSE_TAB.get()) return;
        event.remove(
                RedstoneEngineering.OPTICAL_FIBER_JUNCTION_ITEM.get().getDefaultInstance(),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
        );
        event.remove(
                RedstoneEngineering.COPPER_CABLE_JUNCTION_ITEM.get().getDefaultInstance(),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
        );
    }

    private static void addInventoryDiagnosticsButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        int x = Math.min(containerScreen.getGuiLeft() + containerScreen.getXSize() + 4, screen.width - 26);
        int y = Math.max(6, containerScreen.getGuiTop() + 4);
        Button diagnosticsButton = Button.builder(
                Component.literal("✚").withStyle(ChatFormatting.RED),
                button -> Minecraft.getInstance().setScreen(new RseDiagnosticsScreen(screen))
        ).bounds(x, y, 22, 20).build();
        event.addListener(diagnosticsButton);
    }
}
