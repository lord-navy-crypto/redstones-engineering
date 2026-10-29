package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
    public static final IntegerProperty RANGE_INDEX = IntegerProperty.create("range_index", 0, 3);
    private static final int[] RANGES = {8, 16, 32, 64};

    public record RangeSample(int distance, int maxRange, boolean complete) {
        public PortQuality quality() {
            if (!complete) return PortQuality.STALE;
            return distance < 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID;
        }
    }

    public LapisPrecisionRangeSensorBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(RANGE_INDEX, 1));
    }

    @Override public MapCodec<LapisPrecisionRangeSensorBlock> codec() { return RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_precision_range_sensor"; }
    @Override protected String instrumentName() { return "Lapis Precision Range Sensor"; }
    @Override protected String rangeText(BlockState state) { return RANGES[state.getValue(RANGE_INDEX)] + " blocks"; }
    @Override protected EngineeringDomain inputDomain() { return EngineeringDomain.GENERIC; }
    @Override protected String inputPortLabel() { return "RANGE SENSE"; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_INDEX);
    }

    public static RangeSample rangeSample(ServerLevel level, BlockPos pos, BlockState state) {
        int max = RANGES[state.getValue(RANGE_INDEX)];
        Direction direction = state.getValue(DirectionalDomainBlock.FACING).getOpposite();
        RangeScanSupport.RayResult ray = RangeScanSupport.scan(
                level, pos, direction, max, RangeScanSupport::solidOrFluid);
        return new RangeSample(ray.status() == RangeScanSupport.Status.TARGET ? ray.distance() : -1,
                max, ray.complete());
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
        int normalized = Math.round(EngineeringMath.clamp(sample.distance(), 0, sample.maxRange())
                * 100.0f / sample.maxRange());
        return new Measurement(normalized, PortQuality.VALID,
                "distance=" + sample.distance() + "/" + sample.maxRange() + " blocks");
    }

    /** Server-authoritative Configure action. The old sample cannot survive a range change. */
    public boolean adjustRange(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != this) return false;
        int next = Math.floorMod(state.getValue(RANGE_INDEX) + delta, RANGES.length);
        BlockState updated = state.setValue(RANGE_INDEX, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        RuntimeIntStore.remove(server, runtimeKey(), pos);
        DomainNetwork.driveLapis(server, outputPos(pos, updated), pos, 0, false);
        server.scheduleTick(pos, this, 1);
        return true;
    }

    public static int rangeBlocks(BlockState state) {
        return RANGES[state.getValue(RANGE_INDEX)];
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && player.isShiftKeyDown()) {
            if (hit.getDirection() == Direction.UP && level instanceof ServerLevel server) {
                // Explicitly requested measurement: bounded and read-only, never
                // silently re-run the scan when drawing the normal HMI.
                RangeScanSupport.RayResult ray = RangeScanSupport.scan(
                        server, pos, state.getValue(FACING).getOpposite(),
                        rangeBlocks(state), RangeScanSupport::solidOrFluid);
                RangeSample sample = rangeSample(server, pos, state);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "LAPIS RANGE FIELD TEST | status=" + ray.status()
                                + " | covered=" + ray.scannedCells() + "/" + ray.maxRange()
                                + " | target-distance=" + (sample.distance() > 0 ? sample.distance() : "NONE")
                                + " | raw-quality=" + sample.quality()
                                + " | conditioned=" + output(level, pos) + "/100:" + outputQuality(level, pos)
                                + " | UP=inspect only; other Shift faces=change range"
                                + " | clear beam and unloaded coverage are different"), false);
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            adjustRange(level, pos, 1);
            BlockState updated = level.getBlockState(pos);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Precision Range Sensor range = " + rangeBlocks(updated) + " blocks"), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
