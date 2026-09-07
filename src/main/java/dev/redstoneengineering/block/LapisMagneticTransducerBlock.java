package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Magnetic field magnitude -> normalized Lapis precision signal. */
public class LapisMagneticTransducerBlock extends AbstractLapisTransducerBlock {
    public LapisMagneticTransducerBlock(Properties p) { super(p); }

    @Override
    public MapCodec<LapisMagneticTransducerBlock> codec() {
        return RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER_CODEC.value();
    }

    @Override
    protected String runtimeKey() {
        return "lapis_magnetic_transducer";
    }

    @Override
    protected String instrumentName() {
        return "Lapis Hall / Field Transducer";
    }

    @Override
    protected String rangeText(BlockState state) {
        return "B-level 0..15, radius 6";
    }

    @Override
    protected EngineeringDomain inputDomain() {
        return EngineeringDomain.IRON_MAGNETIC;
    }

    @Override
    protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        // Magnetic field is sampled at the transducer body, matching MagneticFieldSensorBlock.
        // The BACK engineering port communicates which side is the sensing boundary, but using
        // inputPos here would place the scan origin on an adjacent magnetic source. fieldSample()
        // deliberately excludes its origin to prevent a sensor from self-counting.
        MagneticPhysics.FieldSample sample = MagneticPhysics.fieldSample(level, pos, 6);
        int normalized = Math.round(EngineeringMath.clamp(sample.field(), 0, 15) * 100.0f / 15.0f);
        PortQuality quality = sample.complete() ? PortQuality.VALID : PortQuality.STALE;
        return new Measurement(
                normalized,
                quality,
                "B-level=" + sample.field() + "/15 coverage=" + sample.scannedCells() + "/" + sample.expectedCells()
        );
    }
}
