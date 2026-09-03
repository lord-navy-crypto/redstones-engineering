package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Optical power detector / photodiode: light intensity -> Lapis precision signal. */
public class LapisOpticalTransducerBlock extends AbstractLapisTransducerBlock {
    public LapisOpticalTransducerBlock(Properties p) { super(p); }
    @Override public MapCodec<LapisOpticalTransducerBlock> codec() { return RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_optical_transducer"; }
    @Override protected String instrumentName() { return "Lapis Optical Power Transducer"; }
    @Override protected String rangeText(BlockState state) { return "optical intensity 0..15"; }
    @Override protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        var sample = DomainNetwork.sampleOptical(level, inputPos(pos, state));
        int normalized = Math.round(EngineeringMath.clamp(sample.intensity(), 0, 15) * 100.0f / 15.0f);
        return new Measurement(normalized, sample.valid(), "I=" + sample.intensity() + "/15 channel=" + sample.channel());
    }
}
