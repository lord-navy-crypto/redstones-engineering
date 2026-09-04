package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Aligned, channel-selective optical BACK input -> FRONT redstone output. */
public class FreeSpaceOpticalReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    public FreeSpaceOpticalReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<FreeSpaceOpticalReceiverBlock> codec() {
        return RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("FREE-SPACE OPTICAL IN", inputSide(state), EngineeringDomain.OPTICAL,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "power"),
                new EngineeringPort("REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    private boolean validOptical(Level level, BlockPos pos, BlockState state) {
        return InformationRuntime.valid(level, "free_optical", pos)
                && InformationRuntime.quality(level, "free_optical", pos) > 0
                && InformationRuntime.aux(level, "free_optical", pos) == state.getValue(CHANNEL);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        boolean valid = validOptical(level, pos, state);
        int quality = InformationRuntime.quality(level, "free_optical", pos);
        PortQuality portQuality = valid
                ? PortQuality.VALID
                : quality > 0 ? PortQuality.DOMAIN_MISMATCH : PortQuality.NO_SIGNAL;
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), Math.min(15, InformationRuntime.value(level, "free_optical", pos)),
                    0.0, 15.0, portQuality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), portQuality));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return validOptical(level, pos, state)
                ? Math.min(15, InformationRuntime.value(level, "free_optical", pos)) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int quality = InformationRuntime.quality(level, "free_optical", pos);
        if (quality > 0) {
            InformationRuntime.write(
                    level,
                    "free_optical",
                    pos,
                    InformationRuntime.value(level, "free_optical", pos),
                    InformationRuntime.aux(level, "free_optical", pos),
                    quality > 20,
                    Math.max(0, quality - 20)
            );
        }
        updateOutput(level, pos, state, computeOutput(level, pos, state));
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "free_optical", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int channel = (state.getValue(CHANNEL) + 1) % 4;
                BlockState next = state.setValue(CHANNEL, channel);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                updateOutput(level, pos, next, computeOutput(level, pos, next));
                player.displayClientMessage(Component.literal(
                        "Free-space optical RX channel=" + channel
                                + " power=" + InformationRuntime.value(level, "free_optical", pos)
                                + " quality=" + InformationRuntime.quality(level, "free_optical", pos)
                                + "% | alignment required"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
