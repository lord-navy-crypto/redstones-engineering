package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Receiver stores live intensity/channel outside BlockState to prevent state explosion. */
public class OpticalReceiverBlock extends DomainBlock {
    private static final String KEY = "optical_receiver";
    public OpticalReceiverBlock(Properties p) { super(p); }
    @Override public MapCodec<OpticalReceiverBlock> codec() { return RedstoneEngineering.OPTICAL_RECEIVER_CODEC.value(); }

    public static void setOptical(Level level, BlockPos pos, int intensity, int channel, boolean valid) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3);
        rt[0] = valid ? Math.max(0, Math.min(15, intensity)) : 0;
        rt[1] = valid ? Math.max(0, Math.min(15, channel)) : 0;
        rt[2] = valid && rt[0] > 0 ? 1 : 0;
    }
    public static int intensity(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[0]; }
    public static int channel(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[1]; }
    public static boolean valid(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[2] == 1; }

    @Override protected void neighborChanged(BlockState s, Level l, BlockPos p, net.minecraft.world.level.block.Block nb, BlockPos np, boolean moved) { if (l instanceof ServerLevel sl) DomainNetwork.recomputeOptical(sl,p); }
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s,l,p,old,moved); if (l instanceof ServerLevel sl) DomainNetwork.recomputeOptical(sl,p); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) { if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p); super.onRemove(s,l,p,ns,moved); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) pl.displayClientMessage(Component.literal(valid(l,p) ? "Optical receiver | I="+intensity(l,p)+"/15 | channel="+channel(l,p) : "Optical receiver | no valid light"), true);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
