package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Re-times/re-shapes a serial payload only when input quality clears a configurable decision threshold. */
public class DigitalRegeneratorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 0, 2);
    private static final int[] MIN_QUALITY = {20, 40, 60};

    public DigitalRegeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THRESHOLD, 1));
    }

    @Override
    public MapCodec<DigitalRegeneratorBlock> codec() {
        return RedstoneEngineering.DIGITAL_REGENERATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(THRESHOLD);
    }

    public static int minimumQuality(int thresholdIndex) {
        return MIN_QUALITY[Math.max(0, Math.min(MIN_QUALITY.length - 1, thresholdIndex))];
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "SERIAL IN",
                        inputSide(state),
                        EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "byte"
                ),
                new EngineeringPort(
                        "REGENERATED SERIAL OUT",
                        outputSide(state),
                        EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        false,
                        "byte"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            BlockPos input = inputPos(pos, state);
            boolean valid = InformationRuntime.valid(level, "serial", input);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    InformationRuntime.value(level, "serial", input) & 0xFF,
                    0.0,
                    255.0,
                    valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
            ));
        }
        boolean valid = InformationRuntime.valid(level, "serial", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                InformationRuntime.value(level, "serial", pos) & 0xFF,
                0.0,
                255.0,
                valid ? PortQuality.VALID : PortQuality.FAULT
        ));
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        int inputQuality = InformationRuntime.quality(level, "serial", input);
        boolean valid = InformationRuntime.valid(level, "serial", input)
                && inputQuality >= minimumQuality(state.getValue(THRESHOLD));
        int value = InformationRuntime.value(level, "serial", input) & 0xFF;
        int period = Math.max(1, InformationRuntime.aux(level, "serial", input));
        InformationRuntime.write(level, "serial", pos, value, period, valid, valid ? 100 : 0);
        if (level.getBlockState(output).getBlock() instanceof SerialDataLineBlock) {
            SerialNetwork.recompute(level, output);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (level instanceof ServerLevel serverLevel) update(serverLevel, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            InformationRuntime.clear(level, "serial", pos);
            BlockPos output = outputPos(pos, state);
            BlockState outputState = level.getBlockState(output);
            if (outputState.getBlock() instanceof SerialDataLineBlock line) {
                serverLevel.scheduleTick(output, line, 1);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int nextThreshold = (state.getValue(THRESHOLD) + 1) % 3;
                BlockState next = state.setValue(THRESHOLD, nextThreshold);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                update(serverPlayer.serverLevel(), pos, next);
                int inputQuality = InformationRuntime.quality(level, "serial", inputPos(pos, next));
                player.displayClientMessage(Component.literal(
                        "Digital regenerator minQuality=" + minimumQuality(nextThreshold) + "%"
                                + " inputQuality=" + inputQuality + "%"
                                + " output=" + (InformationRuntime.valid(level, "serial", pos) ? "VALID" : "REJECTED")
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
