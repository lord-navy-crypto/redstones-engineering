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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class QuartzPhaseDelayBlock extends DirectionalDomainBlock {
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 8);
    private static final String KEY = "quartz_phase_delay";
    public QuartzPhaseDelayBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(DELAY, 2)); }
    @Override public MapCodec<QuartzPhaseDelayBlock> codec() { return RedstoneEngineering.QUARTZ_PHASE_DELAY_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(DELAY); }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s, l, p, old, moved); if (!l.isClientSide) l.scheduleTick(p, this, 1); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) { if (!s.is(ns.getBlock())) { if (l instanceof ServerLevel sl) DomainNetwork.driveQuartz(sl, outputPos(p,s), p, false, 1, false); RuntimeIntStore.remove(l, KEY, p); } super.onRemove(s, l, p, ns, moved); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        var in = DomainNetwork.sampleQuartz(l, inputPos(p, s));
        int[] rt = RuntimeIntStore.get(l, KEY, p, 3); // pending, prev, out
        rt[2] = 0;
        if (rt[0] > 0) { rt[0]--; if (rt[0] == 0) rt[2] = 1; }
        if (in.valid() && in.active() && rt[1] == 0 && rt[0] == 0 && rt[2] == 0) rt[0] = s.getValue(DELAY);
        rt[1] = in.active() ? 1 : 0;
        DomainNetwork.driveQuartz(l, outputPos(p, s), p, rt[2] == 1, in.periodTicks(), in.valid());
        l.scheduleTick(p, this, 1);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int d = s.getValue(DELAY); d = d >= 8 ? 1 : d + 1;
            BlockState n = s.setValue(DELAY, d); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            pl.displayClientMessage(Component.literal("Quartz edge-delay | rising edge delay=" + d + " ticks"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
