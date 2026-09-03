package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Magnetic field magnitude -> normalized Lapis precision signal. */
public class LapisMagneticTransducerBlock extends AbstractLapisTransducerBlock {
    public LapisMagneticTransducerBlock(Properties p) { super(p); }
    @Override public MapCodec<LapisMagneticTransducerBlock> codec() { return RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_magnetic_transducer"; }
    @Override protected String instrumentName() { return "Lapis Hall / Field Transducer"; }
    @Override protected String rangeText(BlockState state) { return "B-level 0..15, radius 6"; }
    @Override protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        int b = MagneticPhysics.fieldAt(level, inputPos(pos, state), 6);
        int normalized = Math.round(EngineeringMath.clamp(b, 0, 15) * 100.0f / 15.0f);
        return new Measurement(normalized, true, "B-level=" + b + "/15");
    }
}
