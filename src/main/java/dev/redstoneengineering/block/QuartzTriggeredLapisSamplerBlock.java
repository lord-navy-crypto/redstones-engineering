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
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Quartz rising-edge triggered sample-and-hold for the Lapis precision domain. */
public class QuartzTriggeredLapisSamplerBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    private static final String KEY = "quartz_triggered_lapis_sampler";

    public QuartzTriggeredLapisSamplerBlock(Properties p) {
        super(p);
    }

    @Override
    public MapCodec<QuartzTriggeredLapisSamplerBlock> codec() {
        return RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER_CODEC.value();
    }

    private Direction triggerSide(BlockState state) {
        return leftOf(outputSide(state));
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "LAPIS INPUT",
                        inputSide(state),
                        EngineeringDomain.LAPIS,
                        PortKind.MEASUREMENT,
                        PortDirection.INPUT,
                        false,
                        "normalized"
                ),
                new EngineeringPort(
                        "QUARTZ TRIGGER",
                        triggerSide(state),
                        EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER,
                        PortDirection.INPUT,
                        false,
                        "edge"
                ),
                new EngineeringPort(
                        "HELD LAPIS OUTPUT",
                        outputSide(state),
                        EngineeringDomain.LAPIS,
                        PortKind.MEASUREMENT,
                        PortDirection.OUTPUT,
                        false,
                        "normalized"
                )
        );
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

        if (side == inputSide(state)) {
            var sample = DomainNetwork.sampleLapis(level, inputPos(pos, state));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    sample.valid() ? sample.value() / 100.0 : 0.0,
                    0.0,
                    1.0,
                    sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL
            ));
        }
        if (side == triggerSide(state)) {
            var clock = DomainNetwork.sampleQuartz(level, pos.relative(triggerSide(state)));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    clock.active() ? 1.0 : 0.0,
                    0.0,
                    1.0,
                    clock.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL
            ));
        }

        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                rt[2] == 1 ? rt[1] / 100.0 : 0.0,
                0.0,
                1.0,
                rt[2] == 1 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3); // previous clock, held value, held valid
        var clock = DomainNetwork.sampleQuartz(level, pos.relative(triggerSide(state)));
        boolean active = clock.valid() && clock.active();
        boolean rising = active && rt[0] == 0;
        if (rising) {
            var sample = DomainNetwork.sampleLapis(level, inputPos(pos, state));
            rt[1] = sample.value();
            rt[2] = sample.valid() ? 1 : 0;
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, rt[1], rt[2] == 1);
        }
        rt[0] = active ? 1 : 0;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) {
                DomainNetwork.driveLapis(server, outputPos(pos, state), pos, 0, false);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int[] rt = RuntimeIntStore.get(level, KEY, pos, 3);
            player.displayClientMessage(Component.literal(
                    "Quartz Triggered Lapis Sampler | held="
                            + (rt[2] == 1 ? String.format("%.2f", rt[1] / 100.0) : "INVALID")
                            + " | quartz input=LEFT | lapis input=BACK | output=FRONT"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
