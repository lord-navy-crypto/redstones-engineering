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

public class QuartzClockDividerBlock extends DirectionalDomainBlock {
    public static final IntegerProperty DIV_INDEX = IntegerProperty.create("division", 0, 3);
    private static final String KEY = "quartz_divider";
    public QuartzClockDividerBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(DIV_INDEX, 0)); }
    @Override public MapCodec<QuartzClockDividerBlock> codec() { return RedstoneEngineering.QUARTZ_CLOCK_DIVIDER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(DIV_INDEX); }
    private static int div(int i) { return switch (i) { case 0 -> 2; case 1 -> 4; case 2 -> 8; default -> 16; }; }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s, l, p, old, moved); if (!l.isClientSide) l.scheduleTick(p, this, 1); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) { if (!s.is(ns.getBlock())) { if (l instanceof ServerLevel sl) DomainNetwork.driveQuartz(sl, outputPos(p,s), p, false, 1, false); RuntimeIntStore.remove(l, KEY, p); } super.onRemove(s, l, p, ns, moved); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        var in = DomainNetwork.sampleQuartz(l, inputPos(p, s));
        int[] rt = RuntimeIntStore.get(l, KEY, p, 3); // count, prev, out
        boolean rising = in.valid() && in.active() && rt[1] == 0;
        int d = div(s.getValue(DIV_INDEX));
        if (rising) rt[0] = (rt[0] + 1) % d;
        rt[2] = rt[0] < d / 2 ? 1 : 0;
        rt[1] = in.active() ? 1 : 0;
        int outTicks = Math.min(4096, Math.max(1, in.periodTicks()) * d);
        DomainNetwork.driveQuartz(l, outputPos(p, s), p, rt[2] == 1, outTicks, in.valid());
        l.scheduleTick(p, this, 1);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int i = (s.getValue(DIV_INDEX) + 1) % 4;
            BlockState n = s.setValue(DIV_INDEX, i); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            RuntimeIntStore.get(l, KEY, p, 3)[0] = 0;
            pl.displayClientMessage(Component.literal("Quartz divider | ÷" + div(i)), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
