package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.CircuitPhysics;
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

public class CopperSeriesResistorBlock extends DirectionalDomainBlock {
    public static final IntegerProperty RESISTANCE = IntegerProperty.create("resistance", 1, 15);
    private static final String KEY = "copper_series_resistor";
    public CopperSeriesResistorBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(RESISTANCE, 4)); }
    @Override public MapCodec<CopperSeriesResistorBlock> codec() { return RedstoneEngineering.COPPER_SERIES_RESISTOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(RESISTANCE); }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s, l, p, old, moved); if (!l.isClientSide) l.scheduleTick(p, this, 2); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) { if (!s.is(ns.getBlock())) { if (l instanceof ServerLevel sl) DomainNetwork.driveCopper(sl, outputPos(p,s), p, 0); RuntimeIntStore.remove(l, KEY, p); } super.onRemove(s, l, p, ns, moved); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int vin = DomainNetwork.sampleCopperVoltage(l, inputPos(p, s));
        double loadR = CircuitPhysics.equivalentLoadResistance(l, outputPos(p, s), 128);
        int out = CircuitPhysics.divider(vin, s.getValue(RESISTANCE), loadR);
        RuntimeIntStore.get(l, KEY, p, 1)[0] = out;
        DomainNetwork.driveCopper(l, outputPos(p, s), p, out);
        l.scheduleTick(p, this, 2);
    }
    public static int outputVoltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide) {
            int rr = s.getValue(RESISTANCE); rr = rr >= 15 ? 1 : rr + 1;
            BlockState n = s.setValue(RESISTANCE, rr); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            double load = CircuitPhysics.equivalentLoadResistance(l, outputPos(p, n), 128);
            int out = RuntimeIntStore.get(l, KEY, p, 1)[0];
            pl.displayClientMessage(Component.literal(String.format("Copper series resistor | Rs=%d | estimated Rload=%.2f | Vout=%d", rr, load, out)), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
