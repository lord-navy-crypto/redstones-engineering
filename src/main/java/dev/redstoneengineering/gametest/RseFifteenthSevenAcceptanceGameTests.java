package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.EntityDensitySensorBlock;
import dev.redstoneengineering.block.MolecularCloudReceiverBlock;
import dev.redstoneengineering.block.SoulFluxInjectorBlock;
import dev.redstoneengineering.block.SoulFluxMeterBlock;
import dev.redstoneengineering.block.SoulSandReservoirBlock;
import dev.redstoneengineering.block.SoulSoilConduitBlock;
import dev.redstoneengineering.block.TankLevelSensorBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Final tail campaign: two environmental sensors and five Soul-domain devices. */
public final class RseFifteenthSevenAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFifteenthSevenAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void tankLevelSensorPublishesPhysicalApertureAndFrontReadout(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.TANK_LEVEL_SENSOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
        helper.setBlock(sensor.above(), Blocks.WATER.defaultBlockState());
        helper.setBlock(sensor.above(2), Blocks.WATER.defaultBlockState());

        helper.runAfterDelay(12, () -> {
            TankLevelSensorBlock block = RedstoneEngineering.TANK_LEVEL_SENSOR.get();
            BlockState state = helper.getBlockState(sensor);
            if (!hasPort(block, state, Direction.UP, EngineeringDomain.GENERIC, PortKind.SENSOR, PortDirection.INPUT, false)) {
                helper.fail("Tank level sensor did not expose its non-wired UP tank-column aperture", sensor);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.REDSTONE, PortKind.SENSOR, PortDirection.OUTPUT, true)) {
                helper.fail("Tank level sensor did not expose its FRONT redstone readout", sensor);
                return;
            }
            if (TankLevelSensorBlock.physicalCount(helper.getLevel(), helper.absolutePos(sensor)) < 2) {
                helper.fail("Tank level sensor did not observe the placed two-block fluid column", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void entityDensitySensorSeparatesFreeSpaceSenseFromRedstone(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(2, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.ENTITY_DENSITY_SENSOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            EntityDensitySensorBlock block = RedstoneEngineering.ENTITY_DENSITY_SENSOR.get();
            BlockState state = helper.getBlockState(sensor);
            if (!hasPort(block, state, Direction.UP, EngineeringDomain.GENERIC, PortKind.SENSOR, PortDirection.INPUT, false)) {
                helper.fail("Entity density sensor did not expose its free-space occupancy aperture", sensor);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.REDSTONE, PortKind.SENSOR, PortDirection.OUTPUT, true)) {
                helper.fail("Entity density sensor did not expose its FRONT redstone output", sensor);
                return;
            }
            if (block.engineeringPort(state, Direction.WEST).isPresent()) {
                helper.fail("Entity density sensor incorrectly exposed a wired BACK input", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulSoilConduitCarriesAndClearsTransientFlux(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(2, 1, 2);
        helper.setBlock(conduit, RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState());
        BlockPos absolute = helper.absolutePos(conduit);
        SoulFluxNetwork.inject(helper.getLevel(), absolute, 20);

        helper.runAfterDelay(2, () -> {
            SoulSoilConduitBlock block = RedstoneEngineering.SOUL_SOIL_CONDUIT.get();
            BlockState state = helper.getBlockState(conduit);
            if (block.engineeringPorts(state).size() != 6
                    || !hasPort(block, state, Direction.NORTH, EngineeringDomain.SOUL_FLUX, PortKind.BUS, PortDirection.BIDIRECTIONAL, false)) {
                helper.fail("Soul-soil conduit did not expose six-face SOUL_FLUX bus semantics", conduit);
                return;
            }
            if (SoulFluxNetwork.charge(helper.getLevel(), absolute) <= 0) {
                helper.fail("Soul-soil conduit did not carry injected transient flux", conduit);
                return;
            }
            helper.setBlock(conduit, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(1, () -> {
                if (InformationRuntime.value(helper.getLevel(), "soul_flux", absolute) != 0) {
                    helper.fail("Removing Soul-soil conduit left ghost runtime flux", conduit);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void soulReservoirStoresAndDecaysCharge(GameTestHelper helper) {
        BlockPos reservoir = new BlockPos(2, 1, 2);
        helper.setBlock(reservoir, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        BlockPos absolute = helper.absolutePos(reservoir);
        SoulFluxNetwork.inject(helper.getLevel(), absolute, 20);
        int initial = SoulFluxNetwork.charge(helper.getLevel(), absolute);
        if (initial <= 0) {
            helper.fail("Soul-sand reservoir did not store an injected packet", reservoir);
            return;
        }

        helper.runAfterDelay(45, () -> {
            SoulSandReservoirBlock block = RedstoneEngineering.SOUL_SAND_RESERVOIR.get();
            BlockState state = helper.getBlockState(reservoir);
            if (block.engineeringPorts(state).size() != 6
                    || !hasPort(block, state, Direction.UP, EngineeringDomain.SOUL_FLUX, PortKind.BUS, PortDirection.BIDIRECTIONAL, false)) {
                helper.fail("Soul reservoir did not expose six-face SOUL_FLUX storage ports", reservoir);
                return;
            }
            int now = SoulFluxNetwork.charge(helper.getLevel(), absolute);
            if (now >= initial || now <= 0) {
                helper.fail("Soul reservoir did not preserve slow positive decay semantics", reservoir);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void soulInjectorConvertsRedstoneCommandIntoFlux(GameTestHelper helper) {
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos conduit = new BlockPos(3, 1, 2);
        BlockPos power = injector.above();

        helper.setBlock(conduit, RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState());
        helper.setBlock(injector, RedstoneEngineering.SOUL_FLUX_INJECTOR.get().defaultBlockState());
        helper.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(6, () -> {
            SoulFluxInjectorBlock block = RedstoneEngineering.SOUL_FLUX_INJECTOR.get();
            BlockState state = helper.getBlockState(injector);
            if (!hasPort(block, state, Direction.UP, EngineeringDomain.REDSTONE, PortKind.CONTROL, PortDirection.INPUT, true)) {
                helper.fail("Soul injector did not expose dedicated UP redstone command input", injector);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.SOUL_FLUX, PortKind.CONVERTER, PortDirection.OUTPUT, false)) {
                helper.fail("Soul injector did not expose its SOUL_FLUX converter output", injector);
                return;
            }
            if (SoulFluxInjectorBlock.commandSignal(helper.getLevel(), helper.absolutePos(injector)) <= 0) {
                helper.fail("Soul injector did not read the UP redstone command", injector);
                return;
            }
            if (SoulFluxNetwork.charge(helper.getLevel(), helper.absolutePos(conduit)) <= 0) {
                helper.fail("Powered Soul injector did not inject flux into adjacent conduit", conduit);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void soulFluxMeterIsSoulInputRedstoneOutputObserver(GameTestHelper helper) {
        BlockPos reservoir = new BlockPos(1, 1, 2);
        BlockPos meter = new BlockPos(2, 1, 2);
        helper.setBlock(reservoir, RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState());
        helper.setBlock(meter, RedstoneEngineering.SOUL_FLUX_METER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        SoulFluxNetwork.inject(helper.getLevel(), helper.absolutePos(reservoir), 80);

        helper.runAfterDelay(25, () -> {
            SoulFluxMeterBlock block = RedstoneEngineering.SOUL_FLUX_METER.get();
            BlockState state = helper.getBlockState(meter);
            if (!hasPort(block, state, Direction.WEST, EngineeringDomain.SOUL_FLUX, PortKind.MEASUREMENT, PortDirection.INPUT, false)) {
                helper.fail("Soul Flux meter BACK face was not a SOUL_FLUX measurement input", meter);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.REDSTONE, PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true)) {
                helper.fail("Soul Flux meter FRONT face was not its redstone readout", meter);
                return;
            }
            if (state.getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                helper.fail("Soul Flux meter did not convert stored charge into redstone readout", meter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void molecularReceiverPublishesAmbientApertureAndClearsRuntime(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        BlockPos absolute = helper.absolutePos(receiver);

        helper.runAfterDelay(3, () -> {
            MolecularCloudReceiverBlock block = RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER.get();
            BlockState state = helper.getBlockState(receiver);
            if (!hasPort(block, state, Direction.UP, EngineeringDomain.GENERIC, PortKind.SENSOR, PortDirection.INPUT, false)) {
                helper.fail("Molecular receiver did not expose its UP free-space sensing aperture", receiver);
                return;
            }
            if (!hasPort(block, state, Direction.EAST, EngineeringDomain.REDSTONE, PortKind.SENSOR, PortDirection.OUTPUT, true)) {
                helper.fail("Molecular receiver did not expose its FRONT redstone readout", receiver);
                return;
            }
            int[] runtime = RuntimeIntStore.get(helper.getLevel(), "molecular_sensor", absolute, 3);
            runtime[0] = 7;
            runtime[2] = 9;
            helper.setBlock(receiver, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(1, () -> {
                if (MolecularCloudReceiverBlock.filtered(helper.getLevel(), absolute) != 0) {
                    helper.fail("Removing molecular receiver left filtered runtime history behind", receiver);
                    return;
                }
                helper.succeed();
            });
        });
    }

    private static boolean hasPort(
            EngineeringPortProvider provider,
            BlockState state,
            Direction side,
            EngineeringDomain domain,
            PortKind kind,
            PortDirection direction,
            boolean redstoneConnectable
    ) {
        Optional<EngineeringPort> port = provider.engineeringPort(state, side);
        return port.isPresent()
                && port.get().domain() == domain
                && port.get().kind() == kind
                && port.get().direction() == direction
                && port.get().redstoneConnectable() == redstoneConnectable;
    }
}
