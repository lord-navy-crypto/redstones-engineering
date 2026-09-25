package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.LapisPrecisionRangeSensorLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Directional time-of-flight-style range sensor, represented as a normalized Lapis quantity. */
public class LapisPrecisionRangeSensorBlock extends AbstractLapisTransducerBlock {
    public static final IntegerProperty RANGE_INDEX = IntegerProperty.create(
            "range_index",
            LapisPrecisionRangeSensorLogic.MIN_LEGACY_RANGE_INDEX,
            LapisPrecisionRangeSensorLogic.MAX_LEGACY_RANGE_INDEX);

    public record RangeSample(int distance, int maxRange, boolean complete) {
        public PortQuality quality() {
            if (!complete) return PortQuality.STALE;
            return distance < 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID;
        }
    }

    public LapisPrecisionRangeSensorBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(
                RANGE_INDEX, LapisPrecisionRangeSensorLogic.DEFAULT_LEGACY_RANGE_INDEX));
    }

    @Override public MapCodec<LapisPrecisionRangeSensorBlock> codec() { return RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_precision_range_sensor"; }
    @Override protected String instrumentName() { return "Lapis Precision Range Sensor"; }
    @Override protected String rangeText(BlockState state) {
        return LapisPrecisionRangeSensorLogic.rangeForLegacyIndex(state.getValue(RANGE_INDEX)) + " blocks";
    }
    @Override protected EngineeringDomain inputDomain() { return EngineeringDomain.GENERIC; }
    @Override protected String inputPortLabel() { return "RANGE SENSE"; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_INDEX);
    }

    public static int configuredRange(Level level, BlockPos pos, BlockState state) {
        int fallback = LapisPrecisionRangeSensorLogic.rangeForLegacyIndex(state.getValue(RANGE_INDEX));
        if (level instanceof ServerLevel serverLevel) {
            return LapisPrecisionRangeSensorLogic.boundedRange(
                    EngineeringDeviceParameters.get(serverLevel)
                            .extendedParameters(serverLevel, pos,
                                    new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0))
                            .a());
        }
        return fallback;
    }

    public static boolean setConfiguredRange(ServerLevel level, BlockPos pos, int range) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionRangeSensorBlock sensor)) return false;
        int bounded = LapisPrecisionRangeSensorLogic.boundedRange(range);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) {
            RuntimeIntStore.remove(level, sensor.runtimeKey(), pos);
            DomainNetwork.driveLapis(level, sensor.outputPos(pos, state), pos, 0, false);
            level.scheduleTick(pos, sensor, 1);
        }
        return changed;
    }

    public static RangeSample rangeSample(ServerLevel level, BlockPos pos, BlockState state) {
        int max = configuredRange(level, pos, state);
        Direction direction = state.getValue(DirectionalDomainBlock.FACING).getOpposite();
        for (int i = 1; i <= max; i++) {
            BlockPos p = pos.relative(direction, i);
            if (!level.hasChunkAt(p)) return new RangeSample(-1, max, false);
            BlockState target = level.getBlockState(p);
            if (!target.isAir() || !level.getFluidState(p).isEmpty()) {
                return new RangeSample(i, max, true);
            }
        }
        return new RangeSample(-1, max, true);
    }

    @Override
    protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        RangeSample sample = rangeSample(level, pos, state);
        if (sample.quality() == PortQuality.STALE) {
            return new Measurement(0, PortQuality.STALE,
                    "range coverage incomplete before " + sample.maxRange() + " blocks");
        }
        if (sample.quality() == PortQuality.NO_SIGNAL) {
            return new Measurement(0, PortQuality.NO_SIGNAL,
                    "no target within " + sample.maxRange() + " blocks");
        }
        int normalized = LapisPrecisionRangeSensorLogic.normalizedDistance(
                sample.distance(), sample.maxRange());
        return new Measurement(normalized, PortQuality.VALID,
                "distance=" + sample.distance() + "/" + sample.maxRange() + " blocks");
    }

    /** Server-authoritative Configure action. The old sample cannot survive a range change. */
    public boolean adjustRange(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != this) return false;
        int span = LapisPrecisionRangeSensorLogic.MAX_LEGACY_RANGE_INDEX
                - LapisPrecisionRangeSensorLogic.MIN_LEGACY_RANGE_INDEX + 1;
        int next = LapisPrecisionRangeSensorLogic.MIN_LEGACY_RANGE_INDEX + Math.floorMod(
                state.getValue(RANGE_INDEX) - LapisPrecisionRangeSensorLogic.MIN_LEGACY_RANGE_INDEX + delta,
                span);
        BlockState updated = state.setValue(RANGE_INDEX, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        EngineeringDeviceParameters.get(server).setExtendedParameters(
                server, pos, new EngineeringDeviceParameters.ExtendedParameters(
                        LapisPrecisionRangeSensorLogic.rangeForLegacyIndex(next), 0, 0, 0));
        RuntimeIntStore.remove(server, runtimeKey(), pos);
        DomainNetwork.driveLapis(server, outputPos(pos, updated), pos, 0, false);
        server.scheduleTick(pos, this, 1);
        return true;
    }

    public static int rangeBlocks(BlockState state) {
        return LapisPrecisionRangeSensorLogic.rangeForLegacyIndex(state.getValue(RANGE_INDEX));
    }

    public static int rangeBlocks(Level level, BlockPos pos, BlockState state) {
        return configuredRange(level, pos, state);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && player.isShiftKeyDown()) {
            adjustRange(level, pos, 1);
            BlockState updated = level.getBlockState(pos);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Precision Range Sensor range = " + rangeBlocks(level, pos, updated) + " blocks"), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
