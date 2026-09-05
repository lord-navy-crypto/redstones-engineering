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
import dev.redstoneengineering.physics.SoulFluxNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Redstone-commanded converter that injects bounded fictional Soul Flux into adjacent nodes. */
public class SoulFluxInjectorBlock extends Block implements EngineeringPortProvider {
    public SoulFluxInjectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SoulFluxInjectorBlock> codec() {
        return RedstoneEngineering.SOUL_FLUX_INJECTOR_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "REDSTONE→SOUL FLUX",
                        side,
                        EngineeringDomain.SOUL_FLUX,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        false,
                        "flux"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos target = pos.relative(side);
        if (!SoulFluxNetwork.isNode(level, target)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), 0.0, 0.0, 100.0, PortQuality.NO_SIGNAL));
        }
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), SoulFluxNetwork.charge(level, target), 0.0, 100.0, PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int command = level.getBestNeighborSignal(pos);
        if (command <= 0) return;
        int packet = command * 4;
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (SoulFluxNetwork.isNode(level, target)) {
                SoulFluxNetwork.inject(level, target, packet);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            int command = level.getBestNeighborSignal(pos);
            player.displayClientMessage(Component.literal(
                    "Soul Flux injector | REDSTONE command=" + command + "/15"
                            + " → packet=" + (command * 4) + "/60"
                            + " | six-face SOUL_FLUX converter output | Minecraft-fictional physics"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
