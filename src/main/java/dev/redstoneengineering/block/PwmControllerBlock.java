package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
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
    public static final IntegerProperty PERIOD_MODE = IntegerProperty.create("period_mode", 0, 3);
    public static final BooleanProperty INVERT = BooleanProperty.create("invert");
    private static final String KEY = "redstone_pwm";

    public record PwmAssessment(int command, int periodTicks, int onTicks, int phase,
                                int requestedDutyPermille, int effectiveDutyPermille,
                                int quantizationErrorPermille, boolean inhibited, boolean inverted) {}

    public PwmControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PERIOD_MODE, 2).setValue(INVERT, false));
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
            PortQuality quality = inhibitEvidenceUnusable(inhibit.quality())
                    ? RedstoneObservationSupport.combineQuality(command.quality(), inhibit.quality())
                    : command.quality();
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), quality));
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

        int period = periodFor(state.getValue(PERIOD_MODE));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 1);
        int phase = Math.floorMod(rt[0], period);
        int onTicks = quantizedOnTicks(input, period);
        int output = phase < onTicks ? 15 : 0;
        if (input <= 0) output = 0;
        if (input >= 15) output = 15;
        if (state.getValue(INVERT)) output = output > 0 ? 0 : 15;
        // Invalid command evidence and unusable safety evidence both fail to a de-energized output.
        if (!commandUsable) output = 0;
        if (inhibited) output = 0;

        updateOutput(level, pos, state, output);
        if (commandUsable && !inhibited && input > 0 && input < 15) {
            rt[0] = (phase + 1) % period;
            level.scheduleTick(pos, this, 1);
        } else {
            rt[0] = 0;
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    public static int quantizedOnTicks(int command, int periodTicks) {
        int boundedCommand = Math.max(0, Math.min(15, command));
        int boundedPeriod = Math.max(1, periodTicks);
        return Math.max(0, Math.min(boundedPeriod,
                (int) Math.round((boundedCommand / 15.0) * boundedPeriod)));
    }

    public static int requestedDutyPermille(int command) {
        return (int) Math.round(Math.max(0, Math.min(15, command)) * (1000.0 / 15.0));
    }

    public static int effectiveDutyPermille(int command, int periodTicks) {
        int period = Math.max(1, periodTicks);
        return (int) Math.round(quantizedOnTicks(command, period) * (1000.0 / period));
    }

    public static int phase(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != 1 ? 0 : rt[0];
    }

    public PwmAssessment assessment(Level level, BlockPos pos, BlockState state) {
        var commandObservation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        var inhibitObservation = RedstoneObservationSupport.observe(
                level, pos, leftOf(state.getValue(FACING)));
        int command = commandObservation.valid() ? commandObservation.value() : 0;
        int period = periodFor(state.getValue(PERIOD_MODE));
        int requested = requestedDutyPermille(command);
        int effective = effectiveDutyPermille(command, period);
        boolean inhibited = (inhibitObservation.valid() && inhibitObservation.value() > 0)
                || inhibitEvidenceUnusable(inhibitObservation.quality());
        return new PwmAssessment(command, period, quantizedOnTicks(command, period), phase(level, pos),
                requested, effective, effective - requested, inhibited, state.getValue(INVERT));
    }

    public boolean adjustPeriodMode(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int mode = Math.floorMod(state.getValue(PERIOD_MODE) + delta, 4);
        BlockState next = state.setValue(PERIOD_MODE, mode);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = 0;
        level.scheduleTick(pos, this, 1);
        return true;
    }

    public boolean toggleInvert(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        BlockState next = state.setValue(INVERT, !state.getValue(INVERT));
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = 0;
        level.scheduleTick(pos, this, 1);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                toggleInvert(level, pos);
                PwmAssessment a = assessment(level, pos, level.getBlockState(pos));
                player.displayClientMessage(Component.literal(
                        "PWM | invert=" + a.inverted() + " | period=" + a.periodTicks() + "t"
                                + " | requested=" + a.requestedDutyPermille()/10.0 + "%"
                                + " | realized=" + a.effectiveDutyPermille()/10.0 + "%"
                                + " | inhibit=" + (a.inhibited() ? "ACTIVE/FAIL-SAFE" : "CLEAR")), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static int periodFor(int mode) {
        return switch (mode) { case 0 -> 4; case 1 -> 8; case 2 -> 16; case 3 -> 32; default -> 16; };
    }
}
