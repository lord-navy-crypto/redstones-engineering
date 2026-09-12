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
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Front-facing process measurement display with a single BACK redstone input port. */
public class AnalogIndicatorBlock extends DirectionalRedstoneEndpointBlock implements EngineeringPortProvider {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    public record InputObservation(int value, PortQuality quality) {}

    public AnalogIndicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    public MapCodec<AnalogIndicatorBlock> codec() {
        return RedstoneEngineering.ANALOG_INDICATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "SIGNAL IN",
                backSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                true,
                "signal"
        ));
    }

    /**
     * Read a redstone value and its independent source evidence. A connected source
     * configured to zero is a real measurement; an empty back face is NO_SIGNAL.
     * Missing chunk coverage is STALE and must not overwrite the last displayed value.
     * Engineering-aware upstream devices also propagate their quality state instead of
     * being reduced to the weaker "a block exists here" heuristic.
     */
    public InputObservation inputObservation(Level level, BlockPos pos, BlockState state) {
        Direction back = backSide(state);
        BlockPos sourcePos = pos.relative(back);
        if (!level.hasChunkAt(sourcePos)) {
            return new InputObservation(state.getValue(LEVEL), PortQuality.STALE);
        }

        int value = readBackInput(level, pos, state);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (sourceState.isAir()) return new InputObservation(0, PortQuality.NO_SIGNAL);

        if (sourceState.getBlock() instanceof EngineeringPortProvider provider) {
            Direction sourceFace = back.getOpposite();
            Optional<EngineeringPort> sourcePort = provider.engineeringPort(sourceState, sourceFace);
            if (sourcePort.isPresent()) {
                EngineeringPort port = sourcePort.get();
                if (port.domain() == EngineeringDomain.REDSTONE
                        && port.redstoneConnectable()
                        && port.direction() != PortDirection.INPUT) {
                    Optional<EngineeringPortSnapshot> sourceSnapshot = provider.engineeringSnapshot(
                            level, sourcePos, sourceState, sourceFace);
                    if (sourceSnapshot.isPresent()) {
                        EngineeringPortSnapshot snapshot = sourceSnapshot.get();
                        return new InputObservation(
                                EngineeringSignal.clamp((int) Math.round(snapshot.value())),
                                snapshot.quality());
                    }
                    // A declared redstone output without richer runtime evidence still counts
                    // as a connected source, preserving the legacy valid-zero behavior.
                    return new InputObservation(value, PortQuality.VALID);
                }
            }
        }

        if (value > 0) return new InputObservation(value, PortQuality.VALID);
        if (sourceState.getBlock().canConnectRedstone(sourceState, level, sourcePos, back)) {
            return new InputObservation(0, PortQuality.VALID);
        }
        return new InputObservation(0, PortQuality.NO_SIGNAL);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        InputObservation observation = inputObservation(level, pos, state);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && connectionMatches(direction, backSide(state));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) update(level, pos, state);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moved
    ) {
        if (!level.isClientSide) update(level, pos, state);
    }

    private void update(Level level, BlockPos pos, BlockState state) {
        InputObservation observation = inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return;
        int value = observation.value();
        if (value != state.getValue(LEVEL)) {
            level.setBlock(pos, state.setValue(LEVEL, value), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                InputObservation observation = inputObservation(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Analog Process Indicator = " + state.getValue(LEVEL) + "/15"
                                + " | inputQuality=" + observation.quality()
                                + " | FRONT display=" + frontSide(state).getName()
                                + " BACK IN=" + backSide(state).getName()
                                + " | readout-only"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
