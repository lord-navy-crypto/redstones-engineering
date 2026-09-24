package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCircuitMeterBlock;
import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.diagnostic.CopperCommissioningAssessment;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative electrical HMI for the observer-only Copper Circuit Meter. */
public final class CopperCircuitMeterMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_FACE_PREVIOUS = 0;
    public static final int BUTTON_FACE_NEXT = 1;

    private final DataSlot facing = trackedInt();
    private final DataSlot voltage = trackedInt();
    private final DataSlot resistanceCenti = trackedInt();
    private final DataSlot currentMilli = trackedInt();
    private final DataSlot powerCenti = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot commissioningStatus = trackedInt();
    private final DataSlot meterReadingCenti = trackedInt();
    private final DataSlot repeatabilityCenti = trackedInt();
    private final DataSlot biasCenti = trackedInt();
    private final DataSlot driftCenti = trackedInt();
    private final DataSlot noiseCenti = trackedInt();
    private final DataSlot uncertaintyCenti = trackedInt();
    private final DataSlot sampleAgeTicks = trackedInt();
    private final DataSlot sampleCount = trackedInt();
    private final DataSlot measurementQuality = trackedInt();
    private final DataSlot saturated = trackedInt();

    public CopperCircuitMeterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public CopperCircuitMeterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.COPPER_CIRCUIT_METER.get(), containerId, inventory, pos,
                RedstoneEngineering.COPPER_CIRCUIT_METER.get());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof CopperCircuitMeterBlock)) return;
        CopperCircuitMeterBlock.ElectricalDiagnostics diagnostics =
                CopperCircuitMeterBlock.electricalDiagnostics(level, blockPos, state);
        facing.set(state.getValue(CopperCircuitMeterBlock.FACING).ordinal());
        voltage.set(diagnostics.voltage());
        resistanceCenti.set(scaled(diagnostics.equivalentResistance(), 100.0));
        currentMilli.set(scaled(diagnostics.current(), 1000.0));
        powerCenti.set(scaled(diagnostics.power(), 100.0));
        quality.set(diagnostics.quality().ordinal());
        commissioningStatus.set(CopperCommissioningAssessment.assess(
                diagnostics.quality(), diagnostics.voltage()).code());

        MeasurementSnapshot measurement = CopperCircuitMeterBlock.measurement(level, blockPos);
        if (measurement.sampleCount() <= 0) {
            meterReadingCenti.set(0);
            repeatabilityCenti.set(0);
            biasCenti.set(0);
            driftCenti.set(0);
            noiseCenti.set(0);
            uncertaintyCenti.set(0);
            sampleAgeTicks.set(0);
            sampleCount.set(0);
            measurementQuality.set(MeasurementQuality.INVALID.ordinal());
            saturated.set(0);
        } else {
            meterReadingCenti.set(scaled(measurement.reading(), 100.0));
            repeatabilityCenti.set(scaled(measurement.repeatability(), 100.0));
            biasCenti.set(scaled(measurement.bias(), 100.0));
            driftCenti.set(scaled(measurement.drift(), 100.0));
            noiseCenti.set(scaled(measurement.noise(), 100.0));
            uncertaintyCenti.set(scaled(measurement.uncertaintyProxy(), 100.0));
            sampleAgeTicks.set((int)Math.max(0L, Math.min(Short.MAX_VALUE, measurement.sampleAgeTicks())));
            sampleCount.set(Math.max(0, Math.min(Short.MAX_VALUE, measurement.sampleCount())));
            measurementQuality.set(measurement.quality().ordinal());
            saturated.set(measurement.saturated() ? 1 : 0);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = switch (id) {
            case BUTTON_FACE_PREVIOUS -> CopperCircuitMeterBlock.rotateMeasurementFace(level, blockPos, false);
            case BUTTON_FACE_NEXT -> CopperCircuitMeterBlock.rotateMeasurementFace(level, blockPos, true);
            default -> false;
        };
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private static int scaled(double value, double scale) {
        if (!Double.isFinite(value)) return 0;
        long rounded = Math.round(value * scale);
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
    }

    public Direction facing() {
        int ordinal = facing.get();
        Direction[] values = Direction.values();
        return ordinal < 0 || ordinal >= values.length ? Direction.NORTH : values[ordinal];
    }

    public int voltage() { return voltage.get(); }
    public double resistance() { return resistanceCenti.get() / 100.0; }
    public double current() { return currentMilli.get() / 1000.0; }
    public double power() { return powerCenti.get() / 100.0; }
    public PortQuality quality() {
        int ordinal = quality.get();
        PortQuality[] values = PortQuality.values();
        return ordinal < 0 || ordinal >= values.length ? PortQuality.NO_SIGNAL : values[ordinal];
    }
    public CommissioningStatus commissioningStatus() { return CommissioningStatus.fromCode(commissioningStatus.get()); }
    public double meterReading() { return meterReadingCenti.get() / 100.0; }
    public double repeatability() { return repeatabilityCenti.get() / 100.0; }
    public double bias() { return biasCenti.get() / 100.0; }
    public double drift() { return driftCenti.get() / 100.0; }
    public double noise() { return noiseCenti.get() / 100.0; }
    public double uncertaintyProxy() { return uncertaintyCenti.get() / 100.0; }
    public int sampleAgeTicks() { return sampleAgeTicks.get(); }
    public int sampleCount() { return sampleCount.get(); }
    public boolean saturated() { return saturated.get() != 0; }
    public MeasurementQuality measurementQuality() {
        int ordinal = measurementQuality.get();
        MeasurementQuality[] values = MeasurementQuality.values();
        return ordinal < 0 || ordinal >= values.length ? MeasurementQuality.INVALID : values[ordinal];
    }
    public boolean energized() { return quality() == PortQuality.VALID && voltage() > 0; }
}
