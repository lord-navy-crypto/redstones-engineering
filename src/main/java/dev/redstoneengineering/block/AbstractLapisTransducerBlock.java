package dev.redstoneengineering.block;

import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SensorModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Common physical-quantity -> Lapis transducer behavior for alpha.8.
 * BlockState stores only small player configuration; live measurements are runtime data.
 */
public abstract class AbstractLapisTransducerBlock extends DirectionalDomainBlock {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 3);

    protected AbstractLapisTransducerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PROFILE, 1));
    }

    protected record Measurement(int normalized, boolean valid, String detail) {}

    protected abstract String runtimeKey();
    protected abstract String instrumentName();
    protected abstract String rangeText(BlockState state);
    protected abstract Measurement sense(ServerLevel level, BlockPos pos, BlockState state);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROFILE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int profile = state.getValue(PROFILE);
        Measurement raw = sense(level, pos, state);
        int measured = raw.valid() ? SensorModel.condition(level, pos, raw.normalized(), profile) : 0;
        int[] rt = RuntimeIntStore.get(level, runtimeKey(), pos, 5);

        int output;
        boolean valid;
        if (SensorModel.latencySamples(profile) == 0 || rt[4] == 0) {
            output = measured;
            valid = raw.valid();
        } else {
            output = rt[2];
            valid = rt[3] == 1;
        }

        rt[0] = output;
        rt[1] = valid ? 1 : 0;
        rt[2] = measured;
        rt[3] = raw.valid() ? 1 : 0;
        rt[4] = 1;

        DomainNetwork.driveLapis(level, outputPos(pos, state), pos, output, valid);
        level.scheduleTick(pos, this, SensorModel.samplePeriod(profile));
    }

    public int output(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, runtimeKey(), pos, 5)[0];
    }

    public boolean valid(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, runtimeKey(), pos, 5)[1] == 1;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) DomainNetwork.driveLapis(server, outputPos(pos, state), pos, 0, false);
            RuntimeIntStore.remove(level, runtimeKey(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                int next = (state.getValue(PROFILE) + 1) & 3;
                state = state.setValue(PROFILE, next);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
            int profile = state.getValue(PROFILE);
            String value = valid(level, pos) ? String.format("%.2f", output(level, pos) / 100.0) : "INVALID";
            String detail = level instanceof ServerLevel server ? sense(server, pos, state).detail() : "";
            player.displayClientMessage(Component.literal(
                    instrumentName() + " | Lapis=" + value
                            + " | profile=" + SensorModel.profileName(profile)
                            + " | sample=" + SensorModel.samplePeriod(profile) + "t"
                            + " | resolution=" + SensorModel.resolutionStep(profile) + "/100"
                            + " | noise=±" + SensorModel.noiseAmplitude(profile) + "/100"
                            + " | latency=" + SensorModel.latencySamples(profile) + " sample"
                            + " | range=" + rangeText(state)
                            + (detail.isEmpty() ? "" : " | " + detail)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
