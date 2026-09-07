package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Six-face optical receiver terminal; it consumes a carrier and is never a transparent relay. */
public class OpticalReceiverBlock extends DomainBlock implements EngineeringPortProvider {
    private static final String KEY = "optical_receiver";
    private static final int INTENSITY = 0;
    private static final int CHANNEL = 1;
    private static final int VALID = 2;
    private static final int DRIVER_COUNT = 3;
    private static final int PHYSICAL_INPUTS = 4;
    private static final int RUNTIME_SIZE = 5;

    public OpticalReceiverBlock(Properties p) { super(p); }
    @Override public MapCodec<OpticalReceiverBlock> codec() { return RedstoneEngineering.OPTICAL_RECEIVER_CODEC.value(); }

    public static void setOptical(Level level, BlockPos pos, int intensity, int channel, boolean valid) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int inputs = physicalFiberInputs(level, pos);
        int drivers = Math.max(0, NetworkKernel.stats(level, "optical").activeDrivers());
        if (valid && drivers == 0) drivers = 1;
        boolean accepted = valid && intensity > 0 && inputs <= 1;
        runtime[INTENSITY] = accepted ? Math.max(0, Math.min(15, intensity)) : 0;
        runtime[CHANNEL] = accepted ? Math.max(0, Math.min(15, channel)) : 0;
        runtime[VALID] = accepted ? 1 : 0;
        runtime[DRIVER_COUNT] = drivers;
        runtime[PHYSICAL_INPUTS] = inputs;
    }

    public static int intensity(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos);return r==null?0:r[INTENSITY]; }
    public static int channel(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos);return r==null?0:r[CHANNEL]; }
    public static boolean valid(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos);return r!=null&&r.length>VALID&&r[VALID]==1; }
    public static int driverCount(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos);return r==null||r.length<=DRIVER_COUNT?0:r[DRIVER_COUNT]; }
    public static int inputCount(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos);return r==null||r.length<=PHYSICAL_INPUTS?physicalFiberInputs(level,pos):r[PHYSICAL_INPUTS]; }

    private static int physicalFiberInputs(Level level, BlockPos pos) {
        int count = 0;
        for (Direction side : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(side));
            if (!(neighbor.getBlock() instanceof OpticalFiberBlock)
                    && !(neighbor.getBlock() instanceof OpticalFiberJunctionBlock)) continue;
            if (!(neighbor.getBlock() instanceof ConnectedCableBlock cable) || !cable.topologyValid(neighbor)) continue;
            if (ConnectedCableBlock.connected(neighbor, side.getOpposite())) count++;
        }
        return count;
    }

    public static PortQuality quality(Level level, BlockPos pos) {
        if (inputCount(level,pos) > 1 || driverCount(level,pos) > 1) return PortQuality.TOPOLOGY_ERROR;
        return valid(level,pos) ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for(Direction direction:Direction.values()) {
            ports.add(new EngineeringPort("OPTICAL RECEIVER", direction, EngineeringDomain.OPTICAL,
                    PortKind.MEASUREMENT, PortDirection.INPUT, false, "intensity"));
        }
        return ports;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level,BlockPos pos,BlockState state,Direction side) {
        return engineeringPort(state,side).map(port -> new EngineeringPortSnapshot(
                port,intensity(level,pos),0.0,15.0,quality(level,pos)));
    }

    @Override
    protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos neighborPos,boolean moved) {
        // Receiver is a terminal sink: every adjacent component is recomputed independently.
        if(level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOpticalAround(serverLevel,pos);
    }

    @Override
    protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if(level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOpticalAround(serverLevel,pos);
    }

    @Override
    protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!state.is(next.getBlock())) {
            RuntimeIntStore.remove(level,KEY,pos);
            if(level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOpticalAround(serverLevel,pos);
        }
        super.onRemove(state,level,pos,next,moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide&&player instanceof ServerPlayer serverPlayer&&!player.isShiftKeyDown()) {
            FieldDeviceUi.open(serverPlayer,pos);
        } else if(!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Optical receiver | " + quality(level,pos)
                            + " | I="+intensity(level,pos)+"/15"
                            + " | channel="+channel(level,pos)
                            + " | inputs="+inputCount(level,pos)
                            + " | drivers="+driverCount(level,pos)),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
