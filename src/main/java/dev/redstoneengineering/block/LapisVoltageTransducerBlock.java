package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.physics.CopperObservationSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Copper-domain voltage -> isolated Lapis measurement signal. */
public class LapisVoltageTransducerBlock extends AbstractLapisTransducerBlock {
    public LapisVoltageTransducerBlock(Properties p) { super(p); }
    @Override public MapCodec<LapisVoltageTransducerBlock> codec() { return RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_voltage_transducer"; }
    @Override protected String instrumentName() { return "Lapis Voltage Transducer"; }
    @Override protected String rangeText(BlockState state) { return "Copper V-level 0..15"; }
    @Override protected EngineeringDomain inputDomain() { return EngineeringDomain.COPPER; }
    @Override protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos probe = inputPos(pos, state);
        if (!level.hasChunkAt(probe)) {
            return new Measurement(0, dev.redstoneengineering.core.port.PortQuality.STALE, "Copper coverage unavailable");
        }

        // DomainNetwork owns the numerical voltage. CopperObservationSupport independently
        // owns source/topology evidence, so an isolated wire is not promoted to VALID merely
        // because its numeric voltage happens to be zero.
        int v = DomainNetwork.sampleCopperVoltage(level, probe, pos);
        CopperObservationSupport.Observation observation = CopperObservationSupport.measure(level, probe, pos);
        int normalized = Math.round(EngineeringMath.clamp(v, 0, 15) * 100.0f / 15.0f);
        return new Measurement(
                normalized,
                observation.quality(),
                "V-level=" + v + "/15 quality=" + observation.quality()
        );
    }
}
