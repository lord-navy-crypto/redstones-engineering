package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PneumaticProportionalValveLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Redstone-commanded inline proportional valve with finite spool travel.
 *
 * BACK is pneumatic inlet, FRONT outlet, UP is opening command. The UP signal is a target;
 * the pneumatic solver consumes actual spool opening rather than jumping instantly to command.
 */
public class PneumaticProportionalValveBlock extends DirectionalDomainBlock implements EntityBlock, EngineeringPortProvider {
    public static final IntegerProperty RESPONSE_MODE = IntegerProperty.create("response_mode", 0, 2);

    private static final String RUNTIME_KEY = "pneumatic_proportional_valve";
    private static final int ACTUAL_OPENING = 0;
    private static final int LAST_DELTA = 1;
    private static final int TRAVEL = 2;
    private static final int REVERSALS = 3;
    private static final int INITIALIZED = 4;
    private static final int RUNTIME_SIZE = 5;

    public PneumaticProportionalValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESPONSE_MODE, 1));
    }

    @Override public MapCodec<PneumaticProportionalValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RESPONSE_MODE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.OUTPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "OPENING COMMAND", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"
                )
        );
    }

    public static RedstoneObservationSupport.Observation commandObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.UP);
    }

    public static int commandedOpening(Level level, BlockPos pos) {
        RedstoneObservationSupport.Observation command = commandObservation(level, pos);
        return command.valid() ? command.value() : 0;
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    /** Actual physical spool opening consumed by PneumaticNetwork. */
    public static int actualOpening(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(15, runtime[ACTUAL_OPENING]));
    }

    /** Compatibility alias retained for the pneumatic solver and existing callers. */
    public static int opening(Level level, BlockPos pos) {
        return actualOpening(level, pos);
    }

    public static int trackingError(Level level, BlockPos pos) {
        return PneumaticProportionalValveLogic.trackingError(
                actualOpening(level, pos), commandedOpening(level, pos));
    }

    public static int travel(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[TRAVEL]);
    }

    public static int reversals(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[REVERSALS]);
    }

    public static boolean stepResponseMode(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PneumaticProportionalValveBlock valve)) return false;
        int mode = state.getValue(RESPONSE_MODE);
        int next = Math.floorMod(mode + (forward ? 1 : -1), 3);
        level.setBlock(pos, state.setValue(RESPONSE_MODE, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, valve, 1);
        return true;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            RedstoneObservationSupport.Observation command = commandObservation(level, pos);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    descriptor.get(), command.value(), command.quality()
            ));
        }
        PneumaticObservationSupport.Observation pressure =
                PneumaticObservationSupport.observe(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure.pressure(), 0.0, 100.0, pressure.quality()
        ));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechatronicsVisualBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getOpposite() == Direction.UP;
    }

    /** Renderer-facing immutable projection; reads actual spool/pressure but never writes simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos) {
        return MechatronicsVisualState.valve(actualOpening(level, pos), PneumaticNetwork.pressure(level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, 1);
            MechatronicsVisualBlockEntity.push(server, pos, visualState(server, pos));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int command = commandedOpening(level, pos);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        int oldOpening = runtime[ACTUAL_OPENING];
        int oldDelta = runtime[LAST_DELTA];

        if (runtime[INITIALIZED] == 0) {
            runtime[INITIALIZED] = 1;
            runtime[ACTUAL_OPENING] = 0;
            oldOpening = 0;
            oldDelta = 0;
        }

        int nextOpening = PneumaticProportionalValveLogic.stepOpening(
                oldOpening, command, state.getValue(RESPONSE_MODE));
        int delta = nextOpening - oldOpening;

        runtime[ACTUAL_OPENING] = nextOpening;
        runtime[LAST_DELTA] = delta;
        if (delta != 0) {
            if (runtime[TRAVEL] < Integer.MAX_VALUE - Math.abs(delta)) runtime[TRAVEL] += Math.abs(delta);
            else runtime[TRAVEL] = Integer.MAX_VALUE;
            if (oldDelta != 0 && Integer.signum(oldDelta) != Integer.signum(delta)
                    && runtime[REVERSALS] < Integer.MAX_VALUE) {
                runtime[REVERSALS]++;
            }
        }

        if (nextOpening != oldOpening) {
            PneumaticNetwork.recompute(level, pos);
            MechatronicsVisualBlockEntity.push(level, pos, visualState(level, pos));
        }

        if (nextOpening != command) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            if (level instanceof ServerLevel server) {
                InformationRuntime.clear(level, "pneumatic", pos);
                PneumaticNetwork.recomputeAround(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Proportional valve | command=" + commandedOpening(level, pos) + "/15"
                                + " actual=" + actualOpening(level, pos) + "/15"
                                + " error=" + trackingError(level, pos)
                                + " | response=" + PneumaticProportionalValveLogic.modeName(state.getValue(RESPONSE_MODE))
                                + " rate=" + PneumaticProportionalValveLogic.responseRate(state.getValue(RESPONSE_MODE)) + "/t"
                                + " | travel=" + travel(level, pos)
                                + " reversals=" + reversals(level, pos)
                                + " | pressure=" + PneumaticNetwork.pressure(level, pos) + "/100"
                                + " | UP=command BACK→FRONT"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
