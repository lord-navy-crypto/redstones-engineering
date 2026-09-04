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
import dev.redstoneengineering.physics.DifferentialNetwork;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Two-wire conceptual differential medium. Payload remains digital; quality models common-mode rejection. */
public class DifferentialDataPairBlock extends Block implements EngineeringPortProvider {
    public DifferentialDataPairBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DifferentialDataPairBlock> codec() {
        return RedstoneEngineering.DIFFERENTIAL_DATA_PAIR_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "DIFFERENTIAL DATA",
                        side,
                        EngineeringDomain.DIFFERENTIAL_DATA,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "bit"
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
        boolean valid = InformationRuntime.valid(level, "diff", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                InformationRuntime.value(level, "diff", pos) & 1,
                0.0,
                1.0,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DifferentialNetwork.recompute(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            DifferentialNetwork.clearNode(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = pos.relative(direction);
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (neighborState.getBlock() instanceof DifferentialDataPairBlock pair) {
                        serverLevel.scheduleTick(neighborPos, pair, 1);
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
                        "Differential pair: bit=" + (InformationRuntime.value(level, "diff", pos) & 1)
                                + " quality=" + InformationRuntime.quality(level, "diff", pos) + "%"
                                + " valid=" + InformationRuntime.valid(level, "diff", pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
