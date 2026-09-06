package dev.redstoneengineering;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.SequenceControllerBlock;
import dev.redstoneengineering.block.TopologyDebuggerBlock;
import dev.redstoneengineering.gametest.RseEngineeringSystemsGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Systems-level extension registry layered on the historical 122-block core. */
@Mod(RedstoneEngineering.MOD_ID)
public final class EngineeringSystemsModule {
    public static final int SYSTEM_BLOCK_COUNT = 5;

    public static final DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_TYPE, RedstoneEngineering.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RedstoneEngineering.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RedstoneEngineering.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SequenceControllerBlock>> SEQUENCE_CONTROLLER_CODEC =
            codec("sequence_controller", SequenceControllerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SafetyInterlockBlock>> SAFETY_INTERLOCK_CODEC =
            codec("safety_interlock", SafetyInterlockBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<FaultInjectorBlock>> FAULT_INJECTOR_CODEC =
            codec("fault_injector", FaultInjectorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AlarmProcessorBlock>> ALARM_PROCESSOR_CODEC =
            codec("alarm_processor", AlarmProcessorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<TopologyDebuggerBlock>> TOPOLOGY_DEBUGGER_CODEC =
            codec("topology_debugger", TopologyDebuggerBlock::new);

    public static final DeferredBlock<SequenceControllerBlock> SEQUENCE_CONTROLLER =
            BLOCKS.registerBlock("sequence_controller", SequenceControllerBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<SafetyInterlockBlock> SAFETY_INTERLOCK =
            BLOCKS.registerBlock("safety_interlock", SafetyInterlockBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<FaultInjectorBlock> FAULT_INJECTOR =
            BLOCKS.registerBlock("fault_injector", FaultInjectorBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<AlarmProcessorBlock> ALARM_PROCESSOR =
            BLOCKS.registerBlock("alarm_processor", AlarmProcessorBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<TopologyDebuggerBlock> TOPOLOGY_DEBUGGER =
            BLOCKS.registerBlock("topology_debugger", TopologyDebuggerBlock::new, machineProps(MapColor.COLOR_BLUE));

    public static final DeferredItem<BlockItem> SEQUENCE_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem("sequence_controller", SEQUENCE_CONTROLLER);
    public static final DeferredItem<BlockItem> SAFETY_INTERLOCK_ITEM = ITEMS.registerSimpleBlockItem("safety_interlock", SAFETY_INTERLOCK);
    public static final DeferredItem<BlockItem> FAULT_INJECTOR_ITEM = ITEMS.registerSimpleBlockItem("fault_injector", FAULT_INJECTOR);
    public static final DeferredItem<BlockItem> ALARM_PROCESSOR_ITEM = ITEMS.registerSimpleBlockItem("alarm_processor", ALARM_PROCESSOR);
    public static final DeferredItem<BlockItem> TOPOLOGY_DEBUGGER_ITEM = ITEMS.registerSimpleBlockItem("topology_debugger", TOPOLOGY_DEBUGGER);

    public EngineeringSystemsModule(IEventBus modBus) {
        BLOCK_TYPES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(EngineeringSystemsModule::addCreativeTabItems);
        modBus.addListener(EngineeringSystemsModule::registerGameTests);
    }

    private static <T extends Block> DeferredHolder<MapCodec<? extends Block>, MapCodec<T>> codec(
            String name, java.util.function.Function<BlockBehaviour.Properties, T> factory) {
        return BLOCK_TYPES.register(name, () -> BlockBehaviour.simpleCodec(factory));
    }

    private static BlockBehaviour.Properties machineProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, path);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(id("rse"))) return;
        event.accept(SEQUENCE_CONTROLLER_ITEM);
        event.accept(SAFETY_INTERLOCK_ITEM);
        event.accept(FAULT_INJECTOR_ITEM);
        event.accept(ALARM_PROCESSOR_ITEM);
        event.accept(TOPOLOGY_DEBUGGER_ITEM);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseEngineeringSystemsGameTests.class);
    }

    public static String summary() {
        return "Engineering Systems: Sequence / Interlock / Fault / Alarm / Topology Diagnostics";
    }
}
