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

/** Pulse activity is transient runtime data; frequency/amplitude remain persistent configuration. */
public class AmethystResonatorBlock extends DomainBlock {
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency",1,15);
    public static final IntegerProperty AMPLITUDE = IntegerProperty.create("amplitude",1,15);
    private static final String KEY = "amethyst_resonator";
    public AmethystResonatorBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FREQUENCY,1).setValue(AMPLITUDE,12)); }
    @Override public MapCodec<AmethystResonatorBlock> codec() { return RedstoneEngineering.AMETHYST_RESONATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FREQUENCY, AMPLITUDE); }
    public static boolean isActive(Level l, BlockPos p) { return RuntimeIntStore.get(l, KEY, p, 1)[0] == 1; }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) { if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l,KEY,p); if (l instanceof ServerLevel sl && !s.is(ns.getBlock())) DomainNetwork.recomputeAmethyst(sl,p); super.onRemove(s,l,p,ns,moved); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { if (isActive(l,p)) { RuntimeIntStore.get(l,KEY,p,1)[0]=0; DomainNetwork.recomputeAmethyst(l,p); } }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            BlockState n = s;
            if (pl.isShiftKeyDown()) { RuntimeIntStore.get(l,KEY,p,1)[0]=1; l.scheduleTick(p,this,4); }
            else if (hit.getDirection() == net.minecraft.core.Direction.UP || hit.getDirection() == net.minecraft.core.Direction.DOWN) { int a=s.getValue(AMPLITUDE); n=s.setValue(AMPLITUDE,a>=15?1:a+1); l.setBlock(p,n,Block.UPDATE_CLIENTS); }
            else { int f=s.getValue(FREQUENCY); n=s.setValue(FREQUENCY,f>=15?1:f+1); l.setBlock(p,n,Block.UPDATE_CLIENTS); }
            if (l instanceof ServerLevel sl) DomainNetwork.recomputeAmethyst(sl,p);
            pl.displayClientMessage(Component.literal("Amethyst resonator | f="+n.getValue(FREQUENCY)+" | amplitude="+n.getValue(AMPLITUDE)+(isActive(l,p)?" | PULSE":"")),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
