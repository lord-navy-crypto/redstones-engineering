package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Common drive evidence; mechanical and hydroacoustic propagation remain distinct. */
final class ExciterDriveSupport {
    private ExciterDriveSupport() {}

    static RedstoneObservationSupport.Observation observation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.DOWN);
    }

    static int amplitude(Level level, BlockPos pos) {
        var drive = observation(level, pos);
        return drive.valid() ? drive.value() : 0;
    }

    static EngineeringPortSnapshot snapshot(EngineeringPort port, Direction face,
                                            RedstoneObservationSupport.Observation drive) {
        int amplitude = drive.valid() ? drive.value() : 0;
        if (face == Direction.DOWN)
            return EngineeringPortSnapshot.redstone(port, drive.value(), drive.quality());
        PortQuality quality = drive.valid()
                ? (amplitude > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL)
                : drive.quality();
        return new EngineeringPortSnapshot(port, amplitude, 0.0, 15.0, quality);
    }
}
