package dev.redstoneengineering;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.SequenceControllerBlock;
import dev.redstoneengineering.gametest.RseEngineeringSystemsGameTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Systems-level extension registry.
 *
 * <p>The historical 122-block core remains registered by {@link RedstoneEngineering}.
 * These three blocks use a separate mod entrypoint with explicit IEventBus listeners,
 * matching the repository's warning-free event-registration policy while keeping the
 * systems extension independently auditable.</p>
 */
@Mod(RedstoneEngineering.MOD_ID)
public final class EngineeringSystemsModule {
    public static final int SYSTEM_BLOCK_COUNT = 3;

    public static final MapCodec<SequenceControllerBlock> SEQUENCE_CONTROLLER_CODEC =
            BlockBehaviour.simpleCodec(SequenceControllerBlock::new);
    public static final MapCodec<SafetyInterlockBlock> SAFETY_INTERLOCK_CODEC =
            BlockBehaviour.simpleCodec(SafetyInterlockBlock::new);
    public static final MapCodec<FaultInjectorBlock> FAULT_INJECTOR_CODEC =
            BlockBehaviour.simpleCodec(FaultInjectorBlock::new);

    public static final SequenceControllerBlock SEQUENCE_CONTROLLER =
            new SequenceControllerBlock(machineProps(MapColor.COLOR_PURPLE));
    public static final SafetyInterlockBlock SAFETY_INTERLOCK =
            new SafetyInterlockBlock(machineProps(MapColor.COLOR_RED));
    public static final FaultInjectorBlock FAULT_INJECTOR =
            new FaultInjectorBlock(machineProps(MapColor.COLOR_ORANGE));

    public static final BlockItem SEQUENCE_CONTROLLER_ITEM =
            new BlockItem(SEQUENCE_CONTROLLER, new Item.Properties());
    public static final BlockItem SAFETY_INTERLOCK_ITEM =
            new BlockItem(SAFETY_INTERLOCK, new Item.Properties());
    public static final BlockItem FAULT_INJECTOR_ITEM =
            new BlockItem(FAULT_INJECTOR, new Item.Properties());

    public EngineeringSystemsModule(IEventBus modBus) {
        modBus.addListener(EngineeringSystemsModule::register);
        modBus.addListener(EngineeringSystemsModule::addCreativeTabItems);
        modBus.addListener(EngineeringSystemsModule::registerGameTests);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, path);
    }

    private static BlockBehaviour.Properties machineProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F);
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_TYPE, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER_CODEC);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK_CODEC);
            helper.register(id("fault_injector"), FAULT_INJECTOR_CODEC);
        });
        event.register(Registries.BLOCK, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK);
            helper.register(id("fault_injector"), FAULT_INJECTOR);
        });
        event.register(Registries.ITEM, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER_ITEM);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK_ITEM);
            helper.register(id("fault_injector"), FAULT_INJECTOR_ITEM);
        });
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(id("rse"))) return;
        event.accept(SEQUENCE_CONTROLLER_ITEM);
        event.accept(SAFETY_INTERLOCK_ITEM);
        event.accept(FAULT_INJECTOR_ITEM);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseEngineeringSystemsGameTests.class);
    }

    public static String summary() {
        return Component.literal("Engineering Systems: Sequence / Interlock / Fault Injection").getString();
    }
}
