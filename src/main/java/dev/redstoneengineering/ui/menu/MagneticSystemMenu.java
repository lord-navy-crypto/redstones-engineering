package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative HMI model for magnetic sources, actuators, converters and observers. */
public final class MagneticSystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_ELECTROMAGNET = 0;
    public static final int KIND_PERMANENT = 1;
    public static final int KIND_COIL = 2;
    public static final int KIND_FIELD_SENSOR = 3;
    public static final int KIND_GRADIENT = 4;

    public static final int BUTTON_PRIMARY_PREVIOUS = 0;
    public static final int BUTTON_PRIMARY_NEXT = 1;
    public static final int BUTTON_ROTATE_LEFT = 2;
    public static final int BUTTON_ROTATE_RIGHT = 3;
    public static final int BUTTON_INPUT_LEFT = 4;
    public static final int BUTTON_INPUT_RIGHT = 5;
    public static final int BUTTON_OUTPUT_LEFT = 6;
    public static final int BUTTON_OUTPUT_RIGHT = 7;
    public static final int BUTTON_SECONDARY_PREVIOUS = 8;
    public static final int BUTTON_SECONDARY_NEXT = 9;
    public static final int BUTTON_TERTIARY_PREVIOUS = 10;
    public static final int BUTTON_TERTIARY_NEXT = 11;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot extra = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot complete = trackedInt();
    private final DataSlot engineeringA = trackedInt();
    private final DataSlot engineeringB = trackedInt();
    private final DataSlot engineeringC = trackedInt();
    private final DataSlot runtimeA = trackedInt();
    private final DataSlot runtimeB = trackedInt();

    public MagneticSystemMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public MagneticSystemMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.MAGNETIC_SYSTEM.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0); extra.set(0);
        quality.set(PortQuality.NO_SIGNAL.ordinal()); facing.set(-1); inputFacing.set(-1); outputFacing.set(-1); complete.set(0);
        engineeringA.set(0); engineeringB.set(0); engineeringC.set(0); runtimeA.set(0); runtimeB.set(0);

        if (block instanceof ElectromagnetBlock) {
            kind.set(KIND_ELECTROMAGNET);
            CopperNetworkSupport.TerminalInput input = ElectromagnetBlock.input(level, blockPos);
            var response = ElectromagnetBlock.configuredResponse(level, blockPos);
            primary.set(state.getValue(ElectromagnetBlock.FIELD));
            secondary.set(input.voltage());
            tertiary.set(input.connectedFeeds());
            auxiliary.set(ElectromagnetBlock.targetField(level, blockPos));
            extra.set(ElectromagnetBlock.thermalLoad(level, blockPos));
            engineeringA.set(response.a());
            engineeringB.set(response.b());
            engineeringC.set(response.c());
            runtimeA.set(ElectromagnetBlock.trackingError(level, blockPos));
            runtimeB.set(ElectromagnetBlock.runTicks(level, blockPos));
            quality.set(input.quality().ordinal());
            complete.set(input.quality() == PortQuality.VALID ? 1 : 0);
        } else if (block instanceof PermanentMagnetBlock) {
            kind.set(KIND_PERMANENT);
            PermanentMagnetBlock.SourceEvidence evidence = PermanentMagnetBlock.evidence(state);
            primary.set(evidence.strength());
            facing.set(evidence.northMarker().ordinal());
            quality.set(PortQuality.VALID.ordinal());
            complete.set(1);
        } else if (block instanceof InductionCoilBlock coil) {
            kind.set(KIND_COIL);
            Direction in = DirectionalDomainBlock.seriesInputSide(state);
            Direction out = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(in.ordinal());
            outputFacing.set(out.ordinal());
            facing.set(out.ordinal());
            var input = coil.engineeringSnapshot(level, blockPos, state, in);
            primary.set(input.map(s -> (int) Math.round(s.value())).orElse(0));
            secondary.set(InductionCoilBlock.outputVoltage(level, blockPos));
            tertiary.set(InductionCoilBlock.configuredTurns(level, blockPos, state));
            engineeringA.set(InductionCoilBlock.configuredTurns(level, blockPos, state));
            engineeringB.set(state.getValue(InductionCoilBlock.TURNS));
            PortQuality q = InductionCoilBlock.outputQuality(level, blockPos);
            quality.set(q.ordinal());
            complete.set(q == PortQuality.VALID ? 1 : 0);
        } else if (block instanceof MagneticFieldSensorBlock) {
            kind.set(KIND_FIELD_SENSOR);
            MagneticFieldSensorBlock.Observation observation = MagneticFieldSensorBlock.observation(level, blockPos, state);
            primary.set(observation.field());
            secondary.set(observation.scannedCells());
            tertiary.set(observation.expectedCells());
            complete.set(observation.complete() ? 1 : 0);
            quality.set(observation.complete() ? PortQuality.VALID.ordinal() : PortQuality.STALE.ordinal());
        } else if (block instanceof MagneticGradientMeterBlock) {
            kind.set(KIND_GRADIENT);
            MagneticGradientMeterBlock.GradientSample gx = MagneticGradientMeterBlock.gradient(level, blockPos, Direction.Axis.X);
            MagneticGradientMeterBlock.GradientSample gy = MagneticGradientMeterBlock.gradient(level, blockPos, Direction.Axis.Y);
            MagneticGradientMeterBlock.GradientSample gz = MagneticGradientMeterBlock.gradient(level, blockPos, Direction.Axis.Z);
            primary.set(gx.value()); secondary.set(gy.value()); tertiary.set(gz.value());
            auxiliary.set(MagneticPhysics.fieldAt(level, blockPos, 6));
            extra.set(gx.scannedCells() + gy.scannedCells() + gz.scannedCells());
            boolean allComplete = gx.complete() && gy.complete() && gz.complete();
            complete.set(allComplete ? 1 : 0);
            quality.set(allComplete ? PortQuality.VALID.ordinal() : PortQuality.STALE.ordinal());
        } else kind.set(-1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof ElectromagnetBlock) {
            if (!(level instanceof ServerLevel server)) return false;
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                changed = ElectromagnetBlock.setEngineeringParameters(
                        server, blockPos,
                        engineeringA.get() + (id == BUTTON_PRIMARY_NEXT ? 1 : -1),
                        engineeringB.get(),
                        engineeringC.get());
            } else if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                changed = ElectromagnetBlock.setEngineeringParameters(
                        server, blockPos,
                        engineeringA.get(),
                        engineeringB.get() + (id == BUTTON_SECONDARY_NEXT ? 1 : -1),
                        engineeringC.get());
            } else if (id == BUTTON_TERTIARY_PREVIOUS || id == BUTTON_TERTIARY_NEXT) {
                changed = ElectromagnetBlock.setEngineeringParameters(
                        server, blockPos,
                        engineeringA.get(),
                        engineeringB.get(),
                        engineeringC.get() + (id == BUTTON_TERTIARY_NEXT ? 1 : -1));
            } else return false;
        } else if (block instanceof PermanentMagnetBlock) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int strength = state.getValue(PermanentMagnetBlock.STRENGTH);
                strength = id == BUTTON_PRIMARY_NEXT ? (strength >= 15 ? 1 : strength + 1) : (strength <= 1 ? 15 : strength - 1);
                level.setBlock(blockPos, state.setValue(PermanentMagnetBlock.STRENGTH, strength), Block.UPDATE_CLIENTS);
                changed = true;
            } else if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
                Direction current = state.getValue(PermanentMagnetBlock.FACING);
                Direction next = id == BUTTON_ROTATE_RIGHT ? current.getClockWise() : current.getCounterClockWise();
                level.setBlock(blockPos, state.setValue(PermanentMagnetBlock.FACING, next), Block.UPDATE_CLIENTS);
                changed = true;
            } else return false;
        } else if (block instanceof InductionCoilBlock) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                if (!(level instanceof ServerLevel server)) return false;
                changed = InductionCoilBlock.setConfiguredTurns(
                        server, blockPos, engineeringA.get() + (id == BUTTON_PRIMARY_NEXT ? 1 : -1));
            } else if (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT
                    || id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT
                    || id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
                // A physical induction coil is a straight-through converter: magnetic INPUT and
                // copper OUTPUT remain exactly opposite. Legacy endpoint IDs rotate one rigid axis.
                boolean clockwise = id == BUTTON_INPUT_RIGHT || id == BUTTON_OUTPUT_RIGHT || id == BUTTON_ROTATE_RIGHT;
                changed = DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, clockwise);
            } else return false;
            if (changed) level.scheduleTick(blockPos, block, 1);
        } else return false;

        if (changed) { refreshAuthoritativeSnapshot(); broadcastChanges(); }
        return changed;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); }
    public int extra() { return extra.get(); }
    public int engineeringA() { return engineeringA.get(); }
    public int engineeringB() { return engineeringB.get(); }
    public int engineeringC() { return engineeringC.get(); }
    public int runtimeA() { return runtimeA.get(); }
    public int runtimeB() { return runtimeB.get(); }
    public boolean complete() { return complete.get() != 0; }
    public PortQuality quality() {
        int o = quality.get(); PortQuality[] all = PortQuality.values();
        return o < 0 || o >= all.length ? PortQuality.NO_SIGNAL : all[o];
    }
    public Direction facing() {
        int o = facing.get(); Direction[] all = Direction.values();
        return o < 0 || o >= all.length ? Direction.NORTH : all[o];
    }
    public boolean hasInputEndpoint() { return kind.get() == KIND_COIL && inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return kind.get() == KIND_COIL && outputFacing.get() >= 0; }
}
