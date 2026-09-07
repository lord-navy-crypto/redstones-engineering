package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.OpticalObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Passive 1x2 optical splitter. Integer carrier levels make odd-input split loss explicit. */
public class OpticalSplitterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public record SplitEvidence(
            int inputIntensity,
            int channel,
            PortQuality inputQuality,
            int branchAIntensity,
            int branchBIntensity,
            int quantizationLoss
    ) {}

    public OpticalSplitterBlock(Properties properties) { super(properties); }
    @Override public MapCodec<OpticalSplitterBlock> codec() { return RedstoneEngineering.OPTICAL_SPLITTER_CODEC.value(); }

    private BlockPos branchBPos(BlockPos pos, BlockState state) {
        return pos.relative(leftOf(outputSide(state)));
    }

    public SplitEvidence evidence(Level level, BlockPos pos, BlockState state) {
        OpticalObservationSupport.Observation input = OpticalObservationSupport.observe(level, inputPos(pos, state));
        int branch = input.quality() == PortQuality.VALID ? input.intensity() / 2 : 0;
        int quantizationLoss = input.quality() == PortQuality.VALID
                ? Math.max(0, input.intensity() - branch * 2)
                : 0;
        return new SplitEvidence(
                input.intensity(), input.channel(), input.quality(), branch, branch, quantizationLoss);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("OPTICAL INPUT", inputSide(state), EngineeringDomain.OPTICAL,
                        PortKind.BUS, PortDirection.INPUT, false, "intensity"),
                new EngineeringPort("OPTICAL OUTPUT A", outputSide(state), EngineeringDomain.OPTICAL,
                        PortKind.BUS, PortDirection.OUTPUT, false, "intensity"),
                new EngineeringPort("OPTICAL OUTPUT B", leftOf(outputSide(state)), EngineeringDomain.OPTICAL,
                        PortKind.BUS, PortDirection.OUTPUT, false, "intensity"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos samplePos = side == inputSide(state) ? inputPos(pos, state) : pos.relative(side);
        OpticalObservationSupport.Observation observation = OpticalObservationSupport.observe(level, samplePos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), observation.intensity(), 0.0, 15.0, observation.quality()));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SplitEvidence evidence = evidence(level, pos, state);
        boolean driven = evidence.inputQuality() == PortQuality.VALID && evidence.branchAIntensity() > 0;
        DomainNetwork.driveOptical(
                level, outputPos(pos, state), pos,
                evidence.branchAIntensity(), evidence.channel(), driven);
        DomainNetwork.driveOptical(
                level, branchBPos(pos, state), pos,
                evidence.branchBIntensity(), evidence.channel(), driven);
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel serverLevel) {
            DomainNetwork.driveOptical(serverLevel, outputPos(pos, state), pos, 0, 0, false);
            DomainNetwork.driveOptical(serverLevel, branchBPos(pos, state), pos, 0, 0, false);
            DomainNetwork.recomputeOpticalAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            FieldDeviceUi.open(serverPlayer, pos);
        } else if (!level.isClientSide) {
            SplitEvidence evidence = evidence(level, pos, state);
            player.displayClientMessage(Component.literal(
                    "Optical 1x2 splitter | input=" + evidence.inputIntensity() + "/15"
                            + " | channel=" + evidence.channel()
                            + " | quality=" + evidence.inputQuality()
                            + " | branches=" + evidence.branchAIntensity() + "+" + evidence.branchBIntensity()
                            + " | quantization loss=" + evidence.quantizationLoss()
                            + " | idealized ≈3 dB split"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
