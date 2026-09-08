package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Observer/converter: BACK Soul-Flux measurement becomes a FRONT 0..15 redstone readout. */
public class SoulFluxMeterBlock extends PassiveDirectionalSignalBlock {
    public SoulFluxMeterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SoulFluxMeterBlock> codec() {
        return RedstoneEngineering.SOUL_FLUX_METER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "SOUL FLUX MEASURE",
                        inputSide(state),
                        EngineeringDomain.SOUL_FLUX,
                        PortKind.MEASUREMENT,
                        PortDirection.INPUT,
                        false,
                        "charge"
                ),
                new EngineeringPort(
                        "REDSTONE READOUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    public record ChargeObservation(int value, PortQuality quality) {}

    /** Observer-only measurement that keeps node presence separate from the numerical charge. */
    public static ChargeObservation inputObservation(Level level, BlockPos pos, BlockState state) {
        Direction input = state.getValue(FACING).getOpposite();
        BlockPos source = pos.relative(input);
        if (!level.hasChunkAt(source)) return new ChargeObservation(0, PortQuality.STALE);
        if (!SoulFluxNetwork.isNode(level, source)) return new ChargeObservation(0, PortQuality.NO_SIGNAL);

        InformationRuntime.Snapshot snapshot = SoulFluxNetwork.chargeSnapshot(level, source);
        int value = Math.max(0, Math.min(100, snapshot.value()));
        if (snapshot.ageTicks() < 0) return new ChargeObservation(0, PortQuality.NO_SIGNAL);
        if (!snapshot.valid() || snapshot.qualityPercent() <= 0) {
            return new ChargeObservation(value, PortQuality.STALE);
        }
        return new ChargeObservation(value, PortQuality.VALID);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        ChargeObservation observation = inputObservation(level, pos, state);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), observation.value(), 0.0, 100.0, observation.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), observation.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    public static int inputCharge(Level level, BlockPos pos, BlockState state) {
        return inputObservation(level, pos, state).value();
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        ChargeObservation observation = inputObservation(level, pos, state);
        return observation.quality() == PortQuality.VALID
                ? Math.min(15, (observation.value() * 15) / 100) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 20);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            ChargeObservation observation = inputObservation(level, pos, state);
            player.displayClientMessage(Component.literal(
                    "Soul Flux meter | BACK SOUL_FLUX=" + observation.value()
                            + "/100 [" + observation.quality().name() + "]"
                            + " → FRONT REDSTONE=" + state.getValue(OUTPUT) + "/15"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
