package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.IronCoreBlock;
import dev.redstoneengineering.block.SoulSandReservoirBlock;
import dev.redstoneengineering.block.SoulSoilConduitBlock;
import dev.redstoneengineering.block.ThermalCalorimeterBlock;
import dev.redstoneengineering.block.ThermalHeaterBlock;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.block.ThermalRadiatorBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral closure contracts for the final seven-block Pioneer Wave 17. */
public final class RsePioneerWave17GameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RsePioneerWave17GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void finalSevenParameterProfilesMatchImplementedModels(GameTestHelper helper) {
        int[] expectedR = {1, 2, 4, 8};
        for (int i = 0; i < expectedR.length; i++) {
            var state = RedstoneEngineering.THERMAL_HEATER.get().defaultBlockState()
                    .setValue(ThermalHeaterBlock.RESISTANCE_INDEX, i);
            if (ThermalHeaterBlock.resistance(state) != expectedR[i]) {
                helper.fail("Thermal heater resistance profile drifted from 1/2/4/8", MARKER);
                return;
            }
        }
        for (int v = 0; v <= 15; v++) {
            for (int r : expectedR) {
                int target = ThermalHeaterBlock.targetTemperature(v, r);
                if (target < ThermalPhysics.AMBIENT || target > 100) {
                    helper.fail("Thermal heater target must remain bounded by ambient..100", MARKER);
                    return;
                }
            }
        }
        if (IronCoreBlock.magnetizeThreshold() != 8 || IronCoreBlock.appliedFieldRadius() != 2) {
            helper.fail("Iron-core Pioneer contract must expose the implemented threshold/radius", MARKER);
            return;
        }
        if (SoulSoilConduitBlock.decayPeriodTicks() != 20 || SoulSandReservoirBlock.decayPeriodTicks() != 40) {
            helper.fail("Soul transport/storage decay profiles drifted from implemented 20/40 tick cadence", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void thermalMassCapacityChangesBoundedResponse(GameTestHelper helper) {
        helper.setBlock(MARKER, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 20)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 1));
        var low = ThermalMassBlock.thermalState(helper.getLevel(), helper.absolutePos(MARKER), helper.getBlockState(MARKER));

        helper.setBlock(MARKER, helper.getBlockState(MARKER).setValue(ThermalMassBlock.HEAT_CAPACITY, 4));
        var high = ThermalMassBlock.thermalState(helper.getLevel(), helper.absolutePos(MARKER), helper.getBlockState(MARKER));

        if (low.maxStep() != 4 || high.maxStep() != 1 || low.capacity() != 1 || high.capacity() != 4) {
            helper.fail("Thermal mass capacity must retain maxStep=max(1,5-C)", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void calorimeterHistoryIsServerRetainedObserverEvidence(GameTestHelper helper) {
        BlockPos calorimeter = new BlockPos(2, 1, 2);
        BlockPos mass = calorimeter.east();
        helper.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState()
                .setValue(ThermalMassBlock.TEMPERATURE, 60)
                .setValue(ThermalMassBlock.HEAT_CAPACITY, 3));
        helper.setBlock(calorimeter, RedstoneEngineering.THERMAL_CALORIMETER.get().defaultBlockState());

        helper.runAfterDelay(24, () -> {
            BlockPos absolute = helper.absolutePos(calorimeter);
            ThermalCalorimeterBlock.Sample sample = ThermalCalorimeterBlock.sample(helper.getLevel(), absolute);
            ThermalCalorimeterBlock.History history = ThermalCalorimeterBlock.history(helper.getLevel(), absolute);
            if (sample.bodyCount() != 1 || sample.heatCapacity() != 3 || !history.initialized()) {
                helper.fail("Calorimeter must retain server history while observing the adjacent Thermal Mass", calorimeter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void soulReservoirValidZeroDiffersFromAbsentConduitFlux(GameTestHelper helper) {
        BlockPos reservoir = new BlockPos(1, 1, 2);
        BlockPos conduit = new BlockPos(3, 1, 2);
        helper.setBlock(reservoir, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        helper.setBlock(conduit, RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockPos reservoirAbs = helper.absolutePos(reservoir);
            BlockPos conduitAbs = helper.absolutePos(conduit);
            var reservoirState = helper.getBlockState(reservoir);
            var conduitState = helper.getBlockState(conduit);
            var reservoirSnapshot = RedstoneEngineering.SOUL_SAND_RESERVOIR.get()
                    .engineeringSnapshot(helper.getLevel(), reservoirAbs, reservoirState, Direction.UP).orElse(null);
            var conduitSnapshot = RedstoneEngineering.SOUL_SOIL_CONDUIT.get()
                    .engineeringSnapshot(helper.getLevel(), conduitAbs, conduitState, Direction.UP).orElse(null);
            if (reservoirSnapshot == null || conduitSnapshot == null
                    || reservoirSnapshot.value() != 0.0
                    || reservoirSnapshot.quality() != PortQuality.VALID
                    || conduitSnapshot.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Initialized empty Soul reservoir must be VALID zero while absent conduit flux remains NO_SIGNAL", reservoir);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void finalSevenPortRolesRemainSpecialized(GameTestHelper helper) {
        var ironPorts = RedstoneEngineering.IRON_CORE.get().engineeringPorts(
                RedstoneEngineering.IRON_CORE.get().defaultBlockState());
        var massPorts = RedstoneEngineering.THERMAL_MASS.get().engineeringPorts(
                RedstoneEngineering.THERMAL_MASS.get().defaultBlockState());
        var heaterPorts = RedstoneEngineering.THERMAL_HEATER.get().engineeringPorts(
                RedstoneEngineering.THERMAL_HEATER.get().defaultBlockState());
        var radiatorPorts = RedstoneEngineering.THERMAL_RADIATOR.get().engineeringPorts(
                RedstoneEngineering.THERMAL_RADIATOR.get().defaultBlockState());
        var calorimeterPorts = RedstoneEngineering.THERMAL_CALORIMETER.get().engineeringPorts(
                RedstoneEngineering.THERMAL_CALORIMETER.get().defaultBlockState());

        if (ironPorts.size() != 6 || ironPorts.stream().anyMatch(p -> p.domain() != EngineeringDomain.IRON_MAGNETIC)) {
            helper.fail("Iron core must remain six-face free-space magnetic coupling metadata", MARKER);
            return;
        }
        if (massPorts.size() != 6 || massPorts.stream().anyMatch(p ->
                p.domain() != EngineeringDomain.THERMAL || p.direction() != PortDirection.BIDIRECTIONAL)) {
            helper.fail("Thermal mass must remain six-face bidirectional thermal body state", MARKER);
            return;
        }
        if (heaterPorts.size() != 6 || heaterPorts.stream().anyMatch(p ->
                p.domain() != EngineeringDomain.COPPER || p.direction() != PortDirection.INPUT)) {
            helper.fail("Thermal heater must remain a six-face Copper-input converter", MARKER);
            return;
        }
        if (radiatorPorts.size() != 6 || radiatorPorts.stream().anyMatch(p ->
                p.domain() != EngineeringDomain.THERMAL || p.direction() != PortDirection.INPUT)) {
            helper.fail("Thermal radiator must remain a passive six-face thermal sink", MARKER);
            return;
        }
        if (calorimeterPorts.size() != 6 || calorimeterPorts.stream().anyMatch(p ->
                p.domain() != EngineeringDomain.THERMAL || p.direction() != PortDirection.INPUT)) {
            helper.fail("Thermal calorimeter must remain observer-only thermal measurement input", MARKER);
            return;
        }
        helper.succeed();
    }
}
