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
import dev.redstoneengineering.physics.PrecisionObservationSupport;
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
    private static final int RUNTIME_SIZE = 4;
    private static final int CLOCK_SEEN = 0;
    private static final int PREVIOUS_CLOCK = 1;
    private static final int HELD_VALUE = 2;
    private static final int HELD_QUALITY = 3;

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

    private static int encodeQuality(PortQuality quality) {
        return quality.ordinal() + 1;
    }

    public static int heldValue(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE ? 0 : runtime[HELD_VALUE];
    }

    public static PortQuality heldQuality(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[HELD_QUALITY] <= 0) return PortQuality.STALE;
        int ordinal = runtime[HELD_QUALITY] - 1;
        PortQuality[] values = PortQuality.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.STALE;
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
            var sample = PrecisionObservationSupport.lapis(level, inputPos(pos, state));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    sample.value() / 100.0,
                    0.0,
                    1.0,
                    sample.quality()
            ));
        }
        if (side == triggerSide(state)) {
            var clock = PrecisionObservationSupport.quartz(level, pos.relative(triggerSide(state)));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    clock.active() ? 1.0 : 0.0,
                    0.0,
                    1.0,
                    clock.quality()
            ));
        }

        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                heldValue(level, pos) / 100.0,
                0.0,
                1.0,
                heldQuality(level, pos)
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        var clock = PrecisionObservationSupport.quartz(level, pos.relative(triggerSide(state)));

        if (!clock.valid()) {
            // An unknown/invalid clock interval breaks edge chronology. The next valid
            // sample establishes a new baseline instead of manufacturing an edge.
            runtime[CLOCK_SEEN] = 0;
            runtime[PREVIOUS_CLOCK] = 0;
            level.scheduleTick(pos, this, 1);
            return;
        }

        int active = clock.active() ? 1 : 0;
        if (runtime[CLOCK_SEEN] == 0) {
            runtime[CLOCK_SEEN] = 1;
            runtime[PREVIOUS_CLOCK] = active;
            level.scheduleTick(pos, this, 1);
            return;
        }

        boolean rising = active == 1 && runtime[PREVIOUS_CLOCK] == 0;
        if (rising) {
            var sample = PrecisionObservationSupport.lapis(level, inputPos(pos, state));
            runtime[HELD_VALUE] = sample.valid() ? sample.value() : 0;
            runtime[HELD_QUALITY] = encodeQuality(sample.quality());
            DomainNetwork.driveLapis(
                    level,
                    outputPos(pos, state),
                    pos,
                    runtime[HELD_VALUE],
                    sample.valid()
            );
        }
        runtime[PREVIOUS_CLOCK] = active;
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
            player.displayClientMessage(Component.literal(
                    "Quartz Triggered Lapis Sampler | held="
                            + (heldQuality(level, pos) == PortQuality.VALID
                            ? String.format("%.2f", heldValue(level, pos) / 100.0)
                            : heldQuality(level, pos).name())
                            + " | quartz input=LEFT | lapis input=BACK | output=FRONT"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
