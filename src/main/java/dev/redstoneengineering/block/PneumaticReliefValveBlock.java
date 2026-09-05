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
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/** Safety relief valve. BACK is inlet, FRONT is pressure-limited outlet, excess pressure vents to ambient. */
public class PneumaticReliefValveBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty SETPOINT = IntegerProperty.create("setpoint", 1, 4);
    private static final String RUNTIME = "pneumatic_relief";
    private static final int DIAG_SIZE = 4; // events, lastExcess, totalVentedProxy, previousVenting

    public PneumaticReliefValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SETPOINT, 3));
    }

    @Override public MapCodec<PneumaticReliefValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_RELIEF_VALVE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SETPOINT);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.SAFETY, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "LIMITED OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.SAFETY, PortDirection.OUTPUT, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int pressure = PneumaticNetwork.pressure(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure, 0.0, 100.0,
                pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    private static int[] diagnostics(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, RUNTIME, pos, DIAG_SIZE);
    }

    public static int ventEvents(Level level, BlockPos pos) { return diagnostics(level, pos)[0]; }
    public static int lastExcess(Level level, BlockPos pos) { return diagnostics(level, pos)[1]; }
    public static int totalVentedProxy(Level level, BlockPos pos) { return diagnostics(level, pos)[2]; }
    public static boolean venting(Level level, BlockPos pos) { return diagnostics(level, pos)[3] != 0; }

    /** Called by the pneumatic solver. Repeated solver passes during one overpressure episode count one event. */
    public static void recordVent(Level level, BlockPos pos, int excess) {
        int[] diag = diagnostics(level, pos);
        if (diag[3] == 0) diag[0]++;
        diag[1] = Math.max(0, excess);
        diag[2] += Math.max(0, excess);
        diag[3] = 1;
    }

    /** Re-arms the event edge once pressure is no longer above the configured setpoint. */
    public static void clearVenting(Level level, BlockPos pos) {
        diagnostics(level, pos)[3] = 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            RuntimeIntStore.remove(level, RUNTIME, pos);
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int next = state.getValue(SETPOINT) % 4 + 1;
                BlockState newState = state.setValue(SETPOINT, next);
                level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
                player.displayClientMessage(Component.literal(
                        "Relief valve setpoint=" + (next * 25) + "/100 ventEvents=" + ventEvents(level, pos)
                                + " lastExcess=" + lastExcess(level, pos)
                                + " totalVentedProxy=" + totalVentedProxy(level, pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
