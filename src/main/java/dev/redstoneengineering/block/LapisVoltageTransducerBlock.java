package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
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
        BlockState s = level.getBlockState(probe);
        boolean valid = s.getBlock() instanceof CopperWireBlock
                || s.getBlock() instanceof CopperVoltageSourceBlock
                || s.getBlock() instanceof CopperResistiveLoadBlock
                || s.getBlock() instanceof CopperCapacitorBlock
                || s.getBlock() instanceof CopperSeriesResistorBlock
                || s.getBlock() instanceof CopperFuseBlock
                || s.getBlock() instanceof InductionCoilBlock
                || s.getBlock() instanceof CopperCableJunctionBlock;
        int v = DomainNetwork.sampleCopperVoltage(level, probe, pos);
        // Directional processors are measurable only when the probe is on a real
        // input/output face. Side-face attachment is not a valid electrical node.
        if (s.hasProperty(DirectionalDomainBlock.FACING)) {
            var facing = s.getValue(DirectionalDomainBlock.FACING);
            valid = valid && (pos.equals(probe.relative(facing)) || pos.equals(probe.relative(facing.getOpposite())));
        }
        int normalized = Math.round(EngineeringMath.clamp(v, 0, 15) * 100.0f / 15.0f);
        return new Measurement(normalized, valid, valid ? "V-level=" + v + "/15" : "invalid probe face");
    }
}
