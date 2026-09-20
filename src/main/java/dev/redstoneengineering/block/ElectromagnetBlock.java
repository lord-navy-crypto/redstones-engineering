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
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.ElectromagnetLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Copper-powered electromagnet with finite inductive response and thermal derating.
 *
 * Copper voltage commands excitation, but actual field does not jump instantly. Sustained high
 * excitation accumulates thermal load; protection progressively caps achievable field until the
 * coil cools. FIELD remains the external magnetic source consumed by MagneticPhysics.
 */
public class ElectromagnetBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD = IntegerProperty.create("field", 0, 15);

    private static final String RUNTIME_KEY = "electromagnet";
    private static final int TARGET_FIELD = 0;
    private static final int THERMAL_LOAD = 1;
    private static final int RUN_TICKS = 2;
    private static final int INITIALIZED = 3;
    private static final int RUNTIME_SIZE = 4;

    public ElectromagnetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FIELD, 0));
    }

    @Override public MapCodec<ElectromagnetBlock> codec() { return RedstoneEngineering.ELECTROMAGNET_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FIELD); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values()).map(side ->
                new EngineeringPort("COPPER COIL INPUT", side, EngineeringDomain.COPPER,
                        PortKind.ACTUATOR, PortDirection.INPUT, false, "voltage")).toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInputOnSide(level, pos, side);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), input.voltage(), 0.0, 15.0, input.quality()));
    }

    public static CopperNetworkSupport.TerminalInput input(Level level, BlockPos pos) {
        return CopperNetworkSupport.terminalInput(level, pos);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    public static int targetField(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(15, runtime[TARGET_FIELD]));
    }

    public static int thermalLoad(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(1000, runtime[THERMAL_LOAD]));
    }

    public static int trackingError(Level level, BlockPos pos) {
        return targetField(level, pos) - Math.max(0, Math.min(15, level.getBlockState(pos).getValue(FIELD)));
    }

    public static int runTicks(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[RUN_TICKS]);
    }

    public static boolean thermalDerated(Level level, BlockPos pos) {
        return thermalLoad(level, pos) >= 700;
    }

    public static EngineeringDeviceParameters.ExtendedParameters configuredResponse(Level level, BlockPos pos) {
        var fallback = new EngineeringDeviceParameters.ExtendedParameters(2, 3, 0, 0);
        if (level instanceof ServerLevel serverLevel) {
            return EngineeringDeviceParameters.get(serverLevel).extendedParameters(serverLevel, pos, fallback);
        }
        return fallback;
    }

    public static boolean setResponseRates(ServerLevel level, BlockPos pos, int rise, int fall) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ElectromagnetBlock magnet)) return false;
        var next = new EngineeringDeviceParameters.ExtendedParameters(
                Math.max(1, Math.min(15, rise)),
                Math.max(1, Math.min(15, fall)),
                0, 0);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(level, pos, next);
        if (changed) level.scheduleTick(pos, magnet, 1);
        return changed;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
        int commandedField = input.quality() == PortQuality.VALID ? input.voltage() : 0;
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        int thermal = ElectromagnetLogic.nextThermal(runtime[THERMAL_LOAD], commandedField);
        int target = ElectromagnetLogic.deratedTarget(commandedField, thermal);
        var response = configuredResponse(level, pos);
        int actual = ElectromagnetLogic.stepFieldRate(state.getValue(FIELD), target, response.a(), response.b());

        runtime[TARGET_FIELD] = target;
        runtime[THERMAL_LOAD] = thermal;
        runtime[INITIALIZED] = 1;
        if (commandedField > 0 && runtime[RUN_TICKS] < Integer.MAX_VALUE) runtime[RUN_TICKS]++;

        if (actual != state.getValue(FIELD)) {
            level.setBlock(pos, state.setValue(FIELD, actual), Block.UPDATE_CLIENTS);
        }

        if (actual != target || commandedField > 0 || thermal > 0) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean moved) {
        if (!state.is(nextState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
                for (Direction direction : Direction.values()) DomainNetwork.recomputeCopper(serverLevel, pos.relative(direction));
            }
        }
        super.onRemove(state, level, pos, nextState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
            player.displayClientMessage(Component.literal(
                    "Electromagnet | V=" + input.voltage() + "/15"
                            + " targetB=" + targetField(level, pos) + "/15"
                            + " actualB=" + state.getValue(FIELD) + "/15"
                            + " error=" + trackingError(level, pos)
                            + " | thermal=" + thermalLoad(level, pos) + "/1000"
                            + " " + ElectromagnetLogic.thermalState(thermalLoad(level, pos))
                            + " | runTicks=" + runTicks(level, pos)
                            + " | feeds=" + input.connectedFeeds()
                            + " | " + input.quality()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
