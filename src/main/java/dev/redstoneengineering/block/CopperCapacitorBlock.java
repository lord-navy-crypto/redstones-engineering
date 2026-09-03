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

public class CopperCapacitorBlock extends DirectionalDomainBlock {
    public static final IntegerProperty C_INDEX = IntegerProperty.create("capacitance", 0, 3);
    private static final String KEY = "copper_capacitor";

    public CopperCapacitorBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(C_INDEX, 1)); }
    @Override public MapCodec<CopperCapacitorBlock> codec() { return RedstoneEngineering.COPPER_CAPACITOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(C_INDEX); }
    private static int tau(int i) { return switch (i) { case 0 -> 2; case 1 -> 4; case 2 -> 8; default -> 16; }; }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) l.scheduleTick(p, this, 2);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) {
            if (l instanceof ServerLevel sl) DomainNetwork.driveCopper(sl, outputPos(p,s), p, 0);
            RuntimeIntStore.remove(l, KEY, p);
        }
        super.onRemove(s, l, p, ns, moved);
    }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int vin = DomainNetwork.sampleCopperVoltage(l, inputPos(p, s));
        int target = (int)Math.round(vin / 15.0 * 100.0);
        int[] rt = RuntimeIntStore.get(l, KEY, p, 1);
        int delta = target - rt[0];
        int step = delta == 0 ? 0 : (int)Math.copySign(Math.max(1, Math.abs(delta) / tau(s.getValue(C_INDEX))), delta);
        rt[0] = EngineeringMath.clamp(rt[0] + step, 0, 100);
        int vout = EngineeringMath.clamp((int)Math.round(rt[0] / 100.0 * 15.0), 0, 15);
        DomainNetwork.driveCopper(l, outputPos(p, s), p, vout);
        l.scheduleTick(p, this, 2);
    }
    public static int outputVoltage(Level level, BlockPos pos) {
        int charge = RuntimeIntStore.get(level, KEY, pos, 1)[0];
        return EngineeringMath.clamp((int)Math.round(charge / 100.0 * 15.0), 0, 15);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int ci = (s.getValue(C_INDEX) + 1) % 4;
            BlockState n = s.setValue(C_INDEX, ci); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            int charge = RuntimeIntStore.get(l, KEY, p, 1)[0];
            pl.displayClientMessage(Component.literal("Copper capacitor | C-index=" + (ci + 1) + " | RC time-constant proxy=" + tau(ci) + " ticks | charge=" + charge + "%"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
