package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PneumaticFlowMeterBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.block.PneumaticValveBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ninth block-by-block acceptance campaign: pneumatic transport, control, safety and metrology integrity. */
public final class RseNinthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseNinthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void pneumaticPipeBreakRecomputesSeparatedIsland(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos pipeA = new BlockPos(2, 1, 2);
        BlockPos pipeB = new BlockPos(3, 1, 2);
        placePoweredCompressor(helper, source);
        helper.setBlock(pipeA, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(pipeB, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(3, () -> {
            EngineeringPortProvider pipe = RedstoneEngineering.PNEUMATIC_PIPE.get();
            if (pipe.engineeringPorts(helper.getBlockState(pipeA)).size() != 6
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(pipeB)) <= 0) {
                helper.fail("Six-way pneumatic pipe did not propagate source pressure", pipeB);
                return;
            }
            helper.setBlock(pipeA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(pipeB)) != 0) {
                    helper.fail("Breaking a pneumatic pipe left stale pressure on the separated island", pipeB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void airReservoirStoresAndClearsTransientPressure(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos reservoir = new BlockPos(2, 1, 2);
        placePoweredCompressor(helper, source);
        helper.setBlock(reservoir, RedstoneEngineering.AIR_RESERVOIR.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(14, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.AIR_RESERVOIR.get();
            BlockPos world = helper.absolutePos(reservoir);
            if (provider.engineeringPorts(helper.getBlockState(reservoir)).size() != 6
                    || dev.redstoneengineering.block.AirReservoirBlock.storedPressure(helper.getLevel(), world) <= 0) {
                helper.fail("Air reservoir did not accumulate pressure on its six-way pneumatic manifold", reservoir);
                return;
            }
            helper.setBlock(reservoir, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (RuntimeIntStore.peek(helper.getLevel(), "info:air_reservoir", world) != null
                        || RuntimeIntStore.peek(helper.getLevel(), "info:pneumatic", world) != null) {
                    helper.fail("Removed reservoir retained transient stored/line pressure runtime", reservoir);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void pneumaticReceiverIsTerminalConverterNotBridge(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos receiverPos = new BlockPos(2, 1, 2);
        BlockPos frontPipe = new BlockPos(3, 1, 2);
        placePoweredCompressor(helper, source);
        helper.setBlock(receiverPos, RedstoneEngineering.PNEUMATIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(frontPipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(4, () -> {
            BlockState state = helper.getBlockState(receiverPos);
            EngineeringPortProvider receiver = RedstoneEngineering.PNEUMATIC_RECEIVER.get();
            var back = receiver.engineeringPort(state, Direction.WEST).orElse(null);
            var front = receiver.engineeringPort(state, Direction.EAST).orElse(null);
            BlockPos receiverWorld = helper.absolutePos(receiverPos);
            if (back == null || back.domain() != EngineeringDomain.PNEUMATIC || back.direction() != PortDirection.INPUT
                    || front == null || front.domain() != EngineeringDomain.REDSTONE || front.direction() != PortDirection.OUTPUT
                    || state.getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                helper.fail("Pneumatic receiver did not expose PNEUMATIC BACK -> REDSTONE FRONT conversion", receiverPos);
                return;
            }
            if (PneumaticNetwork.collect(helper.getLevel(), receiverWorld).contains(helper.absolutePos(frontPipe))
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe)) != 0) {
                helper.fail("Pneumatic receiver incorrectly bridged pressure through its redstone FRONT", frontPipe);
                return;
            }
            if (RedstoneEngineering.PNEUMATIC_RECEIVER.get().canConnectRedstone(
                    state, helper.getLevel(), receiverWorld, Direction.EAST)
                    || !RedstoneEngineering.PNEUMATIC_RECEIVER.get().canConnectRedstone(
                    state, helper.getLevel(), receiverWorld, Direction.WEST)) {
                helper.fail("Pneumatic receiver vanilla-redstone connectivity disagreed with physical domains", receiverPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void manualValveUsesAxialPortsAndClosedStateSplitsFlow(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos valvePos = new BlockPos(2, 1, 2);
        BlockPos frontPipe = new BlockPos(3, 1, 2);
        BlockPos sidePipe = new BlockPos(2, 1, 1);
        placePoweredCompressor(helper, source);
        helper.setBlock(valvePos, RedstoneEngineering.PNEUMATIC_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(frontPipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(sidePipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(3, () -> {
            EngineeringPortProvider valve = RedstoneEngineering.PNEUMATIC_VALVE.get();
            if (valve.engineeringPorts(helper.getBlockState(valvePos)).size() != 2
                    || valve.engineeringPorts(helper.getBlockState(valvePos)).stream()
                    .anyMatch(port -> port.direction() != PortDirection.BIDIRECTIONAL)
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe)) <= 0
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(sidePipe)) != 0) {
                helper.fail("Open manual valve was not an axial BACK<->FRONT device", valvePos);
                return;
            }
            BlockState closed = helper.getBlockState(valvePos).setValue(PneumaticValveBlock.OPEN, false);
            helper.setBlock(valvePos, closed);
            PneumaticNetwork.recomputeAround(helper.getLevel(), helper.absolutePos(valvePos));
            helper.runAfterDelay(3, () -> {
                if (PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe)) != 0) {
                    helper.fail("Closed manual valve did not isolate downstream pressure", frontPipe);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void checkValveAllowsBackToFrontAndRejectsReverse(GameTestHelper helper) {
        BlockPos back = new BlockPos(1, 1, 2);
        BlockPos valve = new BlockPos(2, 1, 2);
        BlockPos front = new BlockPos(3, 1, 2);
        placePoweredCompressor(helper, back);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(front, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, back);

        helper.runAfterDelay(3, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get();
            BlockState state = helper.getBlockState(valve);
            if (provider.engineeringPort(state, Direction.WEST).orElseThrow().direction() != PortDirection.INPUT
                    || provider.engineeringPort(state, Direction.EAST).orElseThrow().direction() != PortDirection.OUTPUT
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(front)) <= 0) {
                helper.fail("Check valve did not pass BACK -> FRONT pressure", valve);
                return;
            }

            helper.setBlock(back.above(), Blocks.AIR.defaultBlockState());
            helper.setBlock(back, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
            helper.setBlock(front, RedstoneEngineering.AIR_COMPRESSOR.get().defaultBlockState());
            helper.setBlock(front.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            recompute(helper, front);
            helper.runAfterDelay(3, () -> {
                if (PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(back)) != 0) {
                    helper.fail("Check valve leaked FRONT -> BACK reverse pressure", back);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void flowMeterReportsDirectionalDropAndClearsRuntime(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos meter = new BlockPos(2, 1, 2);
        BlockPos frontPipe = new BlockPos(3, 1, 2);
        BlockPos sidePipe = new BlockPos(2, 1, 1);
        placePoweredCompressor(helper, source);
        helper.setBlock(meter, RedstoneEngineering.PNEUMATIC_FLOW_METER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(frontPipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(sidePipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(4, () -> {
            BlockPos world = helper.absolutePos(meter);
            EngineeringPortProvider provider = RedstoneEngineering.PNEUMATIC_FLOW_METER.get();
            if (provider.engineeringPorts(helper.getBlockState(meter)).size() != 2
                    || PneumaticFlowMeterBlock.flowProxy(helper.getLevel(), world) <= 0
                    || PneumaticFlowMeterBlock.inletPressure(helper.getLevel(), world)
                    <= PneumaticFlowMeterBlock.outletPressure(helper.getLevel(), world)
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(sidePipe)) != 0) {
                helper.fail("Flow meter did not expose directional pressure-drop metrology", meter);
                return;
            }
            helper.setBlock(meter, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (RuntimeIntStore.peek(helper.getLevel(), "pneumatic_flow", world) != null) {
                    helper.fail("Removed flow meter retained transient ΔP/flow runtime", meter);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void proportionalValveUsesUpCommandAndAxialPneumaticPorts(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos valve = new BlockPos(2, 1, 2);
        BlockPos frontPipe = new BlockPos(3, 1, 2);
        BlockPos sidePipe = new BlockPos(2, 1, 1);
        placePoweredCompressor(helper, source);
        helper.setBlock(valve, RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(valve.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(frontPipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(sidePipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(3, () -> {
            EngineeringPortProvider provider = RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get();
            BlockState state = helper.getBlockState(valve);
            var command = provider.engineeringPort(state, Direction.UP).orElse(null);
            if (provider.engineeringPorts(state).size() != 3
                    || command == null || command.domain() != EngineeringDomain.REDSTONE
                    || command.direction() != PortDirection.INPUT
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe)) <= 0
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(sidePipe)) != 0) {
                helper.fail("Proportional valve did not enforce UP command + BACK -> FRONT pneumatic topology", valve);
                return;
            }
            helper.setBlock(valve.above(), Blocks.AIR.defaultBlockState());
            recompute(helper, source);
            helper.runAfterDelay(3, () -> {
                if (PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe)) != 0) {
                    helper.fail("Zero opening command did not isolate proportional-valve outlet", frontPipe);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void reliefValveClampsAndCountsVentEdges(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos relief = new BlockPos(2, 1, 2);
        BlockPos frontPipe = new BlockPos(3, 1, 2);
        BlockPos sidePipe = new BlockPos(2, 1, 1);
        placePoweredCompressor(helper, source);
        helper.setBlock(relief, RedstoneEngineering.PNEUMATIC_RELIEF_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(frontPipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        helper.setBlock(sidePipe, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        recompute(helper, source);

        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(relief);
            int downstream = PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(frontPipe));
            if (downstream <= 0 || downstream > 74
                    || PneumaticNetwork.pressure(helper.getLevel(), helper.absolutePos(sidePipe)) != 0
                    || PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world) != 1) {
                helper.fail("Relief valve did not clamp 75/100 setpoint or count the first vent edge", relief);
                return;
            }
            recompute(helper, source);
            recompute(helper, source);
            if (PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world) != 1) {
                helper.fail("Sustained overpressure inflated relief vent event count", relief);
                return;
            }
            helper.setBlock(source.above(), Blocks.AIR.defaultBlockState());
            recompute(helper, source);
            helper.runAfterDelay(2, () -> {
                helper.setBlock(source.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                recompute(helper, source);
                helper.runAfterDelay(2, () -> {
                    if (PneumaticReliefValveBlock.ventEvents(helper.getLevel(), world) != 2) {
                        helper.fail("Relief valve did not re-arm its vent edge after pressure returned below setpoint", relief);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static void placePoweredCompressor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RedstoneEngineering.AIR_COMPRESSOR.get().defaultBlockState());
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static void recompute(GameTestHelper helper, BlockPos relativePos) {
        PneumaticNetwork.recompute(helper.getLevel(), helper.absolutePos(relativePos));
    }
}
