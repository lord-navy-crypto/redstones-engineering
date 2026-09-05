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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Minecraft-fictional transient Soul-Flux transport medium. */
public class SoulSoilConduitBlock extends Block implements EngineeringPortProvider {
    private static final int DECAY_PERIOD_TICKS = 20;

    public SoulSoilConduitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SoulSoilConduitBlock> codec() {
        return RedstoneEngineering.SOUL_SOIL_CONDUIT_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "SOUL FLUX BUS",
                        side,
                        EngineeringDomain.SOUL_FLUX,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "flux"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        return engineeringPort(state, side).map(port -> {
            int charge = SoulFluxNetwork.charge(level, pos);
            return new EngineeringPortSnapshot(
                    port, charge, 0.0, 100.0,
                    charge > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL);
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, DECAY_PERIOD_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SoulFluxNetwork.decay(level, pos);
        level.scheduleTick(pos, this, DECAY_PERIOD_TICKS);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) SoulFluxNetwork.clear(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Soul Flux conduit | six-face SOUL_FLUX bus | J≈"
                            + SoulFluxNetwork.charge(level, pos)
                            + "/100 | transient decay=" + DECAY_PERIOD_TICKS + "t | Minecraft-fictional physics"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
