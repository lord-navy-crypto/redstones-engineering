package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.diagnostic.CoreMediaDiagnostics;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Explicit vanilla Redstone 0..15 -> normalized Lapis 0..100 scaler. */
public class RedstoneToLapisScalerBlock extends Block implements EngineeringPortProvider {
    /** Lapis TX face. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Redstone RX face. Never intentionally overlaps {@link #FACING}. */
    public static final DirectionProperty INPUT_FACING = DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    private static final String KEY = "redstone_to_lapis_scaler";
    private static final int RUNTIME_SIZE = 2;

    public RedstoneToLapisScalerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH));
    }

    @Override public MapCodec<RedstoneToLapisScalerBlock> codec() { return RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, INPUT_FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, output).setValue(INPUT_FACING, output.getOpposite());
    }

    private Direction outputSide(BlockState state) { return state.getValue(FACING); }
    private Direction inputSide(BlockState state) { return state.getValue(INPUT_FACING); }
    private static int encodeQuality(PortQuality quality) { return quality.ordinal() + 1; }

    public static int outputValue(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE ? 0 : runtime[0];
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[1] <= 0) return PortQuality.STALE;
        int ordinal = runtime[1] - 1;
        PortQuality[] values = PortQuality.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.STALE;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE INPUT", inputSide(state), EngineeringDomain.REDSTONE, PortKind.CONVERTER, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("LAPIS OUTPUT", outputSide(state), EngineeringDomain.LAPIS, PortKind.CONVERTER, PortDirection.OUTPUT, false, "normalized")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            var observation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), observation.value(), observation.quality()));
        }
        return Optional.of(new EngineeringPortSnapshot(port.get(), outputValue(level, pos) / 100.0, 0.0, 1.0, outputQuality(level, pos)));
    }

    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) { return direction != null && direction == inputSide(state).getOpposite(); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) { super.onPlace(state, level, pos, oldState, moved); if (!level.isClientSide) level.scheduleTick(pos, this, 1); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) { if (!level.isClientSide) level.scheduleTick(pos, this, 1); }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var observation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        PortQuality quality = observation.quality();
        runtime[1] = encodeQuality(quality);
        if (quality == PortQuality.STALE) {
            DomainNetwork.driveLapis(level, pos.relative(outputSide(state)), pos, runtime[0], false);
        } else if (observation.valid()) {
            runtime[0] = CoreMediaDiagnostics.lapisFromRedstone(observation.value());
            DomainNetwork.driveLapis(level, pos.relative(outputSide(state)), pos, runtime[0], true);
        } else {
            runtime[0] = 0;
            DomainNetwork.driveLapis(level, pos.relative(outputSide(state)), pos, 0, false);
        }
        level.scheduleTick(pos, this, 2);
    }

    public static boolean rotateInput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneToLapisScalerBlock block)) return false;
        Direction output = state.getValue(FACING);
        Direction oldInput = state.getValue(INPUT_FACING);
        Direction nextInput = nextFreeHorizontal(oldInput, output, clockwise);
        if (nextInput == oldInput) return false;
        level.setBlock(pos, state.setValue(INPUT_FACING, nextInput), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        level.updateNeighborsAt(pos, block);
        return true;
    }

    public static boolean rotateOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneToLapisScalerBlock block)) return false;
        Direction input = state.getValue(INPUT_FACING);
        Direction oldOutput = state.getValue(FACING);
        Direction nextOutput = nextFreeHorizontal(oldOutput, input, clockwise);
        if (nextOutput == oldOutput) return false;
        if (level instanceof ServerLevel server) {
            DomainNetwork.driveLapis(server, pos.relative(oldOutput), pos, 0, false);
        }
        level.setBlock(pos, state.setValue(FACING, nextOutput), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        level.updateNeighborsAt(pos, block);
        return true;
    }

    private static Direction nextFreeHorizontal(Direction current, Direction forbidden, boolean clockwise) {
        Direction candidate = current;
        for (int i = 0; i < 3; i++) {
            candidate = clockwise ? candidate.getClockWise() : candidate.getCounterClockWise();
            if (candidate != forbidden) return candidate;
        }
        return current;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) DomainNetwork.driveLapis(server, pos.relative(outputSide(state)), pos, 0, false);
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
            } else {
                var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
                if (input.valid()) {
                    int output = outputValue(level, pos);
                    int sourceSpacing = CoreMediaDiagnostics.sourceCodeSpacing(input.value());
                    player.displayClientMessage(Component.literal(
                            "Redstone to Lapis Scaler | RX=" + inputSide(state).getName().toUpperCase()
                                    + " | TX=" + outputSide(state).getName().toUpperCase()
                                    + " | input=" + input.value() + "/15"
                                    + " | output=" + String.format("%.2f", output / 100.0)
                                    + " | sourceCodeSpacing=" + String.format("%.2f", sourceSpacing / 100.0)
                                    + " | UPSCALED REPRESENTATION - NO NEW SOURCE PRECISION"
                                    + " | quality=" + outputQuality(level, pos)), true);
                } else {
                    player.displayClientMessage(Component.literal(
                            "Redstone to Lapis Scaler | RX=" + inputSide(state).getName().toUpperCase()
                                    + " | TX=" + outputSide(state).getName().toUpperCase()
                                    + " | input=" + input.quality().name()
                                    + " | outputQuality=" + outputQuality(level, pos)
                                    + " | precision unavailable until Redstone evidence is VALID"), true);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
