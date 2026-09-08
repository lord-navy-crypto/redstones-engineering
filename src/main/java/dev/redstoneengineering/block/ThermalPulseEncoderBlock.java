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
import dev.redstoneengineering.physics.ThermalPulseKernel;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Low-bandwidth redstone-to-phonon pulse encoder. DOWN is drive input. */
public class ThermalPulseEncoderBlock extends Block implements EngineeringPortProvider {
    private static final String ENCODER_KEY = "thermal_encoder";
    private static final Direction[] OUTPUTS = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public ThermalPulseEncoderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ThermalPulseEncoderBlock> codec() {
        return RedstoneEngineering.THERMAL_PULSE_ENCODER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE DRIVE", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.INPUT, true, "signal"),
                phononOut(Direction.UP), phononOut(Direction.NORTH), phononOut(Direction.SOUTH),
                phononOut(Direction.WEST), phononOut(Direction.EAST)
        );
    }

    private static EngineeringPort phononOut(Direction side) {
        return new EngineeringPort("THERMAL PULSE OUT", side, EngineeringDomain.PHONON_THERMAL,
                PortKind.CONVERTER, PortDirection.OUTPUT, false, "pulse");
    }

    private static PortQuality packetQuality(InformationRuntime.Snapshot packet) {
        return packet.valid() && packet.qualityPercent() > 0 && packet.value() > 0
                ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.DOWN) {
            RedstoneObservationSupport.Observation drive = inputObservation(level, pos);
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), drive.value(), drive.quality()));
        }
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, ENCODER_KEY, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(15, packet.value())), 0.0, 15.0, packetQuality(packet)));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.DOWN;
    }

    /** Observer-only input evidence; a configured LOW source is distinct from no source. */
    public static RedstoneObservationSupport.Observation inputObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.DOWN);
    }

    private void updatePulse(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        RedstoneObservationSupport.Observation drive = inputObservation(level, pos);
        if (!drive.valid() || drive.value() <= 0) {
            InformationRuntime.clear(level, ENCODER_KEY, pos);
            return;
        }
        InformationRuntime.write(level, ENCODER_KEY, pos, drive.value(), 0, true, 100);
        ThermalPulseKernel.send(serverLevel, pos, drive.value(), OUTPUTS);
        // The converter emits an event packet, not a continuously driven phonon level.
        serverLevel.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        if (neighborPos.equals(pos.below())) updatePulse(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) updatePulse(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InformationRuntime.clear(level, ENCODER_KEY, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, ENCODER_KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            FieldDeviceUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
