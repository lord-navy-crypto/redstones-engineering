package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Calorimeter measurement history is runtime data, not a combinatorial BlockState. */
public class ThermalCalorimeterBlock extends DomainBlock {
    private static final String KEY = "thermal_calorimeter";
    public ThermalCalorimeterBlock(Properties properties) { super(properties); }
    @Override public MapCodec<ThermalCalorimeterBlock> codec() { return RedstoneEngineering.THERMAL_CALORIMETER_CODEC.value(); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            RuntimeIntStore.get(level, KEY, pos, 2)[0] = sample(level, pos).temperature();
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p);
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Sample sample = sample(level, pos);
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 2); // last T, delta encoded +100
        int delta = sample.temperature() - rt[0];
        rt[0] = sample.temperature();
        rt[1] = Math.max(0, Math.min(200, delta + 100));
        level.scheduleTick(pos, this, 20);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Sample sample = sample(level, pos);
            int[] rt = RuntimeIntStore.get(level, KEY, pos, 2);
            int delta = rt[1] == 0 ? 0 : rt[1] - 100;
            int relativeHeat = delta * sample.heatCapacity();
            player.displayClientMessage(Component.literal("Thermal calorimeter | T=" + sample.temperature() + "/100 | ΔT/20t=" + delta
                    + " | heat-capacity index=" + sample.heatCapacity() + " | relative C·ΔT=" + relativeHeat), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Sample sample(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState s = level.getBlockState(pos.relative(d));
            if (s.getBlock() instanceof ThermalMassBlock) return new Sample(s.getValue(ThermalMassBlock.TEMPERATURE), s.getValue(ThermalMassBlock.HEAT_CAPACITY));
        }
        return new Sample(ThermalPhysics.environmentTarget(level, pos), 1);
    }
    private record Sample(int temperature, int heatCapacity) {}
}
