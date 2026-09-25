package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Pneumatic BACK input -> isolated vanilla redstone FRONT output. The receiver is a terminal, not a pneumatic bridge. */
public class PneumaticReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final int MIN_RANGE_MODE = 0;
    public static final int MAX_RANGE_MODE = 2;
    public static final int DEFAULT_RANGE_MODE = 2;
    public static final int LOW_FULL_SCALE_PRESSURE = 25;
    public static final int MID_FULL_SCALE_PRESSURE = 50;
    public static final int HIGH_FULL_SCALE_PRESSURE = 100;
    public static final int REDSTONE_FULL_SCALE = 15;
    public static final IntegerProperty RANGE_MODE =
            IntegerProperty.create("range_mode", MIN_RANGE_MODE, MAX_RANGE_MODE);

    public PneumaticReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RANGE_MODE, DEFAULT_RANGE_MODE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_MODE);
    }

    public static int boundedRangeMode(int mode) {
        return Math.max(MIN_RANGE_MODE, Math.min(MAX_RANGE_MODE, mode));
    }

    public static int fullScalePressure(int mode) {
        return switch (boundedRangeMode(mode)) {
            case MIN_RANGE_MODE -> LOW_FULL_SCALE_PRESSURE;
            case 1 -> MID_FULL_SCALE_PRESSURE;
            default -> HIGH_FULL_SCALE_PRESSURE;
        };
    }

    public static int fullScalePressure(BlockState state) {
        return fullScalePressure(state.getValue(RANGE_MODE));
    }

    public static int boundedPressureForScale(int pressure, int fullScale) {
        int boundedScale = Math.max(1, fullScale);
        return Math.max(0, Math.min(boundedScale, pressure));
    }

    public static int scaledOutput(int pressure, int fullScale) {
        int boundedScale = Math.max(1, fullScale);
        int boundedPressure = boundedPressureForScale(pressure, boundedScale);
        return Math.max(0, Math.min(REDSTONE_FULL_SCALE,
                (int) Math.round((boundedPressure / (double) boundedScale) * REDSTONE_FULL_SCALE)));
    }

    public static boolean isClipped(int pressure, int fullScale) {
        return pressure > Math.max(1, fullScale);
    }

    public static double redstoneLevelsPerPressure(int fullScale) {
        return REDSTONE_FULL_SCALE / (double) Math.max(1, fullScale);
    }

    public static double pressurePerRedstoneLevel(int fullScale) {
        return Math.max(1, fullScale) / (double) REDSTONE_FULL_SCALE;
    }

    public static boolean stepRange(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PneumaticReceiverBlock receiver)) return false;
        int mode = state.getValue(RANGE_MODE);
        int span = MAX_RANGE_MODE - MIN_RANGE_MODE + 1;
        int next = MIN_RANGE_MODE + Math.floorMod(
                mode - MIN_RANGE_MODE + (forward ? 1 : -1), span);
        level.setBlock(pos, state.setValue(RANGE_MODE, next), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, receiver, 1);
        return true;
    }

    @Override public MapCodec<PneumaticReceiverBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_RECEIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal"
                )
        );
    }

    private static PneumaticObservationSupport.Observation inputObservation(
            Level level, BlockPos pos, BlockState state
    ) {
        Direction input = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
        return PneumaticObservationSupport.observe(level, pos.relative(input));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        PneumaticObservationSupport.Observation pressure = inputObservation(level, pos, state);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    descriptor.get(), pressure.pressure(), 0.0, 100.0, pressure.quality()
            ));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                descriptor.get(), state.getValue(OUTPUT), pressure.quality()
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return scaledOutput(
                PneumaticNetwork.pressure(level, inputPos(pos, state)),
                fullScalePressure(state)
        );
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                PneumaticObservationSupport.Observation pressure = inputObservation(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Pneumatic receiver pressure=" + pressure.pressure()
                                + "/100 quality=" + pressure.quality()
                                + " fullScale=" + fullScalePressure(state)
                                + " output=" + outputValue(level, pos, state) + "/" + REDSTONE_FULL_SCALE
                                + " gain=" + String.format(java.util.Locale.ROOT, "%.3f",
                                redstoneLevelsPerPressure(fullScalePressure(state)))
                                + " level/pressure"
                                + (isClipped(pressure.pressure(), fullScalePressure(state)) ? " CLIPPED" : "")
                                + " | BACK=PNEUMATIC FRONT=REDSTONE"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
