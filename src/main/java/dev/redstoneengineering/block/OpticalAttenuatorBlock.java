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
import dev.redstoneengineering.physics.EngineeringMath;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Configurable passive optical loss element; complete attenuation is a valid transfer result, not a fault. */
public class OpticalAttenuatorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty LOSS = IntegerProperty.create("loss", 0, 8);

    public record AttenuationEvidence(int inputIntensity, int channel, PortQuality inputQuality,
                                      int loss, int expectedOutputIntensity, boolean fullyAttenuated) {}

    public OpticalAttenuatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOSS, 2));
    }

    @Override public MapCodec<OpticalAttenuatorBlock> codec() { return RedstoneEngineering.OPTICAL_ATTENUATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(LOSS); }

    public static AttenuationEvidence evidence(Level level, BlockPos pos, BlockState state) {
        Direction inputSide = seriesInputSide(state);
        OpticalObservationSupport.Observation input = OpticalObservationSupport.observe(level, pos.relative(inputSide));
        int loss = state.getValue(LOSS);
        int out = input.quality() == PortQuality.VALID ? EngineeringMath.opticalAfterLoss(input.intensity(), loss) : 0;
        return new AttenuationEvidence(input.intensity(), input.channel(), input.quality(), loss, out,
                input.quality() == PortQuality.VALID && input.intensity() > 0 && out == 0);
    }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("OPTICAL INPUT", inputSide(state), EngineeringDomain.OPTICAL, PortKind.CONVERTER, PortDirection.INPUT, false, "intensity"),
                new EngineeringPort("OPTICAL ATTENUATED OUTPUT", outputSide(state), EngineeringDomain.OPTICAL, PortKind.CONVERTER, PortDirection.OUTPUT, false, "intensity"));
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos samplePos = side == inputSide(state) ? inputPos(pos, state) : outputPos(pos, state);
        OpticalObservationSupport.Observation observation = OpticalObservationSupport.observe(level, samplePos);
        return Optional.of(new EngineeringPortSnapshot(port.get(), observation.intensity(), 0.0, 15.0, observation.quality()));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean routeChanged = state.is(oldState.getBlock())
                && oldState.hasProperty(FACING)
                && oldState.hasProperty(INPUT_FACING)
                && (state.getValue(FACING) != oldState.getValue(FACING)
                || state.getValue(INPUT_FACING) != oldState.getValue(INPUT_FACING));
        if (routeChanged || (state.is(oldState.getBlock()) && oldState.hasProperty(LOSS)
                && state.getValue(LOSS).intValue() != oldState.getValue(LOSS).intValue())) {
            configurationChanged(serverLevel, pos, oldState.hasProperty(FACING) ? oldState : state);
        }
        serverLevel.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AttenuationEvidence evidence = evidence(level, pos, state);
        boolean driven = evidence.inputQuality() == PortQuality.VALID && evidence.expectedOutputIntensity() > 0;
        DomainNetwork.driveOptical(level, outputPos(pos, state), pos,
                evidence.expectedOutputIntensity(), evidence.channel(), driven);
        level.scheduleTick(pos, this, 2);
    }

    public static void invalidateOutput(ServerLevel level, BlockPos pos, BlockState state) {
        Direction output = seriesOutputSide(state);
        DomainNetwork.driveOptical(level, pos.relative(output), pos, 0, 0, false);
    }

    /** Shared state-transition hook so every configuration path clears the previous carrier first. */
    public static void configurationChanged(ServerLevel level, BlockPos pos, BlockState state) {
        invalidateOutput(level, pos, state);
        if (level.getBlockState(pos).getBlock() instanceof OpticalAttenuatorBlock attenuator) level.scheduleTick(pos, attenuator, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel serverLevel) {
            invalidateOutput(serverLevel, pos, state);
            DomainNetwork.recomputeOpticalAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            FieldDeviceUi.open(serverPlayer, pos);
        } else if (!level.isClientSide) {
            int loss = state.getValue(LOSS);
            loss = loss >= 8 ? 0 : loss + 1;
            BlockState next = state.setValue(LOSS, loss);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            AttenuationEvidence evidence = evidence(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Optical attenuator | loss index=" + loss
                            + " | input quality=" + evidence.inputQuality()
                            + " | input=" + evidence.inputIntensity() + "/15"
                            + " | expected output=" + evidence.expectedOutputIntensity() + "/15"
                            + (evidence.fullyAttenuated() ? " | FULLY ATTENUATED" : "")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
