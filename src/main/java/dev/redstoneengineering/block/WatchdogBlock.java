package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Heartbeat watchdog with configurable timeout. Any BACK input transition resets the timer. */
public class WatchdogBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TIMEOUT = IntegerProperty.create("timeout", 0, 3);
    private static final int[] TIMEOUT_TICKS = {20, 40, 80, 160};
    private static final String KEY = "watchdog";

    public WatchdogBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(TIMEOUT, 1));
    }

    @Override public MapCodec<WatchdogBlock> codec() { return RedstoneEngineering.WATCHDOG_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(TIMEOUT); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("HEARTBEAT IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.TRIGGER, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("TIMEOUT OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "alarm")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), readBackInput(level, pos, state), PortQuality.VALID));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(OUTPUT), PortQuality.VALID));
    }

    @Override protected int computeOutput(Level l, BlockPos p, BlockState s) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, 4);
        return rt[1] >= timeoutTicks(s.getValue(TIMEOUT)) ? 15 : 0;
    }

    private void sample(ServerLevel l, BlockPos p, BlockState s) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, 4);
        int now = readBackInput(l, p, s);
        if (now != rt[0]) {
            rt[0] = now;
            rt[1] = 0;
            rt[2]++;
        } else {
            rt[1] = Math.min(12000, rt[1] + 2);
        }
        int before = s.getValue(OUTPUT);
        int out = computeOutput(l, p, s);
        if (before == 0 && out > 0) rt[3]++;
        updateOutput(l, p, s, out);
    }

    public static int timeoutTicks(int index) {
        return TIMEOUT_TICKS[Math.max(0, Math.min(TIMEOUT_TICKS.length - 1, index))];
    }

    public static int ageTicks(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < 2 ? 0 : rt[1];
    }

    public static int transitionCount(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < 3 ? 0 : rt[2];
    }

    public static int timeoutCount(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < 4 ? 0 : rt[3];
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) { super.onPlace(s,l,p,o,m); if(l instanceof ServerLevel sl) sl.scheduleTick(p,this,2); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { sample(l,p,s); l.scheduleTick(p,this,2); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                updateOutput(l,p,s,0);
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Watchdog diagnostics reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
