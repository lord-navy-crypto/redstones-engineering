package dev.redstoneengineering;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.SequenceControllerBlock;
import dev.redstoneengineering.gametest.RseEngineeringSystemsGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.gametest.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Systems-level extension registry.
 *
 * <p>The historical 122-block core remains registered by {@link RedstoneEngineering}.
 * These three blocks are registered through NeoForge RegisterEvent so the legacy audit
 * stays stable while the systems closure gate explicitly audits the 122 + 3 aggregate.</p>
 */
@EventBusSubscriber(modid = RedstoneEngineering.MOD_ID)
public final class EngineeringSystemsModule {
    public static final int SYSTEM_BLOCK_COUNT = 3;

    public static final MapCodec<SequenceControllerBlock> SEQUENCE_CONTROLLER_CODEC =
            BlockBehaviour.simpleCodec(SequenceControllerBlock::new);
    public static final MapCodec<SafetyInterlockBlock> SAFETY_INTERLOCK_CODEC =
            BlockBehaviour.simpleCodec(SafetyInterlockBlock::new);
    public static final MapCodec<FaultInjectorBlock> FAULT_INJECTOR_CODEC =
            BlockBehaviour.simpleCodec(FaultInjectorBlock::new);

    public static final SequenceControllerBlock SEQUENCE_CONTROLLER = new SequenceControllerBlock(machineProps(MapColor.COLOR_PURPLE));
    public static final SafetyInterlockBlock SAFETY_INTERLOCK = new SafetyInterlockBlock(machineProps(MapColor.COLOR_RED));
    public static final FaultInjectorBlock FAULT_INJECTOR = new FaultInjectorBlock(machineProps(MapColor.COLOR_ORANGE));

    public static final BlockItem SEQUENCE_CONTROLLER_ITEM = new BlockItem(SEQUENCE_CONTROLLER, new Item.Properties());
    public static final BlockItem SAFETY_INTERLOCK_ITEM = new BlockItem(SAFETY_INTERLOCK, new Item.Properties());
    public static final BlockItem FAULT_INJECTOR_ITEM = new BlockItem(FAULT_INJECTOR, new Item.Properties());

    private EngineeringSystemsModule() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, path);
    }

    private static BlockBehaviour.Properties machineProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.BLOCK_TYPE, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER_CODEC);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK_CODEC);
            helper.register(id("fault_injector"), FAULT_INJECTOR_CODEC);
        });
        event.register(BuiltInRegistries.BLOCK, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK);
            helper.register(id("fault_injector"), FAULT_INJECTOR);
        });
        event.register(BuiltInRegistries.ITEM, helper -> {
            helper.register(id("sequence_controller"), SEQUENCE_CONTROLLER_ITEM);
            helper.register(id("safety_interlock"), SAFETY_INTERLOCK_ITEM);
            helper.register(id("fault_injector"), FAULT_INJECTOR_ITEM);
        });
    }

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(id("rse"))) return;
        event.accept(SEQUENCE_CONTROLLER_ITEM);
        event.accept(SAFETY_INTERLOCK_ITEM);
        event.accept(FAULT_INJECTOR_ITEM);
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RseEngineeringSystemsGameTests.class);
    }

    public static String summary() {
        return Component.literal("Engineering Systems: Sequence / Interlock / Fault Injection").getString();
    }
}
