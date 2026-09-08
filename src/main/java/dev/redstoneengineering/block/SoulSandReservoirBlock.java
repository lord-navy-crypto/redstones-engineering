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

/** Minecraft-fictional Soul-Flux storage node with deliberately slow decay. */
public class SoulSandReservoirBlock extends Block implements EngineeringPortProvider {
    private static final int DECAY_PERIOD_TICKS = 40;

    public SoulSandReservoirBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SoulSandReservoirBlock> codec() {
        return RedstoneEngineering.SOUL_SAND_RESERVOIR_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "SOUL RESERVOIR",
                        side,
                        EngineeringDomain.SOUL_FLUX,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "charge"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        return engineeringPort(state, side).map(port -> {
            InformationRuntime.Snapshot stored = SoulFluxNetwork.chargeSnapshot(level, pos);
            int charge = Math.max(0, Math.min(100, stored.value()));
            PortQuality quality = stored.ageTicks() < 0
                    ? PortQuality.STALE
                    : stored.valid() ? PortQuality.VALID : PortQuality.FAULT;
            return new EngineeringPortSnapshot(
                    port,
                    charge,
                    0.0,
                    100.0,
                    quality
            );
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            SoulFluxNetwork.initializeReservoir(level, pos);
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
            InformationRuntime.Snapshot stored = SoulFluxNetwork.chargeSnapshot(level, pos);
            player.displayClientMessage(Component.literal(
                    "Soul reservoir | six-face SOUL_FLUX storage | Qs="
                            + Math.max(0, Math.min(100, stored.value()))
                            + "/100 | quality=" + (stored.valid() ? "VALID" : "STALE")
                            + " | slow decay=" + DECAY_PERIOD_TICKS + "t | Minecraft-fictional physics"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
