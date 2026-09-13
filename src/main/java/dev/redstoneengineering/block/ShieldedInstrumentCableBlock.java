package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Lower-noise measurement routing. Shielding is treated as commissioning evidence rather than
 * fabricated random noise: the instrument network tracks how much of the physical route is
 * actually shielded and exposes that coverage to diagnostics.
 */
public class ShieldedInstrumentCableBlock extends InstrumentCableBlock {
    public ShieldedInstrumentCableBlock(Properties p) {
        super(p);
    }

    @Override
    public MapCodec<ShieldedInstrumentCableBlock> codec() {
        return RedstoneEngineering.SHIELDED_INSTRUMENT_CABLE_CODEC.value();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            InstrumentNetwork.ProbeSnapshot bus = InstrumentNetwork.scan(level, pos);
            player.displayClientMessage(Component.literal(
                    "Shielded Instrument Bus"
                            + " | shielding=" + bus.shieldingIntegrity()
                            + " | coverage=" + bus.shieldingCoveragePercent() + "%"
                            + " | shielded=" + bus.shieldedCableNodes() + "/" + bus.cableNodes()
                            + " | channels=" + bus.validChannels() + "/" + bus.activeChannels()
                            + " valid/active"
                            + " | integrity=" + bus.integrity()
            ), true);
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
