package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
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

/** Heartbeat watchdog with configurable timeout. Any input transition resets the timer. */
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

    @Override protected int computeOutput(Level l, BlockPos p, BlockState s) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, 4);
        return rt[1] >= TIMEOUT_TICKS[s.getValue(TIMEOUT)] ? 15 : 0;
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
        int out = computeOutput(l,p,s);
        if (before == 0 && out > 0) rt[3]++;
        updateOutput(l, p, s, out);
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) { super.onPlace(s,l,p,o,m); if(l instanceof ServerLevel sl) sl.scheduleTick(p,this,2); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { sample(l,p,s); l.scheduleTick(p,this,2); }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                updateOutput(l,p,s,0);
                pl.displayClientMessage(Component.literal("Watchdog diagnostics reset"), true);
            } else {
                int next=(s.getValue(TIMEOUT)+1)%4; BlockState ns=s.setValue(TIMEOUT,next); l.setBlock(p,ns,Block.UPDATE_CLIENTS);
                int[] rt=RuntimeIntStore.get(l,KEY,p,4);
                pl.displayClientMessage(Component.literal("Watchdog timeout="+TIMEOUT_TICKS[next]+"t | age="+rt[1]+"t transitions="+rt[2]+" timeouts="+rt[3]),true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
