package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Timing measurements are runtime data, not BlockState properties. */
public class QuartzStabilityMonitorBlock extends DirectionalDomainBlock {
    private static final String KEY = "quartz_stability";
    public QuartzStabilityMonitorBlock(Properties p) { super(p); }
    @Override public MapCodec<QuartzStabilityMonitorBlock> codec() { return RedstoneEngineering.QUARTZ_STABILITY_MONITOR_CODEC.value(); }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) l.scheduleTick(p, this, 1);
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p);
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        var in = DomainNetwork.sampleQuartz(l, inputPos(p, s));
        int[] rt = RuntimeIntStore.get(l, KEY, p, 4); // prev, elapsed, last, error
        rt[1] = Math.min(255, rt[1] + 1);
        if (in.valid() && in.active() && rt[0] == 0) {
            rt[2] = rt[1];
            int nominal = in.periodTicks();
            rt[3] = Math.min(255, Math.abs(rt[2] - nominal));
            rt[1] = 0;
        }
        rt[0] = in.active() ? 1 : 0;
        l.scheduleTick(p, this, 1);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int[] rt = RuntimeIntStore.get(l, KEY, p, 4);
            pl.displayClientMessage(Component.literal("Quartz stability monitor | measured period=" + rt[2] + "t | nominal error=" + rt[3] + "t"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
