package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Six-direction measurement bus carrying probe channels rather than redstone power. */
public class InstrumentCableBlock extends ConnectedCableBlock {
    public InstrumentCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends InstrumentCableBlock> codec() {
        return RedstoneEngineering.INSTRUMENT_CABLE_CODEC.value();
    }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor) {
        return TransmissionTopology.instrumentPort(neighbor, direction);
    }

    /** Instrument buses are deliberately multi-drop; unlike power cable, a branch is valid. */
    @Override
    protected int maxConnections() {
        return 6;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            String type = this instanceof ShieldedInstrumentCableBlock ? "Shielded Instrument Bus" : "Instrument Bus Cable";
            player.displayClientMessage(Component.literal(
                    type + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSTRUMENT)
                            + " | ports=" + connectionCount(state)
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
