package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Mechatronic servo primitive with explicit control ports.
 * BACK=command, UP=mode (0=POSITION, >0=VELOCITY), RIGHT=BRAKE.
 * In velocity mode command 7=stop, 0..6 reverse, 8..15 forward.
 */
public class ServoActuatorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SLEW = IntegerProperty.create("slew", 0, 2);

    private static final int POSITION_MODE = 0;
    private static final int VELOCITY_MODE = 1;
    private static final int[] STEP = {1, 2, 3};
    private static final String KEY = "servo";
    private static final String BRAKE = "RIGHT=BRAKE";
    private static final int RUNTIME_SIZE = 16;

    public ServoActuatorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SLEW, 0));
    }

    @Override
    public MapCodec<ServoActuatorBlock> codec() {
        return RedstoneEngineering.SERVO_ACTUATOR_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SLEW);
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
    public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, @Nullable Direction d) {
        if (d == null) return false;
        Direction front = s.getValue(FACING);
        Direction back = front.getOpposite();
        Direction right = rightOf(front);
        Direction physical = d.getOpposite();
        return physical == back || physical == right || physical == Direction.UP;
    }

    private static Direction rightOf(Direction front) {
        return switch (front) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private int read(Level l, BlockPos p, Direction d) {
        return clamp(l.getSignal(p.relative(d), d), 0, 15);
    }

    private static int approach(int value, int target, int step) {
        if (value < target) return Math.min(target, value + step);
        if (value > target) return Math.max(target, value - step);
        return value;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /** Renderer-facing immutable projection; never creates or mutates simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) {
            return MechatronicsVisualState.servo(0, 0, false, STEP[state.getValue(SLEW)]);
        }
        return MechatronicsVisualState.servo(
                runtime[0],
                runtime[2],
                runtime[4] != 0,
                STEP[state.getValue(SLEW)]
        );
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) {
        super.onPlace(s, l, p, o, m);
        if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 2);
    }

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

        if (command != r[1] || mode != r[13]) {
            r[6]++;
            r[7] = now;
            r[8] = r[0];
            r[9] = command;
            r[10] = 0;
            r[11] = 0;
        }
        r[1] = command;
        r[4] = brake ? 1 : 0;
        r[13] = mode;

        int oldPosition = r[0];
        int maxSpeed = STEP[s.getValue(SLEW)];
        int appliedVelocity = r[2];
        int velocityCommand = 0;

        if (brake) {
            appliedVelocity = 0;
        } else if (mode == VELOCITY_MODE) {
            velocityCommand = command - 7;
            int desiredVelocity = clamp(velocityCommand, -maxSpeed, maxSpeed);
            appliedVelocity = approach(appliedVelocity, desiredVelocity, 1);
        } else {
            int positionError = command - r[0];
            int desiredVelocity = clamp(positionError, -maxSpeed, maxSpeed);
            appliedVelocity = approach(appliedVelocity, desiredVelocity, 1);
            if (Math.abs(appliedVelocity) > Math.abs(positionError)) appliedVelocity = positionError;
        }

        int candidatePosition = r[0] + appliedVelocity;
        int limitedPosition = clamp(candidatePosition, 0, 15);
        int softLimitHits = r[15];
        if (candidatePosition != limitedPosition) {
            softLimitHits++;
            appliedVelocity = 0;
        }

        r[0] = limitedPosition;
        r[2] = appliedVelocity;
        r[14] = velocityCommand;
        r[15] = softLimitHits;
        r[3] = mode == POSITION_MODE ? command - r[0] : velocityCommand - appliedVelocity;
        r[12] += Math.abs(r[0] - oldPosition);
        r[10] = Math.max(r[10], Math.abs(appliedVelocity));

        if (mode == POSITION_MODE && r[3] == 0 && oldPosition != r[0]) {
            r[11] = Math.max(1, now - r[7]);
        }
        if (!brake && r[3] != 0 && r[0] == oldPosition && appliedVelocity == 0) r[5]++;

        MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, s));
        l.updateNeighborsAt(p, this);
        l.scheduleTick(p, this, 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, s));
                pl.displayClientMessage(Component.literal("Servo homed; trajectory diagnostics reset"), true);
            } else {
                int n = (s.getValue(SLEW) + 1) % 3;
                BlockState ns = s.setValue(SLEW, n);
                l.setBlock(p, ns, Block.UPDATE_CLIENTS);
                int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
                MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, ns));
                String mode = r[13] == VELOCITY_MODE ? "VELOCITY" : "POSITION";
                pl.displayClientMessage(Component.literal(
                        "Servo mode=" + mode
                                + " pos=" + r[0]
                                + " command=" + r[1]
                                + " velocityCommand=" + r[14]
                                + " appliedVelocity=" + r[2]
                                + " error=" + r[3]
                                + " slew=" + STEP[n] + "/2t"
                                + " brake=" + (r[4] != 0 ? "ON" : "OFF")
                                + " softLimitHits=" + r[15]
                                + " | trajectory commands=" + r[6]
                                + " settle=" + r[11] + "t vmax=" + r[10]
                                + " travel=" + r[12]
                                + " | UP=mode (0=POSITION >0=VELOCITY), BACK velocity uses 7=stop, " + BRAKE), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
