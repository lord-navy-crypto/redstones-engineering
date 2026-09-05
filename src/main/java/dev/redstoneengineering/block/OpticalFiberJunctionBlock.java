package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Serviceable two-ended optical splice. CLOSED joins two fiber segments without
 * introducing a branch; SERVICE_OPEN deliberately isolates both segments for
 * maintenance and fault localization. Use Optical Splitter for 1->2 branching.
 */
public class OpticalFiberJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public static final BooleanProperty SERVICE_OPEN = BooleanProperty.create("service_open");
    private static final String KEY = "optical_junction";

    public OpticalFiberJunctionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SERVICE_OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SERVICE_OPEN);
    }

    @Override public MapCodec<OpticalFiberJunctionBlock> codec(){return RedstoneEngineering.OPTICAL_FIBER_JUNCTION_CODEC.value();}

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        BlockState self = level.getBlockState(pos);
        if (self.getBlock() == this && self.getValue(SERVICE_OPEN)) return false;
        return TransmissionTopology.opticalPort(neighbor, direction);
    }

    public static void setOptical(Level level, BlockPos pos, int intensity, int channel, boolean valid) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3);
        rt[0] = valid ? Math.max(0, Math.min(15, intensity)) : 0;
        rt[1] = valid ? Math.max(0, Math.min(15, channel)) : 0;
        rt[2] = valid && rt[0] > 0 ? 1 : 0;
    }
    public static int intensity(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null?0:r[0]; }
    public static int channel(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null?0:r[1]; }
    public static boolean valid(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r!=null&&r.length>2&&r[2]==1; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        if (state.getValue(SERVICE_OPEN)) return List.of();
        return OpticalPortSupport.connected(state, "OPTICAL SERVICE SPLICE", PortKind.BUS, PortDirection.BIDIRECTIONAL);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, intensity(level,pos), 0.0, 15.0,
                valid(level,pos) ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved){
        super.onPlace(state,level,pos,oldState,moved);
        if(level instanceof ServerLevel server) DomainNetwork.recomputeOptical(server,pos);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved){
        super.neighborChanged(state,level,pos,neighbor,neighborPos,moved);
        if(level instanceof ServerLevel server) {
            if (state.getValue(SERVICE_OPEN)) DomainNetwork.recomputeOpticalAround(server,pos);
            else DomainNetwork.recomputeOptical(server,pos);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel server) DomainNetwork.recomputeOpticalAround(server,pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /**
     * Explicit maintenance control used by player interaction and GameTests.
     * Opening a splice removes its physical arms and independently resolves both
     * adjacent optical components; closing it rebuilds continuity and resolves
     * the joined component again.
     */
    public void setServiceOpen(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || state.getValue(SERVICE_OPEN) == open) return;
        BlockState next = state.setValue(SERVICE_OPEN, open);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        refreshConnections(level, pos, next);
        level.updateNeighborsAt(pos, this);
        if (level instanceof ServerLevel server) {
            if (open) DomainNetwork.recomputeOpticalAround(server, pos);
            else DomainNetwork.recomputeOptical(server, pos);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                boolean open = !state.getValue(SERVICE_OPEN);
                setServiceOpen(level, pos, open);
                player.displayClientMessage(Component.literal(
                        "Optical service splice | " + (open ? "SERVICE OPEN — segments isolated" : "CLOSED — continuity restored")), true);
            } else {
                FieldDeviceUi.open(serverPlayer,pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
