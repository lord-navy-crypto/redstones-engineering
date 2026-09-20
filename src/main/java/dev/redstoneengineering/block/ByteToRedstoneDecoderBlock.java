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
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Byte-to-redstone bridge: saturates 0..255 into the vanilla 0..15 output range. */
public class ByteToRedstoneDecoderBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 1);
    public static final int CLAMP = 0;
    public static final int FULL_SCALE = 1;

    public ByteToRedstoneDecoderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, CLAMP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    public static int decode(int byteValue, int mode) {
        int value = Math.max(0, Math.min(255, byteValue));
        return mode == FULL_SCALE
                ? Math.max(0, Math.min(15, (int) Math.round(value / 17.0)))
                : Math.min(15, value);
    }

    public static String modeName(int mode) {
        return mode == FULL_SCALE ? "FULL_SCALE" : "CLAMP";
    }

    public static boolean stepMode(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ByteToRedstoneDecoderBlock decoder)) return false;
        int next = state.getValue(MODE) == CLAMP ? FULL_SCALE : CLAMP;
        level.setBlock(pos, state.setValue(MODE, next), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        if (level instanceof net.minecraft.server.level.ServerLevel server) server.scheduleTick(pos, decoder, 1);
        return true;
    }

    @Override
    public MapCodec<ByteToRedstoneDecoderBlock> codec() {
        return RedstoneEngineering.BYTE_TO_REDSTONE_DECODER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "BYTE IN",
                        inputSide(state),
                        EngineeringDomain.DATA_BUS_8,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "byte"
                ),
                new EngineeringPort(
                        "REDSTONE OUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    private static PortQuality inputQuality(Level level, BlockPos input) {
        if (!level.hasChunkAt(input)) return PortQuality.STALE;
        if (!DataBusNetwork.isNode(level, input)) return PortQuality.NO_SIGNAL;
        return DataBusNetwork.quality(level, input);
    }

    public static PortQuality inputEvidenceQuality(Level level, BlockPos pos, BlockState state) {
        BlockPos input = pos.relative(DirectionalSignalBlock.seriesInputSide(state));
        return inputQuality(level, input);
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
        BlockPos input = inputPos(pos, state);
        PortQuality inputQuality = inputQuality(level, input);
        int byteValue = DataBusNetwork.sample(level, input);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    byteValue,
                    0.0,
                    255.0,
                    inputQuality
            ));
        }
        PortQuality outputQuality = inputQuality;
        if (inputQuality == PortQuality.VALID && state.getValue(MODE) == CLAMP && byteValue > 15) {
            outputQuality = PortQuality.SATURATED;
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(),
                state.getValue(OUTPUT),
                outputQuality
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        PortQuality quality = inputQuality(level, input);
        if (quality == PortQuality.VALID) {
            return decode(DataBusNetwork.sample(level, input), state.getValue(MODE));
        }
        if (quality == PortQuality.NO_SIGNAL) {
            // A complete observation that no byte source exists is a real de-energized output.
            return 0;
        }
        // STALE/FAULT/DOMAIN/TOPOLOGY evidence cannot define a new decoded code.
        // Retain the last trustworthy Redstone output while engineeringSnapshot exposes quality.
        return state.getValue(OUTPUT);
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
                BlockPos input = inputPos(pos, state);
                player.displayClientMessage(Component.literal(
                        "Byte decoder input=" + DataBusNetwork.sample(level, input)
                                + " quality=" + inputQuality(level, input)
                                + " | mode=" + modeName(state.getValue(MODE))
                                + " | output=" + outputValue(level, pos, state) + "/15"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
