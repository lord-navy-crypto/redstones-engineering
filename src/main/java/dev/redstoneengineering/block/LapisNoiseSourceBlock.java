package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.FaultInjectionModel;
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

/**
 * Repeatable Lapis-domain noise source used both as a source and as a commissioning fault injector.
 * Configuration stays small; the changing sample is transient runtime data.
 */
public class LapisNoiseSourceBlock extends DomainBlock {
    // 21 baseline steps -> 0..100 in increments of 5.
    public static final IntegerProperty BASELINE = IntegerProperty.create("baseline", 0, 20);
    // 11 noise steps -> ±0..20 in increments of 2.
    public static final IntegerProperty NOISE = IntegerProperty.create("noise", 0, 10);
    private static final String KEY = "lapis_noise";

    public LapisNoiseSourceBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(BASELINE, 10).setValue(NOISE, 3));
    }

    @Override public MapCodec<LapisNoiseSourceBlock> codec() { return RedstoneEngineering.LAPIS_NOISE_SOURCE_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(BASELINE, NOISE); }

    public static int currentValue(Level level, BlockPos pos, BlockState state) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 1);
        if (rt[0] == 0 && state.getValue(BASELINE) > 0) rt[0] = state.getValue(BASELINE) * 5;
        return EngineeringMath.clamp(rt[0], 0, 100);
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) {
            RuntimeIntStore.get(l, KEY, p, 1)[0] = s.getValue(BASELINE) * 5;
            l.scheduleTick(p, this, 4);
        }
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p);
        if (l instanceof ServerLevel sl && !s.is(ns.getBlock())) DomainNetwork.recomputeLapis(sl, p);
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int base = s.getValue(BASELINE) * 5;
        int noise = s.getValue(NOISE) * 2;
        // Position + game time gives repeatable fault sequences without introducing persistent state.
        int sample = FaultInjectionModel.addDeterministicNoise(base, noise, l.getGameTime(), p.asLong(), 0, 100);
        RuntimeIntStore.get(l, KEY, p, 1)[0] = sample;
        DomainNetwork.recomputeLapis(l, p);
        l.scheduleTick(p, this, 4);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            BlockState n;
            if (pl.isShiftKeyDown()) {
                int q = s.getValue(NOISE);
                n = s.setValue(NOISE, q >= 10 ? 0 : q + 1);
            } else {
                int b = s.getValue(BASELINE);
                n = s.setValue(BASELINE, b >= 20 ? 0 : b + 1);
            }
            l.setBlock(p, n, Block.UPDATE_CLIENTS);
            int current = currentValue(l, p, n);
            pl.displayClientMessage(Component.literal(
                    "Fault injection [NOISE] | Lapis baseline=" + String.format("%.2f", n.getValue(BASELINE) * 0.05)
                            + " | noise=±" + String.format("%.2f", n.getValue(NOISE) * 0.02)
                            + " | now=" + String.format("%.2f", current / 100.0)
                            + " | shift-click=noise, click=baseline"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
