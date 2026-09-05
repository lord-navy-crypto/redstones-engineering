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
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Mechatronic servo primitive with explicit control ports.
 * BACK=command, UP=mode (0=POSITION, >0=VELOCITY), RIGHT=BRAKE, FRONT=mechanical position.
 * In velocity mode command 7=stop, 0..6 reverse, 8..15 forward.
 */
public class ServoActuatorBlock extends Block implements EntityBlock, EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SLEW = IntegerProperty.create("slew", 0, 2);

    private static final int POSITION_MODE = 0;
    private static final int VELOCITY_MODE = 1;
    private static final int[] STEP = {1, 2, 3};
    private static final String KEY = "servo";
    private static final int RUNTIME_SIZE = 16;

    public ServoActuatorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SLEW, 0));
    }

    @Override public MapCodec<ServoActuatorBlock> codec() { return RedstoneEngineering.SERVO_ACTUATOR_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, SLEW); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MechatronicsVisualBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    public static Direction rightOf(Direction front) {
        return switch (front) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = state.getValue(FACING);
        return List.of(
                new EngineeringPort("COMMAND IN", front.getOpposite(), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "command"),
                new EngineeringPort("MODE SELECT", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "mode"),
                new EngineeringPort("BRAKE", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "brake"),
                new EngineeringPort("POSITION OUT", front, EngineeringDomain.MECHATRONIC_POSITION,
                        PortKind.ACTUATOR, PortDirection.OUTPUT, false, "position")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = state.getValue(FACING);
        if (side == front) {
            return Optional.of(new EngineeringPortSnapshot(port.get(), position(level, pos), 0.0, 15.0, PortQuality.VALID));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), read(level, pos, side), PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, @Nullable Direction d) {
        if (d == null) return false;
        Direction front = s.getValue(FACING);
        Direction physical = d.getOpposite();
        return physical == front.getOpposite() || physical == rightOf(front) || physical == Direction.UP;
    }

    private static int read(Level l, BlockPos p, Direction d) { return clamp(l.getSignal(p.relative(d), d), 0, 15); }
    private static int approach(int value, int target, int step) { if (value < target) return Math.min(target, value + step); if (value > target) return Math.max(target, value - step); return value; }
    private static int clamp(int value, int lo, int hi) { return Math.max(lo, Math.min(hi, value)); }

    public static int slewStep(int index) { return STEP[Math.max(0, Math.min(STEP.length - 1, index))]; }
    public static int position(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null||r.length<1?0:r[0]; }
    public static int command(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null||r.length<2?0:r[1]; }
    public static int velocity(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null||r.length<3?0:r[2]; }
    public static int error(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null||r.length<4?0:r[3]; }
    public static boolean braking(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r!=null&&r.length>4&&r[4]!=0; }
    public static int softLimitHits(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null||r.length<16?0:r[15]; }

    /** Renderer-facing immutable projection; never creates or mutates simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return MechatronicsVisualState.servo(0, 0, false, STEP[state.getValue(SLEW)]);
        return MechatronicsVisualState.servo(runtime[0], runtime[2], runtime[4] != 0, STEP[state.getValue(SLEW)]);
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) { super.onPlace(s, l, p, o, m); if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 2); }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource rnd) {
        int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        Direction front = s.getValue(FACING);
        Direction back = front.getOpposite();
        Direction right = rightOf(front);
        int command = read(l, p, back);
        int mode = read(l, p, Direction.UP) > 0 ? VELOCITY_MODE : POSITION_MODE;
        boolean brake = read(l, p, right) > 0;
        int now = (int) Math.min(Integer.MAX_VALUE, l.getGameTime());

        if (command != r[1] || mode != r[13]) { r[6]++; r[7] = now; r[8] = r[0]; r[9] = command; r[10] = 0; r[11] = 0; }
        r[1] = command; r[4] = brake ? 1 : 0; r[13] = mode;

        int oldPosition = r[0];
        int maxSpeed = STEP[s.getValue(SLEW)];
        int appliedVelocity = r[2];
        int velocityCommand = 0;
        if (brake) appliedVelocity = 0;
        else if (mode == VELOCITY_MODE) {
            velocityCommand = command - 7;
            appliedVelocity = approach(appliedVelocity, clamp(velocityCommand, -maxSpeed, maxSpeed), 1);
        } else {
            int positionError = command - r[0];
            int desiredVelocity = clamp(positionError, -maxSpeed, maxSpeed);
            appliedVelocity = approach(appliedVelocity, desiredVelocity, 1);
            if (Math.abs(appliedVelocity) > Math.abs(positionError)) appliedVelocity = positionError;
        }

        int candidatePosition = r[0] + appliedVelocity;
        int limitedPosition = clamp(candidatePosition, 0, 15);
        if (candidatePosition != limitedPosition) { r[15]++; appliedVelocity = 0; }
        r[0] = limitedPosition;
        r[2] = appliedVelocity;
        r[14] = velocityCommand;
        r[3] = mode == POSITION_MODE ? command - r[0] : velocityCommand - appliedVelocity;
        r[12] += Math.abs(r[0] - oldPosition);
        r[10] = Math.max(r[10], Math.abs(appliedVelocity));
        if (mode == POSITION_MODE && r[3] == 0 && oldPosition != r[0]) r[11] = Math.max(1, now - r[7]);
        if (!brake && r[3] != 0 && r[0] == oldPosition && appliedVelocity == 0) r[5]++;

        MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, s));
        l.updateNeighborsAt(p, this);
        l.scheduleTick(p, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, s));
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Servo homed; trajectory diagnostics reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
