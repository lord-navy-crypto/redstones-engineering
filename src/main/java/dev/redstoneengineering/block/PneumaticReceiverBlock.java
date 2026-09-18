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
    public static final IntegerProperty RANGE_MODE = IntegerProperty.create("range_mode", 0, 2);

    public PneumaticReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RANGE_MODE, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_MODE);
    }

    public static int fullScalePressure(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> 25;
            case 1 -> 50;
            default -> 100;
        };
    }

    public static int fullScalePressure(BlockState state) {
        return fullScalePressure(state.getValue(RANGE_MODE));
    }

    public static int scaledOutput(int pressure, int fullScale) {
        int boundedScale = Math.max(1, fullScale);
        int boundedPressure = Math.max(0, Math.min(boundedScale, pressure));
        return Math.max(0, Math.min(15,
                (int) Math.round((boundedPressure / (double) boundedScale) * 15.0)));
    }

    public static boolean stepRange(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PneumaticReceiverBlock receiver)) return false;
        int mode = state.getValue(RANGE_MODE);
        int next = Math.floorMod(mode + (forward ? 1 : -1), 3);
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
                                + " output=" + outputValue(level, pos, state) + "/15 | BACK=PNEUMATIC FRONT=REDSTONE"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
