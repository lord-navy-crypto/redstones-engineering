package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PwmCarrierLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class PwmControllerBlock extends DirectionalSignalBlock {
    public static final int MIN_LEGACY_PERIOD_MODE = 0;
    public static final int MAX_LEGACY_PERIOD_MODE = 3;
    public static final int DEFAULT_LEGACY_PERIOD_MODE = 2;
    public static final int LEGACY_PERIOD_FAST = 4;
    public static final int LEGACY_PERIOD_MEDIUM = 8;
    public static final int LEGACY_PERIOD_DEFAULT = 16;
    public static final int LEGACY_PERIOD_SLOW = 32;
    public static final int CARRIER_TICK_TICKS = 1;
    public static final IntegerProperty PERIOD_MODE = IntegerProperty.create(
            "period_mode", MIN_LEGACY_PERIOD_MODE, MAX_LEGACY_PERIOD_MODE);
    public static final BooleanProperty INVERT = BooleanProperty.create("invert");
    private static final String KEY = "redstone_pwm";
    private static final int PHASE_SLOT = 0;
    private static final int LATCHED_COMMAND_SLOT = 1;
    private static final int INITIALIZED_SLOT = 2;
    private static final int CYCLE_COUNT_SLOT = 3;
    private static final int RUNTIME_SIZE = 4;

    public record PwmAssessment(int command, int appliedCommand, int periodTicks, int onTicks, int phase,
                                int requestedDutyPermille, int effectiveDutyPermille,
                                int quantizationErrorPermille, boolean pendingUpdate, int completedCycles,
                                boolean inhibited, boolean inverted) {}

    public PwmControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(PERIOD_MODE, DEFAULT_LEGACY_PERIOD_MODE)
                .setValue(INVERT, false));
    }
    @Override public MapCodec<PwmControllerBlock> codec() { return RedstoneEngineering.PWM_CONTROLLER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(PERIOD_MODE, INVERT);
    }
    @Override protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state) || side == outputSide(state) || side == leftOf(facing);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(
                new EngineeringPort("COMMAND IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("PWM OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_BINARY, PortDirection.OUTPUT, true, "signal"),
                new EngineeringPort("INHIBIT", leftOf(facing), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "signal")
        );
    }

    private static boolean inhibitEvidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    public static PortQuality commandQuality(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PwmControllerBlock pwm)) return PortQuality.NO_SIGNAL;
        return RedstoneObservationSupport.observe(level, pos, pwm.inputSide(state)).quality();
    }

    public static PortQuality inhibitQuality(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PwmControllerBlock)) return PortQuality.NO_SIGNAL;
        return RedstoneObservationSupport.observe(level, pos, leftOf(state.getValue(FACING))).quality();
    }

    public static PortQuality outputQuality(Level level, BlockPos pos, BlockState state) {
        PortQuality commandQuality = commandQuality(level, pos, state);
        PortQuality inhibitQuality = inhibitQuality(level, pos, state);
        return inhibitEvidenceUnusable(inhibitQuality)
                ? RedstoneObservationSupport.combineQuality(commandQuality, inhibitQuality)
                : commandQuality;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        Direction inhibitSide = leftOf(state.getValue(FACING));
        var command = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        var inhibit = RedstoneObservationSupport.observe(level, pos, inhibitSide);

        if (side == inputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), command.value(), command.quality()));
        }
        if (side == inhibitSide) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), inhibit.value(), inhibit.quality()));
        }
        if (side == outputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), outputQuality(level, pos, state)));
        }
        return Optional.empty();
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction inhibitSide = leftOf(state.getValue(FACING));
        var command = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        var inhibit = RedstoneObservationSupport.observe(level, pos, inhibitSide);

        boolean commandUsable = command.valid();
        boolean inhibited = (inhibit.valid() && inhibit.value() > 0)
                || inhibitEvidenceUnusable(inhibit.quality());
        int input = commandUsable ? command.value() : 0;
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        if (!commandUsable || inhibited) {
            resetCarrier(rt);
            updateOutput(level, pos, state, 0);
            return;
        }

        int period = configuredPeriod(level, pos, state);
        PwmCarrierLogic.Result carrier = PwmCarrierLogic.step(
                input,
                period,
                new PwmCarrierLogic.State(
                        rt[INITIALIZED_SLOT] != 0,
                        rt[PHASE_SLOT],
                        rt[LATCHED_COMMAND_SLOT],
                        rt[CYCLE_COUNT_SLOT]
                )
        );
        writeCarrier(rt, carrier.state());

        int output = carrier.outputHigh() ? 15 : 0;
        if (state.getValue(INVERT)) output = output > 0 ? 0 : 15;
        updateOutput(level, pos, state, output);

        if (input > PwmCarrierLogic.MIN_COMMAND && input < PwmCarrierLogic.MAX_COMMAND) {
            level.scheduleTick(pos, this, CARRIER_TICK_TICKS);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static int quantizedOnTicks(int command, int periodTicks) {
        return PwmCarrierLogic.quantizedOnTicks(command, periodTicks);
    }

    public static int requestedDutyPermille(int command) {
        return (int) Math.round(PwmCarrierLogic.boundedCommand(command)
                * (1000.0 / PwmCarrierLogic.MAX_COMMAND));
    }

    public static int effectiveDutyPermille(int command, int periodTicks) {
        int period = PwmCarrierLogic.boundedConfiguredPeriod(periodTicks);
        return (int) Math.round(quantizedOnTicks(command, period) * (1000.0 / period));
    }

    public static int phase(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[PHASE_SLOT]);
    }

    public static int appliedCommand(Level level, BlockPos pos, int fallback) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE || rt[INITIALIZED_SLOT] == 0
                ? PwmCarrierLogic.boundedCommand(fallback)
                : PwmCarrierLogic.boundedCommand(rt[LATCHED_COMMAND_SLOT]);
    }

    public static int completedCycles(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[CYCLE_COUNT_SLOT]);
    }

    private static void writeCarrier(int[] rt, PwmCarrierLogic.State state) {
        rt[PHASE_SLOT] = state.phase();
        rt[LATCHED_COMMAND_SLOT] = state.latchedCommand();
        rt[INITIALIZED_SLOT] = state.initialized() ? 1 : 0;
        rt[CYCLE_COUNT_SLOT] = state.completedCycles();
    }

    private static void resetCarrier(int[] rt) {
        java.util.Arrays.fill(rt, 0);
    }

    public PwmAssessment assessment(Level level, BlockPos pos, BlockState state) {
        var commandObservation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        var inhibitObservation = RedstoneObservationSupport.observe(
                level, pos, leftOf(state.getValue(FACING)));
        int command = commandObservation.valid() ? commandObservation.value() : 0;
        int period = configuredPeriod(level, pos, state);
        int applied = appliedCommand(level, pos, command);
        int requested = requestedDutyPermille(command);
        int effective = effectiveDutyPermille(applied, period);
        boolean inhibited = (inhibitObservation.valid() && inhibitObservation.value() > 0)
                || inhibitEvidenceUnusable(inhibitObservation.quality());
        boolean pending = commandObservation.valid() && command > 0 && command < 15 && command != applied;
        return new PwmAssessment(command, applied, period, quantizedOnTicks(applied, period), phase(level, pos),
                requested, effective, effective - requested, pending, completedCycles(level, pos),
                inhibited, state.getValue(INVERT));
    }

    public boolean adjustPeriodMode(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int span = MAX_LEGACY_PERIOD_MODE - MIN_LEGACY_PERIOD_MODE + 1;
        int mode = MIN_LEGACY_PERIOD_MODE + Math.floorMod(
                state.getValue(PERIOD_MODE) - MIN_LEGACY_PERIOD_MODE + delta, span);
        BlockState next = state.setValue(PERIOD_MODE, mode);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) {
            EngineeringDeviceParameters.get(serverLevel).setExtendedParameters(
                    serverLevel, pos,
                    new EngineeringDeviceParameters.ExtendedParameters(periodFor(mode), 0, 0, 0));
        }
        resetCarrier(RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE));
        level.scheduleTick(pos, this, CARRIER_TICK_TICKS);
        return true;
    }

    public boolean toggleInvert(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        BlockState next = state.setValue(INVERT, !state.getValue(INVERT));
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        resetCarrier(RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE));
        level.scheduleTick(pos, this, CARRIER_TICK_TICKS);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                toggleInvert(level, pos);
                PwmAssessment a = assessment(level, pos, level.getBlockState(pos));
                player.displayClientMessage(Component.literal(
                        "PWM | invert=" + a.inverted() + " | period=" + a.periodTicks() + "t"
                                + " | command=" + a.command() + " applied=" + a.appliedCommand()
                                + (a.pendingUpdate() ? " (NEXT CYCLE)" : "")
                                + " | requested=" + a.requestedDutyPermille()/10.0 + "%"
                                + " | realized=" + a.effectiveDutyPermille()/10.0 + "%"
                                + " | cycles=" + a.completedCycles()
                                + " | inhibit=" + (a.inhibited() ? "ACTIVE/FAIL-SAFE" : "CLEAR")), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static int periodFor(int mode) {
        return switch (mode) {
            case MIN_LEGACY_PERIOD_MODE -> LEGACY_PERIOD_FAST;
            case 1 -> LEGACY_PERIOD_MEDIUM;
            case DEFAULT_LEGACY_PERIOD_MODE -> LEGACY_PERIOD_DEFAULT;
            case MAX_LEGACY_PERIOD_MODE -> LEGACY_PERIOD_SLOW;
            default -> LEGACY_PERIOD_DEFAULT;
        };
    }

    public static int configuredPeriod(Level level, BlockPos pos, BlockState state) {
        int fallback = periodFor(state.getValue(PERIOD_MODE));
        if (level instanceof ServerLevel serverLevel) {
            return PwmCarrierLogic.boundedConfiguredPeriod(
                    EngineeringDeviceParameters.get(serverLevel)
                            .extendedParameters(serverLevel, pos,
                                    new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0))
                            .a());
        }
        return fallback;
    }

    public static boolean setConfiguredPeriod(ServerLevel level, BlockPos pos, int period) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PwmControllerBlock pwm)) return false;
        int bounded = PwmCarrierLogic.boundedConfiguredPeriod(period);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) {
            resetCarrier(RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE));
            level.scheduleTick(pos, pwm, CARRIER_TICK_TICKS);
        }
        return changed;
    }
}
