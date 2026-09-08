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
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redstone-commanded converter that injects bounded fictional Soul Flux into adjacent nodes.
 * UP is the dedicated command input; the other five faces are Soul-Flux outputs.
 */
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
        List<EngineeringPort> ports = new ArrayList<>();
        ports.add(new EngineeringPort(
                "REDSTONE COMMAND",
                Direction.UP,
                EngineeringDomain.REDSTONE,
                PortKind.CONTROL,
                PortDirection.INPUT,
                true,
                "signal"
        ));
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) continue;
            ports.add(new EngineeringPort(
                    "SOUL FLUX OUT",
                    side,
                    EngineeringDomain.SOUL_FLUX,
                    PortKind.CONVERTER,
                    PortDirection.OUTPUT,
                    false,
                    "flux"
            ));
        }
        return List.copyOf(ports);
    }

    private static PortQuality fluxQuality(InformationRuntime.Snapshot snapshot) {
        if (snapshot.ageTicks() < 0) return PortQuality.NO_SIGNAL;
        if (!snapshot.valid() || snapshot.qualityPercent() <= 0) return PortQuality.STALE;
        return PortQuality.VALID;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            RedstoneObservationSupport.Observation command = commandObservation(level, pos);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), command.value(), command.quality()));
        }

        BlockPos target = pos.relative(side);
        if (!level.hasChunkAt(target)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), 0.0, 0.0, 100.0, PortQuality.STALE));
        }
        if (!SoulFluxNetwork.isNode(level, target)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), 0.0, 0.0, 100.0, PortQuality.NO_SIGNAL));
        }

        InformationRuntime.Snapshot flux = SoulFluxNetwork.chargeSnapshot(level, target);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(100, flux.value())), 0.0, 100.0, fluxQuality(flux)));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.UP;
    }

    /** Observer-only distinction between an attached zero command and a missing command source. */
    public static RedstoneObservationSupport.Observation commandObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.UP);
    }

    public static int commandSignal(Level level, BlockPos pos) {
        return commandObservation(level, pos).value();
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
        if (!level.isClientSide && neighborPos.equals(pos.above())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RedstoneObservationSupport.Observation command = commandObservation(level, pos);
        if (!command.valid() || command.value() <= 0) return;
        int packet = command.value() * 4;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos target = pos.relative(direction);
            if (level.hasChunkAt(target) && SoulFluxNetwork.isNode(level, target)) {
                SoulFluxNetwork.inject(level, target, packet);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            RedstoneObservationSupport.Observation command = commandObservation(level, pos);
            player.displayClientMessage(Component.literal(
                    "Soul Flux injector | UP REDSTONE command=" + command.value() + "/15"
                            + " [" + command.quality().name() + "]"
                            + " → packet=" + (command.valid() ? command.value() * 4 : 0) + "/60"
                            + " | five-face SOUL_FLUX output | Minecraft-fictional physics"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
