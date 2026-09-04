package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Thermal physical state -> normalized Lapis precision signal. */
public class LapisTemperatureTransducerBlock extends AbstractLapisTransducerBlock {
    public LapisTemperatureTransducerBlock(Properties p) { super(p); }
    @Override public MapCodec<LapisTemperatureTransducerBlock> codec() { return RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_temperature_transducer"; }
    @Override protected String instrumentName() { return "Lapis Temperature Transducer"; }
    @Override protected String rangeText(BlockState state) { return "T-index 0..100"; }
    @Override protected EngineeringDomain inputDomain() { return EngineeringDomain.THERMAL; }
    @Override protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos probe = inputPos(pos, state);
        BlockState s = level.getBlockState(probe);
        int t;
        if (s.getBlock() instanceof ThermalMassBlock) t = s.getValue(ThermalMassBlock.TEMPERATURE);
        else if (s.getBlock() instanceof TemperatureSensorBlock) t = s.getValue(TemperatureSensorBlock.TEMPERATURE);
        else t = ThermalPhysics.environmentTarget(level, probe);
        return new Measurement(EngineeringMath.clamp(t, 0, 100), true, "T-index=" + t);
    }
}
