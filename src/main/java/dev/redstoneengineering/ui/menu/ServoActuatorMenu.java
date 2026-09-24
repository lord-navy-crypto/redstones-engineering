package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative engineering notebook model for the mechatronic servo actuator. */
public final class ServoActuatorMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_PRESET_PREVIOUS = 0;
    public static final int BUTTON_PRESET_NEXT = 1;
    public static final int BUTTON_MAX_SPEED_MINUS = 2;
    public static final int BUTTON_MAX_SPEED_PLUS = 3;
    public static final int BUTTON_ACCEL_PERIOD_MINUS = 4;
    public static final int BUTTON_ACCEL_PERIOD_PLUS = 5;
    public static final int BUTTON_ACCEL_STEP_MINUS = 6;
    public static final int BUTTON_ACCEL_STEP_PLUS = 7;
    public static final int BUTTON_HOME_RESET = 8;
    public static final int BUTTON_ROTATE_LEFT = 20;
    public static final int BUTTON_ROTATE_RIGHT = 21;

    private final DataSlot preset = trackedInt();
    private final DataSlot maxSpeed = trackedInt();
    private final DataSlot accelerationPeriod = trackedInt();
    private final DataSlot accelerationStep = trackedInt();
    private final DataSlot position = trackedInt();
    private final DataSlot command = trackedInt();
    private final DataSlot velocity = trackedInt();
    private final DataSlot error = trackedInt();
    private final DataSlot braking = trackedInt();
    private final DataSlot softLimitHits = trackedInt();
    private final DataSlot loadDelayTicks = trackedInt();
    private final DataSlot reversals = trackedInt();
    private final DataSlot motionSamples = trackedInt();
    private final DataSlot mode = trackedInt();
    private final DataSlot velocityCommand = trackedInt();
    private final DataSlot settleTicks = trackedInt();
    private final DataSlot travel = trackedInt();
    private final DataSlot maxObservedVelocity = trackedInt();
    private final DataSlot commandQuality = trackedInt();
    private final DataSlot modeQuality = trackedInt();
    private final DataSlot brakeQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot facing = trackedInt();

    public ServoActuatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public ServoActuatorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.SERVO_ACTUATOR.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof ServoActuatorBlock)) return;
        var parameters = ServoActuatorBlock.configuredParameters(level, blockPos, state);
        preset.set(ServoActuatorBlock.loadIndex(state));
        maxSpeed.set(parameters.maxSpeed());
        accelerationPeriod.set(parameters.accelerationPeriod());
        accelerationStep.set(parameters.accelerationStep());
        position.set(ServoActuatorBlock.position(level, blockPos));
        command.set(ServoActuatorBlock.command(level, blockPos));
        velocity.set(ServoActuatorBlock.velocity(level, blockPos));
        error.set(ServoActuatorBlock.error(level, blockPos));
        braking.set(ServoActuatorBlock.braking(level, blockPos) ? 1 : 0);
        softLimitHits.set(ServoActuatorBlock.softLimitHits(level, blockPos));
        loadDelayTicks.set(ServoActuatorBlock.loadDelayTicks(level, blockPos));
        reversals.set(ServoActuatorBlock.reversals(level, blockPos));
        motionSamples.set(ServoActuatorBlock.motionSamples(level, blockPos));
        mode.set(ServoActuatorBlock.mode(level, blockPos));
        velocityCommand.set(ServoActuatorBlock.velocityCommand(level, blockPos));
        settleTicks.set(ServoActuatorBlock.settleTicks(level, blockPos));
        travel.set(ServoActuatorBlock.travel(level, blockPos));
        maxObservedVelocity.set(ServoActuatorBlock.maxObservedVelocity(level, blockPos));
        commandQuality.set(ServoActuatorBlock.commandQuality(level, blockPos, state).ordinal());
        modeQuality.set(ServoActuatorBlock.modeQuality(level, blockPos).ordinal());
        brakeQuality.set(ServoActuatorBlock.brakeQuality(level, blockPos, state).ordinal());
        outputQuality.set(ServoActuatorBlock.outputQuality(level, blockPos).ordinal());
        facing.set(state.getValue(ServoActuatorBlock.FACING).ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player) || !(level instanceof ServerLevel serverLevel)) return false;
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof ServoActuatorBlock servo)) return false;

        boolean changed;
        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            changed = ServoActuatorBlock.rotateLayout(level, blockPos, id == BUTTON_ROTATE_RIGHT);
        } else {
            changed = switch (id) {
                case BUTTON_PRESET_PREVIOUS -> ServoActuatorBlock.loadPreset(serverLevel, blockPos, (preset.get() + 3) % 4);
                case BUTTON_PRESET_NEXT -> ServoActuatorBlock.loadPreset(serverLevel, blockPos, (preset.get() + 1) % 4);
                case BUTTON_MAX_SPEED_MINUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 0, -1);
                case BUTTON_MAX_SPEED_PLUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 0, 1);
                case BUTTON_ACCEL_PERIOD_MINUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 1, -1);
                case BUTTON_ACCEL_PERIOD_PLUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 1, 1);
                case BUTTON_ACCEL_STEP_MINUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 2, -1);
                case BUTTON_ACCEL_STEP_PLUS -> ServoActuatorBlock.adjustParameter(serverLevel, blockPos, 2, 1);
                case BUTTON_HOME_RESET -> servo.homeAndReset(level, blockPos);
                default -> false;
            };
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int preset() { return preset.get(); }
    public int maxSpeed() { return maxSpeed.get(); }
    public int accelerationPeriod() { return accelerationPeriod.get(); }
    public int accelerationStep() { return accelerationStep.get(); }
    public int position() { return position.get(); }
    public int command() { return command.get(); }
    public int velocity() { return velocity.get(); }
    public int error() { return error.get(); }
    public boolean braking() { return braking.get() != 0; }
    public int softLimitHits() { return softLimitHits.get(); }
    public int loadDelayTicks() { return loadDelayTicks.get(); }
    public int reversals() { return reversals.get(); }
    public int motionSamples() { return motionSamples.get(); }
    public int mode() { return mode.get(); }
    public boolean velocityMode() { return mode.get() != 0; }
    public int velocityCommand() { return velocityCommand.get(); }
    public int settleTicks() { return settleTicks.get(); }
    public int travel() { return travel.get(); }
    public int maxObservedVelocity() { return maxObservedVelocity.get(); }
    public PortQuality commandQuality() { return decodeQuality(commandQuality.get()); }
    public PortQuality modeQuality() { return decodeQuality(modeQuality.get()); }
    public PortQuality brakeQuality() { return decodeQuality(brakeQuality.get()); }
    public PortQuality outputQuality() { return decodeQuality(outputQuality.get()); }
    public Direction frontDirection() { return decodeDirection(facing.get(), Direction.NORTH); }
    public Direction commandDirection() { return frontDirection().getOpposite(); }
    public Direction brakeDirection() { return ServoActuatorBlock.rightOf(frontDirection()); }
    public Direction modeDirection() { return Direction.UP; }
    public Direction positionOutputDirection() { return frontDirection(); }

    private static Direction decodeDirection(int ordinal, Direction fallback) {
        Direction[] values = Direction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static PortQuality decodeQuality(int ordinal) {
        PortQuality[] values = PortQuality.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.NO_SIGNAL;
    }
}
