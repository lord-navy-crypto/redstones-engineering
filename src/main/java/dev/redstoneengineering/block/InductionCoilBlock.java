package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
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

public class InductionCoilBlock extends DirectionalDomainBlock {
    public static final IntegerProperty TURNS = IntegerProperty.create("turns", 1, 4);
    private static final String KEY = "induction_coil";

    public InductionCoilBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(TURNS, 2)); }
    @Override public MapCodec<InductionCoilBlock> codec() { return RedstoneEngineering.INDUCTION_COIL_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(TURNS); }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) {
            RuntimeIntStore.get(l, KEY, p, 2)[0] = MagneticPhysics.fieldAt(l, p, 6);
            l.scheduleTick(p, this, 2);
        }
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) {
            if (l instanceof ServerLevel sl) DomainNetwork.driveCopper(sl, outputPos(p,s), p, 0);
            RuntimeIntStore.remove(l, KEY, p);
        }
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, 2); // previous flux, emf
        int flux = MagneticPhysics.fieldAt(l, p, 6);
        int delta = Math.abs(flux - rt[0]);
        rt[0] = flux;
        rt[1] = EngineeringMath.clamp(delta * s.getValue(TURNS), 0, 15);
        DomainNetwork.driveCopper(l, outputPos(p, s), p, rt[1]);
        l.scheduleTick(p, this, 2);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 2)[1];
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int t = s.getValue(TURNS); t = t >= 4 ? 1 : t + 1;
            BlockState n = s.setValue(TURNS, t); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            int emf = RuntimeIntStore.get(l, KEY, p, 2)[1];
            pl.displayClientMessage(Component.literal("Induction coil | turns-index=" + t + " | |emf| ∝ N·|ΔΦ/Δt| | current emf=" + emf + "/15"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
