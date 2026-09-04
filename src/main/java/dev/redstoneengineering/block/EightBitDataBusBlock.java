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
import dev.redstoneengineering.physics.NetworkKernel;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 8-bit bundled data medium. Runtime payload; no 256-way BlockState explosion. */
public class EightBitDataBusBlock extends Block implements EngineeringPortProvider {
    public EightBitDataBusBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<EightBitDataBusBlock> codec() {
        return RedstoneEngineering.EIGHT_BIT_DATA_BUS_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "8-BIT DATA BUS",
                        side,
                        EngineeringDomain.DATA_BUS_8,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "byte"
                ))
                .toList();
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
        DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(level, pos);
        PortQuality quality;
        if (diagnostics.driverCount() == 0) quality = PortQuality.NO_SIGNAL;
        else if (!diagnostics.valid()) quality = PortQuality.FAULT;
        else quality = PortQuality.VALID;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                DataBusNetwork.sample(level, pos),
                0.0,
                255.0,
                quality
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
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
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DataBusNetwork.resolve(level, DataBusNetwork.collect(level, pos));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            DataBusNetwork.clearNode(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = pos.relative(direction);
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (neighborState.getBlock() instanceof EightBitDataBusBlock bus) {
                        serverLevel.scheduleTick(neighborPos, bus, 1);
                    }
                }
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
                player.displayClientMessage(Component.literal(
                        "8-bit Bus = " + DataBusNetwork.sample(level, pos)
                                + " (0x" + String.format("%02X", DataBusNetwork.sample(level, pos)) + ")"
                                + " | " + DataBusNetwork.diagnostics(level, pos)
                                + " | " + NetworkKernel.summary(level, "bus8")
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
