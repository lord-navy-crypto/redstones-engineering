package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Serial-to-parallel converter with bounded watchdog cleanup for stale/missing frames. */
public class DeserializerBlock extends DirectionalDomainBlock implements DataBusDriver {
    private static final int WATCHDOG_TICKS = 16;

    public DeserializerBlock(Properties properties) {
        super(properties);
    }

    @Override public MapCodec<DeserializerBlock> codec() { return RedstoneEngineering.DESERIALIZER_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SERIAL IN", inputSide(state), EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "byte"),
                new EngineeringPort("BYTE OUT", outputSide(state), EngineeringDomain.DATA_BUS_8,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "byte")
        );
    }

    private PortQuality inputQuality(Level level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        if (!level.hasChunkAt(input)) return PortQuality.STALE;
        if (!SerialNetwork.isNode(level, input)) return PortQuality.NO_SIGNAL;
        return SerialNetwork.quality(level, input);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos input = inputPos(pos, state);
        PortQuality inputQuality = inputQuality(level, pos, state);
        if (side == inputSide(state)) {
            InformationRuntime.Snapshot serial = InformationRuntime.snapshot(level, "serial", input);
            return Optional.of(new EngineeringPortSnapshot(port.get(),
                    serial.value() & 0xFF,
                    0.0, 255.0, inputQuality));
        }
        InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "bus8_out", pos);
        PortQuality quality = output.ageTicks() < 0 ? PortQuality.STALE : inputQuality;
        return Optional.of(new EngineeringPortSnapshot(port.get(),
                output.value() & 0xFF,
                0.0, 255.0, quality));
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        PortQuality inputQuality = inputQuality(level, pos, state);
        InformationRuntime.Snapshot serial = InformationRuntime.snapshot(level, "serial", input);
        InformationRuntime.Snapshot previous = InformationRuntime.snapshot(level, "bus8_out", pos);
        boolean valid = inputQuality == PortQuality.VALID;
        int value = valid ? serial.value() & 0xFF
                : inputQuality == PortQuality.STALE ? previous.value() & 0xFF : 0;
        InformationRuntime.write(level, "bus8_out", pos, value, 0, valid, valid ? serial.qualityPercent() : 0);
        if (level.getBlockState(output).getBlock() instanceof EightBitDataBusBlock) {
            DataBusNetwork.resolve(level, DataBusNetwork.collect(level, output));
        }
    }

    @Override
    public boolean drivesDataBusAt(BlockPos selfPos, BlockState selfState, BlockPos busPos) {
        return selfPos.relative(outputSide(selfState)).equals(busPos);
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
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, WATCHDOG_TICKS);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && neighborPos.equals(inputPos(pos, state))) {
            update(serverLevel, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            InformationRuntime.clear(level, "bus8_out", pos);
            BlockPos output = outputPos(pos, state);
            if (level.getBlockState(output).getBlock() instanceof EightBitDataBusBlock) {
                DataBusNetwork.resolve(level, DataBusNetwork.collect(level, output));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                BlockPos input = inputPos(pos, state);
                InformationRuntime.Snapshot serial = InformationRuntime.snapshot(level, "serial", input);
                PortQuality inputQuality = inputQuality(level, pos, state);
                InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "bus8_out", pos);
                player.displayClientMessage(Component.literal(
                        "Deserializer input=" + (serial.value() & 0xFF)
                                + " quality=" + inputQuality
                                + " | byteOut=" + (output.value() & 0xFF)
                                + " valid=" + output.valid()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
