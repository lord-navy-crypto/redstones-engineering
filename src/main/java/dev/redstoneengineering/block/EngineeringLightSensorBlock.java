package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SensorModel;
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

/**
 * Local-light instrument with selectable engineering response profile.
 *
 * FAST prioritizes response time; BALANCED is the default; PRECISION minimizes modeled
 * noise; RUGGED trades resolution for stable low-noise operation. Non-FAST profiles
 * retain one sample of acquisition latency instead of pretending every instrument is ideal.
 */
public class EngineeringLightSensorBlock extends DirectionalRedstoneSensorBlock {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 3);

    private static final String RUNTIME_KEY = "engineering_light_sensor_profile";
    private static final int PENDING_READING_100 = 0;
    private static final int PENDING_REFERENCE_100 = 1;
    private static final int INITIALIZED = 2;
    private static final int RUNTIME_SIZE = 3;

    public EngineeringLightSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PROFILE, 1));
    }

    @Override protected String metrologyChannel() { return "light_sensor"; }

    @Override
    public MapCodec<EngineeringLightSensorBlock> codec() {
        return RedstoneEngineering.ENGINEERING_LIGHT_SENSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROFILE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "LIGHT APERTURE",
                        Direction.UP,
                        EngineeringDomain.OPTICAL,
                        PortKind.SENSOR,
                        PortDirection.INPUT,
                        false,
                        "light"
                ),
                new EngineeringPort(
                        "SENSOR OUT",
                        frontSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.SENSOR,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        PortQuality quality = MetrologySupport.portQuality(sensorMeasurement(level, pos));
        if (side == Direction.UP) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), level.getMaxLocalRawBrightness(pos.above()), 0.0, 15.0, quality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(POWER), quality));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int profile = state.getValue(PROFILE);
        double reference = level.getMaxLocalRawBrightness(pos.above());
        double conditioned = MetrologySupport.conditionRedstone(level, pos, reference, profile);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        double emittedReading = conditioned;
        double emittedReference = reference;
        if (SensorModel.latencySamples(profile) > 0 && runtime[INITIALIZED] != 0) {
            emittedReading = runtime[PENDING_READING_100] / 100.0;
            emittedReference = runtime[PENDING_REFERENCE_100] / 100.0;
        }

        runtime[PENDING_READING_100] = (int) Math.round(conditioned * 100.0);
        runtime[PENDING_REFERENCE_100] = (int) Math.round(reference * 100.0);
        runtime[INITIALIZED] = 1;

        sampleMeasurement(level, pos, emittedReading, emittedReference, false);
        updateSensorOutput(
                level,
                pos,
                state,
                (int) Math.round(emittedReading),
                SensorModel.samplePeriod(profile)
        );
    }

    /** Server-authoritative operator action. Changing acquisition profile invalidates old evidence. */
    public static boolean adjustProfile(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EngineeringLightSensorBlock sensor)) return false;
        int nextProfile = Math.floorMod(state.getValue(PROFILE) + delta, 4);
        BlockState updated = state.setValue(PROFILE, nextProfile);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);

        RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        MetrologyStore.remove(level, sensor.metrologyChannel(), pos);
        server.scheduleTick(pos, sensor, 1);
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int profile = state.getValue(PROFILE);
                player.displayClientMessage(Component.literal(
                        "Local Light Sensor = " + state.getValue(POWER) + "/15"
                                + " | profile=" + SensorModel.profileName(profile)
                                + " sample=" + SensorModel.samplePeriod(profile) + "t"
                                + " resolution=" + SensorModel.resolutionStep(profile) + "/100"
                                + " noise=±" + SensorModel.noiseAmplitude(profile) + "/100"
                                + " latency=" + SensorModel.latencySamples(profile) + " sample"
                                + " | OPTICAL aperture=UP"
                                + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                                + " | FRONT REDSTONE OUT=" + frontSide(state).getName()
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
