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
import dev.redstoneengineering.physics.DataBusNetwork;
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

/** Converts one 8-bit bus word into a framed serial payload. */
public class SerializerBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty PERIOD_MODE = IntegerProperty.create("period_mode", 0, 2);
    private static final int[] WORD_PERIODS = {4, 8, 16};
    /** Slow authority watchdog: normal source changes remain neighbor-driven, coverage loss is bounded. */
    private static final int WATCHDOG_TICKS = 16;

    public SerializerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PERIOD_MODE, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PERIOD_MODE);
    }

    public static int wordPeriod(BlockState state) {
        return WORD_PERIODS[Math.max(0, Math.min(WORD_PERIODS.length - 1, state.getValue(PERIOD_MODE)))];
    }

    public static boolean stepPeriod(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SerializerBlock serializer)) return false;
        int next = Math.floorMod(state.getValue(PERIOD_MODE) + (forward ? 1 : -1), WORD_PERIODS.length);
        BlockState updated = state.setValue(PERIOD_MODE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) serializer.update(server, pos, updated);
        return true;
    }

    @Override
    public MapCodec<SerializerBlock> codec() {
        return RedstoneEngineering.SERIALIZER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("BYTE IN", inputSide(state), EngineeringDomain.DATA_BUS_8,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "byte"),
                new EngineeringPort("SERIAL OUT", outputSide(state), EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "byte")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos input = inputPos(pos, state);
        PortQuality inputQuality = DataBusNetwork.quality(level, input);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(port.get(), DataBusNetwork.sample(level, input),
                    0.0, 255.0, inputQuality));
        }
        InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "serial", pos);
        PortQuality quality = output.ageTicks() < 0 ? PortQuality.STALE : inputQuality;
        return Optional.of(new EngineeringPortSnapshot(port.get(),
                output.value() & 0xFF,
                0.0, 255.0, quality));
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        PortQuality inputQuality = DataBusNetwork.quality(level, input);
        boolean valid = inputQuality == PortQuality.VALID;
        InformationRuntime.Snapshot previous = InformationRuntime.snapshot(level, "serial", pos);
        int value = valid ? DataBusNetwork.sample(level, input)
                : inputQuality == PortQuality.STALE ? previous.value() & 0xFF : 0;
        InformationRuntime.write(level, "serial", pos, value, wordPeriod(state), valid, valid ? 100 : 0);
        if (level.getBlockState(output).getBlock() instanceof SerialDataLineBlock) {
            SerialNetwork.recompute(level, output);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            update(serverLevel, pos, state);
            serverLevel.scheduleTick(pos, this, WATCHDOG_TICKS);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        // Normal loaded-topology changes are event-driven; the watchdog only closes coverage/lifecycle gaps.
        if (level instanceof ServerLevel serverLevel && neighborPos.equals(inputPos(pos, state))) {
            update(serverLevel, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, WATCHDOG_TICKS);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "serial", pos);
                PortQuality inputQuality = DataBusNetwork.quality(level, inputPos(pos, state));
                player.displayClientMessage(Component.literal(
                        "Serializer framed byte=" + (output.value() & 0xFF)
                                + " @ " + wordPeriod(state) + "t/word"
                                + " | inputQuality=" + inputQuality
                                + " | sourceValid=" + output.valid()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
