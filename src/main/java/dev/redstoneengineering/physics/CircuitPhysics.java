package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Simplified macroscopic DC models: deliberately below SPICE/MNA complexity. */
public final class CircuitPhysics {
    private CircuitPhysics() {}

    /**
     * Estimate the parallel equivalent resistance of loads reachable from a
     * Copper conductor segment. Junctions are conductors; loads are terminal
     * nodes and never become accidental pass-through wires.
     */
    public static double equivalentLoadResistance(Level level, BlockPos start, int maxNodes) {
        int limit = Math.max(1, Math.min(NetworkKernel.MAX_NODES, maxNodes));
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (level.hasChunkAt(start)) queue.add(start);
        double conductance = 0.0;

        while (!queue.isEmpty() && visited.size() < limit) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            var block = state.getBlock();

            if (block instanceof CopperResistiveLoadBlock) {
                conductance += 1.0 / state.getValue(CopperResistiveLoadBlock.RESISTANCE);
                continue;
            }
            if (block instanceof ElectromagnetBlock) {
                conductance += 1.0 / 4.0;
                continue;
            }
            if (block instanceof ThermalHeaterBlock) {
                conductance += 1.0 / ThermalHeaterBlock.resistance(state);
                continue;
            }

            if (!(block instanceof CopperWireBlock) && !(block instanceof CopperCableJunctionBlock)) {
                continue;
            }
            if (block instanceof ConnectedCableBlock cable && !cable.topologyValid(state)) continue;

            for (Direction direction : Direction.values()) {
                if (block instanceof ConnectedCableBlock
                        && !ConnectedCableBlock.connected(state, direction)) continue;

                BlockPos neighborPos = pos.relative(direction);
                if (!level.hasChunkAt(neighborPos) || visited.contains(neighborPos)) continue;
                BlockState neighbor = level.getBlockState(neighborPos);
                var neighborBlock = neighbor.getBlock();

                boolean relevant = neighborBlock instanceof CopperWireBlock
                        || neighborBlock instanceof CopperCableJunctionBlock
                        || neighborBlock instanceof CopperResistiveLoadBlock
                        || neighborBlock instanceof ElectromagnetBlock
                        || neighborBlock instanceof ThermalHeaterBlock;
                if (!relevant) continue;

                if (neighborBlock instanceof ConnectedCableBlock neighborCable) {
                    if (!neighborCable.topologyValid(neighbor)
                            || !ConnectedCableBlock.connected(neighbor, direction.getOpposite())) continue;
                }
                queue.addLast(neighborPos);
            }
        }

        NetworkKernel.recordScan(level, "copper_load", visited.size(), !queue.isEmpty());
        return conductance <= 0.0 ? 15.0 : 1.0 / conductance;
    }

    public static double current(double voltage,double resistance){return resistance<=0?0:voltage/resistance;}
    public static double power(double voltage,double resistance){double i=current(voltage,resistance);return voltage*i;}
    public static int divider(int vin,double seriesR,double loadR){if(vin<=0)return 0;double v=vin*loadR/(seriesR+loadR);return EngineeringMath.clamp((int)Math.round(v),0,15);}
}
