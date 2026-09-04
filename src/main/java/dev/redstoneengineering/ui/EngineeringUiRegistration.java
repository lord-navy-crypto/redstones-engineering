package dev.redstoneengineering.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Common-side menu registry for the RSE Engineering Interface System.
 *
 * Menus transport bounded configuration intent and read-only snapshots. They do not own
 * simulation, topology, sampling cadence, controller state, or engineering physics.
 */
@Mod(RedstoneEngineering.MOD_ID)
public final class EngineeringUiRegistration {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RedstoneEngineering.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SignalConditionerMenu>> SIGNAL_CONDITIONER =
            MENUS.register("signal_conditioner", () -> IMenuTypeExtension.create(SignalConditionerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PidControllerMenu>> PID_CONTROLLER =
            MENUS.register("pid_controller", () -> IMenuTypeExtension.create(PidControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<OscilloscopeMenu>> OSCILLOSCOPE =
            MENUS.register("oscilloscope", () -> IMenuTypeExtension.create(OscilloscopeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LogicAnalyzerMenu>> LOGIC_ANALYZER =
            MENUS.register("logic_analyzer", () -> IMenuTypeExtension.create(LogicAnalyzerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SignalAnalyzerMenu>> SIGNAL_ANALYZER =
            MENUS.register("signal_analyzer", () -> IMenuTypeExtension.create(SignalAnalyzerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FieldDeviceMenu>> FIELD_DEVICE =
            MENUS.register("field_device", () -> IMenuTypeExtension.create(FieldDeviceMenu::new));

    public EngineeringUiRegistration(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
