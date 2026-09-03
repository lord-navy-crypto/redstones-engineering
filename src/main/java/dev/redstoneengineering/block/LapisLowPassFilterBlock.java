package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
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

/** First-order discrete low-pass filter with runtime output storage. */
public class LapisLowPassFilterBlock extends DirectionalDomainBlock {
    public static final IntegerProperty ALPHA = IntegerProperty.create("alpha", 0, 3);
    private static final String KEY = "lapis_lpf";

    public LapisLowPassFilterBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(ALPHA, 1));
    }

    @Override public MapCodec<LapisLowPassFilterBlock> codec() { return RedstoneEngineering.LAPIS_LOW_PASS_FILTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ALPHA); }

    private static double alpha(int i) { return switch (i) { case 0 -> 0.10; case 1 -> 0.25; case 2 -> 0.50; default -> 0.75; }; }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) l.scheduleTick(p, this, 2);
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) {
            if (l instanceof ServerLevel sl) DomainNetwork.driveLapis(sl, outputPos(p,s), p, 0, false);
            RuntimeIntStore.remove(l, KEY, p);
        }
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        var in = DomainNetwork.sampleLapis(l, inputPos(p, s));
        int[] rt = RuntimeIntStore.get(l, KEY, p, 2); // output, valid
        if (in.valid()) {
            int previous = rt[1] == 0 ? in.value() : rt[0];
            rt[0] = EngineeringMath.clamp((int)Math.round(previous + alpha(s.getValue(ALPHA)) * (in.value() - previous)), 0, 100);
            rt[1] = 1;
            DomainNetwork.driveLapis(l, outputPos(p, s), p, rt[0], true);
        } else {
            rt[1] = 0;
            DomainNetwork.driveLapis(l, outputPos(p, s), p, 0, false);
        }
        l.scheduleTick(p, this, 2);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int i = (s.getValue(ALPHA) + 1) % 4;
            BlockState n = s.setValue(ALPHA, i);
            l.setBlock(p, n, Block.UPDATE_CLIENTS);
            int[] rt = RuntimeIntStore.get(l, KEY, p, 2);
            pl.displayClientMessage(Component.literal("Lapis low-pass | alpha=" + alpha(i) + " | output=" + String.format("%.2f", rt[0] / 100.0)), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
