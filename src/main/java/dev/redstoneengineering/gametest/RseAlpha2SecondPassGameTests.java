package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneRuntimeTelemetry;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTargetHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cross-system destructive checks for the Alpha 2 feature-freeze second pass.
 *
 * <p>The 122-block audit remains the exhaustive per-block registration/resource/port/snapshot
 * matrix. These tests intentionally attack shared runtime failure modes instead of blindly placing
 * every default block state: rapid boundary changes, symmetric cable rebuild, transient-store
 * cleanup, system-chain recovery, and bounded observer-only vanilla telemetry.</p>
 */
public final class RseAlpha2SecondPassGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseAlpha2SecondPassGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void rapidBoundaryToggleConvergesWithoutStaleState(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos conditioner = new BlockPos(1, 1, 2);
        BlockPos indicator = new BlockPos(2, 1, 2);

        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.setBlock(conditioner, conditioner(Direction.EAST, 0, 4));
        helper.setBlock(indicator, indicator(Direction.EAST));

        helper.runAfterDelay(4, () -> {
            assertSignal(helper, conditioner, indicator, 0, "initial LOW did not settle");
            helper.setBlock(source, reference(Direction.EAST, 15));
            helper.runAfterDelay(2, () -> {
                assertSignal(helper, conditioner, indicator, 15, "LOW->HIGH did not converge to saturated 15");
                helper.setBlock(source, reference(Direction.EAST, 0));
                helper.runAfterDelay(2, () -> {
                    assertSignal(helper, conditioner, indicator, 0, "HIGH->LOW retained stale output");
                    helper.setBlock(source, reference(Direction.EAST, 15));
                    helper.runAfterDelay(2, () -> {
                        assertSignal(helper, conditioner, indicator, 15, "second LOW->HIGH transition did not converge");
                        helper.setBlock(source, reference(Direction.EAST, 0));
                        helper.runAfterDelay(2, () -> {
                            assertSignal(helper, conditioner, indicator, 0, "second HIGH->LOW transition retained stale state");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void instrumentCableLinkLifecycleRebuildsSymmetrically(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        helper.setBlock(a, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(b, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockState aState = helper.getBlockState(a);
            BlockState bState = helper.getBlockState(b);
            if (!ConnectedCableBlock.connected(aState, Direction.EAST)
                    || !ConnectedCableBlock.connected(bState, Direction.WEST)) {
                helper.fail("Instrument cable placement did not build a symmetric physical link", a); return;
            }
            if (!(aState.getBlock() instanceof InstrumentCableBlock aCable)
                    || !(bState.getBlock() instanceof InstrumentCableBlock bCable)) {
                helper.fail("Instrument cable registry resolved to the wrong block type", a); return;
            }
            var aSnapshot = aCable.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(a), aState, Direction.EAST);
            var bSnapshot = bCable.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(b), bState, Direction.WEST);
            if (aSnapshot.isEmpty() || bSnapshot.isEmpty()
                    || aSnapshot.get().quality() != PortQuality.VALID
                    || bSnapshot.get().quality() != PortQuality.VALID) {
                helper.fail("Live instrument cable link did not expose VALID read-only snapshots", a); return;
            }

            helper.setBlock(b, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                BlockState detached = helper.getBlockState(a);
                if (ConnectedCableBlock.connected(detached, Direction.EAST)) {
                    helper.fail("Instrument cable retained a ghost connection after neighbor removal", a); return;
                }
                InstrumentCableBlock detachedCable = (InstrumentCableBlock) detached.getBlock();
                if (detachedCable.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(a), detached, Direction.EAST).isPresent()) {
                    helper.fail("Detached instrument face retained a ghost port snapshot", a); return;
                }

                helper.setBlock(b, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
                helper.runAfterDelay(3, () -> {
                    BlockState rebuiltA = helper.getBlockState(a);
                    BlockState rebuiltB = helper.getBlockState(b);
                    if (!ConnectedCableBlock.connected(rebuiltA, Direction.EAST)
                            || !ConnectedCableBlock.connected(rebuiltB, Direction.WEST)) {
                        helper.fail("Instrument cable link did not rebuild symmetrically after replacement", a); return;
                    }
                    InstrumentCableBlock rebuiltCable = (InstrumentCableBlock) rebuiltA.getBlock();
                    var rebuiltSnapshot = rebuiltCable.engineeringSnapshot(
                            helper.getLevel(), helper.absolutePos(a), rebuiltA, Direction.EAST);
                    if (rebuiltSnapshot.isEmpty() || rebuiltSnapshot.get().quality() != PortQuality.VALID) {
                        helper.fail("Rebuilt instrument link did not recover a VALID snapshot", a); return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void faultInjectorRuntimeCleanupSurvivesRemovalAndReinsert(GameTestHelper helper) {
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos arm = new BlockPos(2, 1, 3);
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FaultInjectorBlock.MODE, 1));
        helper.setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(4, () -> {
            BlockPos absolute = helper.absolutePos(injector);
            if (!FaultInjectorBlock.active(helper.getLevel(), absolute)
                    || FaultInjectorBlock.activationCount(helper.getLevel(), absolute) < 1) {
                helper.fail("Fault injector did not create runtime state while armed", injector); return;
            }
            helper.setBlock(injector, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (FaultInjectorBlock.active(helper.getLevel(), absolute)
                        || FaultInjectorBlock.activationCount(helper.getLevel(), absolute) != 0) {
                    helper.fail("Fault injector runtime store survived block removal", injector); return;
                }
                helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                        .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                        .setValue(FaultInjectorBlock.MODE, 1));
                helper.runAfterDelay(4, () -> {
                    if (FaultInjectorBlock.active(helper.getLevel(), absolute)
                            || FaultInjectorBlock.activationCount(helper.getLevel(), absolute) != 0
                            || helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                        helper.fail("Reinserted unarmed fault injector inherited ghost runtime state", injector); return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void faultInjectorToAlarmChainClearsAfterHealthyReset(GameTestHelper helper) {
        BlockPos injector = new BlockPos(1, 1, 2);
        BlockPos alarm = new BlockPos(2, 1, 2);
        BlockPos arm = new BlockPos(1, 1, 3);
        BlockPos reset = new BlockPos(2, 1, 3);

        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FaultInjectorBlock.MODE, 1));
        helper.setBlock(alarm, EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(AlarmProcessorBlock.SEVERITY, 2));
        helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(6, () -> {
            if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || !AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                helper.fail("Fault injector -> alarm chain did not raise the injected fault", alarm); return;
            }
            helper.setBlock(injector, Blocks.AIR.defaultBlockState());
            helper.setBlock(arm, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (!AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                    helper.fail("Alarm lost its latch before explicit healthy reset", alarm); return;
                }
                helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    if (AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))
                            || helper.getBlockState(alarm).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                        helper.fail("Healthy reset did not clear recovered injected-fault chain", alarm); return;
                    }
                    helper.setBlock(reset, Blocks.AIR.defaultBlockState());
                    helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                            .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                            .setValue(FaultInjectorBlock.MODE, 1));
                    helper.runAfterDelay(4, () -> {
                        if (FaultInjectorBlock.active(helper.getLevel(), helper.absolutePos(injector))
                                || helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 0
                                || AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) {
                            helper.fail("Recovered chain re-latched from ghost state after injector replacement", alarm); return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void vanillaOverlayBurstHistoryIsBoundedAndObserverOnly(GameTestHelper helper) {
        BlockPos wire = new BlockPos(2, 1, 2);
        helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteWire = helper.absolutePos(wire);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteWire, 4);
            VanillaRedstoneTargetHistory.clear(level);
            BlockState before = level.getBlockState(absoluteWire);

            for (int i = 0; i < 64; i++) {
                level.updateNeighborsAt(absoluteWire, Blocks.REDSTONE_WIRE);
            }

            var runtime = VanillaRedstoneRuntimeTelemetry.inspect(level, absoluteWire, 4, 20);
            var history = VanillaRedstoneTargetHistory.inspect(level, absoluteWire, 20);
            if (runtime.neighborNotificationEvents() < 64) {
                helper.fail("Burst observer telemetry dropped exact server NeighborNotify observations", wire); return;
            }
            if (history.count() != VanillaRedstoneTargetHistory.DISPLAY_SAMPLES) {
                helper.fail("Exact-target history did not clamp its presentation window to 24 samples", wire); return;
            }
            if (history.values()[VanillaRedstoneTargetHistory.DISPLAY_SAMPLES - 1] != 0) {
                helper.fail("Burst history no longer preserves literal unpowered dust value 0", wire); return;
            }
            if (!level.getBlockState(absoluteWire).equals(before)) {
                helper.fail("Burst VEO observation mutated vanilla redstone state", wire); return;
            }
            helper.succeed();
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState conditioner(Direction facing, int mode, int param) {
        return RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, facing)
                .setValue(SignalConditionerBlock.MODE, mode)
                .setValue(SignalConditionerBlock.PARAM, param);
    }

    private static BlockState indicator(Direction facing) {
        return RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing);
    }

    private static void assertSignal(
            GameTestHelper helper, BlockPos conditioner, BlockPos indicator, int expected, String message
    ) {
        int conditionerOutput = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
        int indicatorLevel = helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL);
        if (conditionerOutput != expected || indicatorLevel != expected) {
            helper.fail(message + " (conditioner=" + conditionerOutput + ", indicator=" + indicatorLevel
                    + ", expected=" + expected + ")", conditioner);
        }
    }
}
