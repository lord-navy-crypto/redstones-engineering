package dev.redstoneengineering.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import dev.redstoneengineering.ui.menu.OperationsMonitorMenu;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RedstoneEngineering.MOD_ID)
public final class EngineeringUiRegistration {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RedstoneEngineering.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<SignalConditionerMenu>> SIGNAL_CONDITIONER = MENUS.register("signal_conditioner", () -> IMenuTypeExtension.create(SignalConditionerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PidControllerMenu>> PID_CONTROLLER = MENUS.register("pid_controller", () -> IMenuTypeExtension.create(PidControllerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<OscilloscopeMenu>> OSCILLOSCOPE = MENUS.register("oscilloscope", () -> IMenuTypeExtension.create(OscilloscopeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<LogicAnalyzerMenu>> LOGIC_ANALYZER = MENUS.register("logic_analyzer", () -> IMenuTypeExtension.create(LogicAnalyzerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SignalAnalyzerMenu>> SIGNAL_ANALYZER = MENUS.register("signal_analyzer", () -> IMenuTypeExtension.create(SignalAnalyzerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<FieldDeviceMenu>> FIELD_DEVICE = MENUS.register("field_device", () -> IMenuTypeExtension.create(FieldDeviceMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<UniversalFieldDeviceMenu>> UNIVERSAL_FIELD_DEVICE = MENUS.register("universal_field_device", () -> IMenuTypeExtension.create(UniversalFieldDeviceMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<RangeSensorMenu>> RANGE_SENSOR = MENUS.register("range_sensor", () -> IMenuTypeExtension.create(RangeSensorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SignalProcessorMenu>> SIGNAL_PROCESSOR = MENUS.register("signal_processor", () -> IMenuTypeExtension.create(SignalProcessorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<QuartzTimingMenu>> QUARTZ_TIMING = MENUS.register("quartz_timing", () -> IMenuTypeExtension.create(QuartzTimingMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<RadioLinkMenu>> RADIO_LINK = MENUS.register("radio_link", () -> IMenuTypeExtension.create(RadioLinkMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<DigitalCommunicationMenu>> DIGITAL_COMMUNICATION = MENUS.register("digital_communication", () -> IMenuTypeExtension.create(DigitalCommunicationMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PneumaticSystemMenu>> PNEUMATIC_SYSTEM = MENUS.register("pneumatic_system", () -> IMenuTypeExtension.create(PneumaticSystemMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<OpticalSystemMenu>> OPTICAL_SYSTEM = MENUS.register("optical_system", () -> IMenuTypeExtension.create(OpticalSystemMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AmethystSystemMenu>> AMETHYST_SYSTEM = MENUS.register("amethyst_system", () -> IMenuTypeExtension.create(AmethystSystemMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MagneticSystemMenu>> MAGNETIC_SYSTEM = MENUS.register("magnetic_system", () -> IMenuTypeExtension.create(MagneticSystemMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ReliabilitySystemMenu>> RELIABILITY_SYSTEM = MENUS.register("reliability_system", () -> IMenuTypeExtension.create(ReliabilitySystemMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<OperationsMonitorMenu>> OPERATIONS_MONITOR = MENUS.register("operations_monitor", () -> IMenuTypeExtension.create(OperationsMonitorMenu::new));
    public EngineeringUiRegistration(IEventBus modBus) { MENUS.register(modBus); }
}
