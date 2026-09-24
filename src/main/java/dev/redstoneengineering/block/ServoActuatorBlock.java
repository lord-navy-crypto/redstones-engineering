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
import dev.redstoneengineering.operations.world.OperationWorldResourceProvider;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mechatronic servo primitive with explicit control ports.
 * BACK=command, UP=mode (0=POSITION, >0=VELOCITY), RIGHT=BRAKE, FRONT=mechanical position.
 * In velocity mode command 7=stop, 0..6 reverse, 8..15 forward.
 */
public class ServoActuatorBlock extends Block implements EntityBlock, EngineeringPortProvider, OperationWorldResourceProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SLEW = IntegerProperty.create("slew", 0, 2);
    /** Lumped mechanical load/inertia profile: 0=unloaded, 1=light, 2=medium, 3=heavy. */
    public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 3);

    private static final int POSITION_MODE = 0;
    private static final int VELOCITY_MODE = 1;
    private static final int[] STEP = {1, 2, 3};
    private static final int[] LOAD_ACCEL_PERIOD = {1, 1, 2, 3};
    private static final int[] LOAD_SPEED_PENALTY = {0, 0, 1, 1};
    private static final String KEY = "servo";
    private static final int RUNTIME_SIZE = 21;
    private static final int ACCEL_PHASE_SLOT = 16;
    private static final int LOAD_DELAY_TICKS_SLOT = 17;
    private static final int REVERSALS_SLOT = 18;
    private static final int LAST_VELOCITY_SLOT = 19;
    private static final int MOTION_SAMPLES_SLOT = 20;

    public ServoActuatorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SLEW, 0)
                .setValue(LOAD, 1));
    }

    @Override public MapCodec<ServoActuatorBlock> codec() { return RedstoneEngineering.SERVO_ACTUATOR_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, SLEW, LOAD); }
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

    /** Observer-only electrical evidence for the selected physical control face. */
    public static RedstoneObservationSupport.Observation controlObservation(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = state.getValue(FACING);
        if (side == front) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), position(level, pos), 0.0, 15.0, outputQuality(level, pos)));
        }
        RedstoneObservationSupport.Observation observation = controlObservation(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        boolean evaluated = runtime != null && runtime.length >= RUNTIME_SIZE;
        boolean brake = braking(level, pos);
        int velocity = velocity(level, pos);
        boolean completionEvidenceAvailable = false;
        return new OperationWorldResourceSnapshot(
                "servo_actuator:" + pos.asLong(),
                Set.of("servo_positioning"),
                evaluated && !brake,
                evaluated && velocity != 0,
                completionEvidenceAvailable,
                false,
                evaluated ? PortQuality.VALID : PortQuality.STALE,
                Map.of(
                        "position", (long) position(level, pos),
                        "command", (long) command(level, pos),
                        "velocity", (long) velocity,
                        "error", (long) error(level, pos),
                        "braking", brake ? 1L : 0L,
                        "soft_limit_hits", (long) softLimitHits(level, pos),
                        "load_profile", (long) state.getValue(LOAD),
                        "load_delay_ticks", (long) loadDelayTicks(level, pos),
                        "motion_samples", (long) motionSamples(level, pos),
                        "reversals", (long) reversals(level, pos)
                )
        );
    }

    @Override
    public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, @Nullable Direction d) {
        if (d == null) return false;
        Direction front = s.getValue(FACING);
        Direction physical = d.getOpposite();
        return physical == front.getOpposite() || physical == rightOf(front) || physical == Direction.UP;
    }

    private static int approach(int value, int target, int step) { if (value < target) return Math.min(target, value + step); if (value > target) return Math.max(target, value - step); return value; }
    private static int clamp(int value, int lo, int hi) { return Math.max(lo, Math.min(hi, value)); }

    public static int slewStep(int index) { return STEP[Math.max(0, Math.min(STEP.length - 1, index))]; }
    public static int loadIndex(BlockState state) { return Math.max(0, Math.min(3, state.getValue(LOAD))); }
    public static String loadName(BlockState state) {
        return switch (loadIndex(state)) {
            case 0 -> "UNLOADED";
            case 1 -> "LIGHT";
            case 2 -> "MEDIUM";
            default -> "HEAVY";
        };
    }
    public static EngineeringDeviceParameters.ServoParameters presetParameters(BlockState state) {
        int load = loadIndex(state);
        int base = STEP[Math.max(0, Math.min(STEP.length - 1, state.getValue(SLEW)))];
        int maxSpeed = Math.max(1, base - LOAD_SPEED_PENALTY[load]);
        return new EngineeringDeviceParameters.ServoParameters(maxSpeed, LOAD_ACCEL_PERIOD[load], 1);
    }

    public static EngineeringDeviceParameters.ServoParameters configuredParameters(Level level, BlockPos pos, BlockState state) {
        EngineeringDeviceParameters.ServoParameters fallback = presetParameters(state);
        if (level instanceof ServerLevel serverLevel) {
            return EngineeringDeviceParameters.get(serverLevel).servoParameters(serverLevel, pos, fallback);
        }
        return fallback;
    }

    public static int accelerationPeriod(BlockState state) { return presetParameters(state).accelerationPeriod(); }
    public static int effectiveMaxSpeed(BlockState state) { return presetParameters(state).maxSpeed(); }
    public static int accelerationPeriod(Level level, BlockPos pos, BlockState state) { return configuredParameters(level, pos, state).accelerationPeriod(); }
    public static int effectiveMaxSpeed(Level level, BlockPos pos, BlockState state) { return configuredParameters(level, pos, state).maxSpeed(); }
    public static int accelerationStep(Level level, BlockPos pos, BlockState state) { return configuredParameters(level, pos, state).accelerationStep(); }

    public static boolean adjustParameter(ServerLevel level, BlockPos pos, int parameter, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ServoActuatorBlock)) return false;
        EngineeringDeviceParameters.ServoParameters current = configuredParameters(level, pos, state);
        EngineeringDeviceParameters.ServoParameters next = switch (parameter) {
            case 0 -> new EngineeringDeviceParameters.ServoParameters(current.maxSpeed() + delta, current.accelerationPeriod(), current.accelerationStep());
            case 1 -> new EngineeringDeviceParameters.ServoParameters(current.maxSpeed(), current.accelerationPeriod() + delta, current.accelerationStep());
            case 2 -> new EngineeringDeviceParameters.ServoParameters(current.maxSpeed(), current.accelerationPeriod(), current.accelerationStep() + delta);
            default -> current;
        };
        boolean changed = EngineeringDeviceParameters.get(level).setServoParameters(level, pos, next);
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    public static boolean loadPreset(ServerLevel level, BlockPos pos, int loadPreset) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ServoActuatorBlock)) return false;
        int bounded = Math.max(0, Math.min(3, loadPreset));
        BlockState nextState = state.setValue(LOAD, bounded);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        boolean changed = EngineeringDeviceParameters.get(level).setServoParameters(level, pos, presetParameters(nextState));
        level.scheduleTick(pos, nextState.getBlock(), 1);
        return changed || bounded != state.getValue(LOAD);
    }
    private static int[] readRuntime(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE ? runtime : null;
    }

    public static int position(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:r[0]; }
    public static int command(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:r[1]; }
    public static int velocity(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:r[2]; }
    public static int error(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:r[3]; }
    public static boolean braking(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r!=null&&r[4]!=0; }
    public static int mode(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?POSITION_MODE:Math.max(POSITION_MODE,Math.min(VELOCITY_MODE,r[13])); }
    public static int velocityCommand(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:r[14]; }
    public static int maxObservedVelocity(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[10]); }
    public static int settleTicks(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[11]); }
    public static int travel(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[12]); }
    public static int softLimitHits(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[15]); }
    public static int loadDelayTicks(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[LOAD_DELAY_TICKS_SLOT]); }
    public static int reversals(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[REVERSALS_SLOT]); }
    public static int motionSamples(Level level, BlockPos pos) { int[] r=readRuntime(level,pos); return r==null?0:Math.max(0,r[MOTION_SAMPLES_SLOT]); }

    public static PortQuality commandQuality(Level level, BlockPos pos, BlockState state) {
        return controlObservation(level, pos, state.getValue(FACING).getOpposite()).quality();
    }

    public static PortQuality modeQuality(Level level, BlockPos pos) {
        return controlObservation(level, pos, Direction.UP).quality();
    }

    public static PortQuality brakeQuality(Level level, BlockPos pos, BlockState state) {
        return controlObservation(level, pos, rightOf(state.getValue(FACING))).quality();
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        return readRuntime(level, pos) == null ? PortQuality.STALE : PortQuality.VALID;
    }

    /** Shared server-state text for expert diagnostics and UI regression compatibility. */
    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] r = RuntimeIntStore.peek(level, KEY, pos);
        if (r == null || r.length < RUNTIME_SIZE) {
            return "Servo trajectory diagnostics | pos=0 command=0 velocity=0 error=0 settle=0t travel=0";
        }
        return "Servo trajectory diagnostics | pos=" + r[0]
                + " command=" + r[1]
                + " velocity=" + r[2]
                + " error=" + r[3]
                + " settle=" + r[11] + "t"
                + " travel=" + r[12]
                + " loadDelay=" + r[LOAD_DELAY_TICKS_SLOT] + "t"
                + " reversals=" + r[REVERSALS_SLOT]
                + " motionSamples=" + r[MOTION_SAMPLES_SLOT]
                + " softLimitHits=" + r[15];
    }

    /** Renderer-facing immutable projection; never creates or mutates simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return MechatronicsVisualState.servo(0, 0, false, STEP[state.getValue(SLEW)]);
        return MechatronicsVisualState.servo(runtime[0], runtime[2], runtime[4] != 0, STEP[state.getValue(SLEW)]);
    }

    public boolean homeAndReset(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        RuntimeIntStore.remove(level, KEY, pos);
        MechatronicsVisualBlockEntity.push(level, pos, visualState(level, pos, state));
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 2);
        return true;
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) { super.onPlace(s, l, p, o, m); if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 2); }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource rnd) {
        int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        Direction front = s.getValue(FACING);
        Direction back = front.getOpposite();
        Direction right = rightOf(front);

        RedstoneObservationSupport.Observation commandInput = controlObservation(l, p, back);
        RedstoneObservationSupport.Observation modeInput = controlObservation(l, p, Direction.UP);
        RedstoneObservationSupport.Observation brakeInput = controlObservation(l, p, right);

        boolean commandAvailable = commandInput.valid();
        int command = commandInput.value();
        int effectiveCommand = commandAvailable ? command : r[0];
        int mode = commandAvailable && modeInput.valid() && modeInput.value() > 0
                ? VELOCITY_MODE : POSITION_MODE;
        boolean brake = !commandAvailable || (brakeInput.valid() && brakeInput.value() > 0);
        int now = (int) Math.min(Integer.MAX_VALUE, l.getGameTime());

        if (command != r[1] || mode != r[13]) { r[6]++; r[7] = now; r[8] = r[0]; r[9] = effectiveCommand; r[10] = 0; r[11] = 0; }
        r[1] = command; r[4] = brake ? 1 : 0; r[13] = mode;

        int oldPosition = r[0];
        int maxSpeed = effectiveMaxSpeed(l, p, s);
        int accelPeriod = accelerationPeriod(l, p, s);
        int accelStep = accelerationStep(l, p, s);
        int appliedVelocity = r[2];
        int velocityCommand = 0;
        int desiredVelocity = 0;
        if (mode == VELOCITY_MODE) {
            velocityCommand = effectiveCommand - 7;
            desiredVelocity = clamp(velocityCommand, -maxSpeed, maxSpeed);
        } else {
            int positionError = effectiveCommand - r[0];
            desiredVelocity = clamp(positionError, -maxSpeed, maxSpeed);
        }

        if (brake) {
            appliedVelocity = 0;
            r[ACCEL_PHASE_SLOT] = 0;
        } else {
            r[ACCEL_PHASE_SLOT]++;
            boolean accelerationUpdate = r[ACCEL_PHASE_SLOT] >= accelPeriod;
            if (accelerationUpdate) {
                r[ACCEL_PHASE_SLOT] = 0;
                appliedVelocity = approach(appliedVelocity, desiredVelocity, accelStep);
            } else if (appliedVelocity != desiredVelocity) {
                if (r[LOAD_DELAY_TICKS_SLOT] < Integer.MAX_VALUE) r[LOAD_DELAY_TICKS_SLOT]++;
            }
            if (mode == POSITION_MODE) {
                int positionError = effectiveCommand - r[0];
                if (Math.abs(appliedVelocity) > Math.abs(positionError)) appliedVelocity = positionError;
            }
        }

        int candidatePosition = r[0] + appliedVelocity;
        int limitedPosition = clamp(candidatePosition, 0, 15);
        if (candidatePosition != limitedPosition) { r[15]++; appliedVelocity = 0; }
        r[0] = limitedPosition;
        r[2] = appliedVelocity;
        r[14] = velocityCommand;
        r[3] = !commandAvailable ? 0
                : mode == POSITION_MODE ? effectiveCommand - r[0] : velocityCommand - appliedVelocity;
        r[12] += Math.abs(r[0] - oldPosition);
        if (r[0] != oldPosition && r[MOTION_SAMPLES_SLOT] < Integer.MAX_VALUE) r[MOTION_SAMPLES_SLOT]++;
        int previousVelocity = r[LAST_VELOCITY_SLOT];
        if (previousVelocity != 0 && appliedVelocity != 0
                && Integer.signum(previousVelocity) != Integer.signum(appliedVelocity)
                && r[REVERSALS_SLOT] < Integer.MAX_VALUE) {
            r[REVERSALS_SLOT]++;
        }
        r[LAST_VELOCITY_SLOT] = appliedVelocity;
        r[10] = Math.max(r[10], Math.abs(appliedVelocity));
        if (mode == POSITION_MODE && r[3] == 0 && oldPosition != r[0]) r[11] = Math.max(1, now - r[7]);
        if (!brake && r[3] != 0 && r[0] == oldPosition && appliedVelocity == 0) r[5]++;

        MechatronicsVisualBlockEntity.push(l, p, visualState(l, p, s));
        l.updateNeighborsAt(p, this);
        l.scheduleTick(p, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeServoParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                if (h.getDirection() == Direction.UP) {
                    int nextLoad = (s.getValue(LOAD) + 1) % 4;
                    if (l instanceof ServerLevel server) loadPreset(server, p, nextLoad);
                    BlockState next = l.getBlockState(p);
                    var parameters = configuredParameters(l, p, next);
                    pl.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Servo mechanical preset=" + loadName(next)
                                    + " | accelPeriod=" + parameters.accelerationPeriod() + "t"
                                    + " | accelStep=" + parameters.accelerationStep()
                                    + " | maxSpeed=" + parameters.maxSpeed()
                                    + " | Shift-click TOP cycles preset; Shift-click another face homes/resets"), true);
                } else {
                    homeAndReset(l, p);
                    pl.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Servo homed; trajectory diagnostics reset"
                                    + " | load=" + loadName(l.getBlockState(p))
                                    + " | Shift-click TOP cycles mechanical load"), true);
                }
            } else {
                FieldDeviceUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
