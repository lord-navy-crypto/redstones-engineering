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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Quartz timing divider with runtime edge count and explicit timing-domain ports. */
public class QuartzClockDividerBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty DIV_INDEX = IntegerProperty.create("division", 0, 3);
    private static final String KEY = "quartz_divider";

    public QuartzClockDividerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DIV_INDEX, 0));
    }

    @Override
    public MapCodec<QuartzClockDividerBlock> codec() {
        return RedstoneEngineering.QUARTZ_CLOCK_DIVIDER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIV_INDEX);
    }

    public static int division(int index) {
        return switch (index) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 8;
            default -> 16;
        };
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("QUARTZ CLOCK IN", inputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.INPUT, false, "ticks"),
                new EngineeringPort("DIVIDED CLOCK OUT", outputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.TRIGGER, PortDirection.OUTPUT, false, "ticks")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.QuartzSample sample = side == inputSide(state)
                ? DomainNetwork.sampleQuartz(level, inputPos(pos, state))
                : DomainNetwork.sampleQuartz(level, outputPos(pos, state));
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), sample.periodTicks(), 0.0, 4096.0,
                sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveQuartz(serverLevel, outputPos(pos, state), pos, false, 1, false);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 3); // count, prev, out
        boolean rising = input.valid() && input.active() && runtime[1] == 0;
        int divisor = division(state.getValue(DIV_INDEX));
        if (rising) runtime[0] = (runtime[0] + 1) % divisor;
        runtime[2] = runtime[0] < divisor / 2 ? 1 : 0;
        runtime[1] = input.active() ? 1 : 0;
        int outputPeriod = Math.min(4096, Math.max(1, input.periodTicks()) * divisor);
        DomainNetwork.driveQuartz(
                level, outputPos(pos, state), pos, runtime[2] == 1, outputPeriod, input.valid());
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int index = (state.getValue(DIV_INDEX) + 1) % 4;
                BlockState next = state.setValue(DIV_INDEX, index);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                int[] runtime = RuntimeIntStore.get(level, KEY, pos, 3);
                runtime[0] = 0;
                runtime[1] = 0;
                runtime[2] = 0;
                player.displayClientMessage(Component.literal(
                        "Quartz divider | ÷" + division(index)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
