package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Shared route actions for true one-input/one-output directional devices.
 *
 * <p>The formal Engineering Port contract is authoritative: endpoint controls are exposed only
 * when the current device declares exactly one receive port and one transmit port and those ports
 * match the underlying explicit INPUT_FACING/FACING route. Multipoint, source, sink, observer and
 * splitter topologies are intentionally excluded.</p>
 */
public final class SeriesRouteActions {
    public static final int BUTTON_INPUT_LEFT = 100;
    public static final int BUTTON_INPUT_RIGHT = 101;
    public static final int BUTTON_OUTPUT_LEFT = 102;
    public static final int BUTTON_OUTPUT_RIGHT = 103;

    private SeriesRouteActions() {
    }

    public static boolean isEndpointAction(int id) {
        return id >= BUTTON_INPUT_LEFT && id <= BUTTON_OUTPUT_RIGHT;
    }

    public static boolean supports(EngineeringDeviceMenu menu) {
        return supports(menu.level.getBlockState(menu.blockPos));
    }

    static boolean supports(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof EngineeringPortProvider provider)) return false;
        if (!(block instanceof DirectionalSignalBlock) && !(block instanceof DirectionalDomainBlock)) return false;

        List<EngineeringPort> ports = provider.engineeringPorts(state);
        int receivers = 0;
        int transmitters = 0;
        Direction receive = null;
        Direction transmit = null;
        for (EngineeringPort port : ports) {
            if (port.canReceive()) {
                receivers++;
                receive = port.side();
            }
            if (port.canTransmit()) {
                transmitters++;
                transmit = port.side();
            }
        }
        if (receivers != 1 || transmitters != 1 || receive == null || transmit == null || receive == transmit) {
            return false;
        }

        if (block instanceof DirectionalSignalBlock) {
            return receive == DirectionalSignalBlock.seriesInputSide(state)
                    && transmit == DirectionalSignalBlock.seriesOutputSide(state);
        }
        return receive == DirectionalDomainBlock.seriesInputSide(state)
                && transmit == DirectionalDomainBlock.seriesOutputSide(state);
    }

    /** Handles only endpoint actions; existing menu-specific ALL-route buttons remain compatible. */
    public static boolean handle(EngineeringDeviceMenu menu, Player player, int id) {
        if (!isEndpointAction(id)) return false;
        if (menu.level.isClientSide) return true;
        if (!menu.stillValid(player) || !supports(menu)) return false;

        BlockState state = menu.level.getBlockState(menu.blockPos);
        boolean clockwise = id == BUTTON_INPUT_RIGHT || id == BUTTON_OUTPUT_RIGHT;
        boolean changed;
        if (state.getBlock() instanceof DirectionalSignalBlock) {
            changed = switch (id) {
                case BUTTON_INPUT_LEFT, BUTTON_INPUT_RIGHT ->
                        DirectionalSignalBlock.rotateSeriesInput(menu.level, menu.blockPos, clockwise);
                case BUTTON_OUTPUT_LEFT, BUTTON_OUTPUT_RIGHT ->
                        DirectionalSignalBlock.rotateSeriesOutput(menu.level, menu.blockPos, clockwise);
                default -> false;
            };
        } else if (state.getBlock() instanceof DirectionalDomainBlock) {
            changed = switch (id) {
                case BUTTON_INPUT_LEFT, BUTTON_INPUT_RIGHT ->
                        DirectionalDomainBlock.rotateSeriesInput(menu.level, menu.blockPos, clockwise);
                case BUTTON_OUTPUT_LEFT, BUTTON_OUTPUT_RIGHT ->
                        DirectionalDomainBlock.rotateSeriesOutput(menu.level, menu.blockPos, clockwise);
                default -> false;
            };
        } else {
            return false;
        }

        if (changed) menu.broadcastChanges();
        return changed;
    }
}
