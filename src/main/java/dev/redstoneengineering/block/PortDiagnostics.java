package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Shared player-facing port descriptions for legacy and modern RSE wiring. */
public final class PortDiagnostics {
    public enum Domain {
        INSULATED_REDSTONE("INSULATED_REDSTONE"),
        COPPER("COPPER"),
        LAPIS("LAPIS_PRECISION"),
        QUARTZ("QUARTZ_TIMING"),
        INSTRUMENT("INSTRUMENT_BUS"),
        OPTICAL("OPTICAL"),
        AMETHYST("AMETHYST_RESONANCE"),
        OTHER("OTHER");

        private final String label;
        Domain(String label) { this.label = label; }
        public String label() { return label; }
    }

    private PortDiagnostics() {}

    public static String connectedCable(Level level, BlockPos pos, BlockState state, Domain expected) {
        List<String> ports = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (ConnectedCableBlock.connected(state, direction)) ports.add(shortName(direction));
        }
        return "domain=" + expected.label()
                + " | links=" + (ports.isEmpty() ? "NONE" : String.join(",", ports))
                + mismatchReport(level, pos, expected, false);
    }

    public static String surfaceTrace(Level level, BlockPos pos, BlockState state, Domain expected) {
        List<String> ports = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (SurfaceTraceBlock.connected(state, direction)) ports.add(shortName(direction));
        }
        return "domain=" + expected.label()
                + " | links=" + (ports.isEmpty() ? "NONE" : String.join(",", ports))
                + " | vertical=ISOLATED"
                + mismatchReport(level, pos, expected, true);
    }

    public static String terminal(BlockState state, RedstoneCableTerminalBlock terminal) {
        Direction vanilla = terminal.vanillaSide(state);
        Direction cable = terminal.cableSide(state);
        boolean output = state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE);
        return "mode=" + (output ? "CABLE→VANILLA" : "VANILLA→CABLE")
                + " | " + shortName(vanilla) + "=VANILLA_" + (output ? "OUT" : "IN")
                + " | " + shortName(cable) + "=INSULATED_CABLE_" + (output ? "IN" : "OUT")
                + " | other sides=ISOLATED";
    }

    public static String directionalFlow(Direction facing) {
        return "IN=" + shortName(facing.getOpposite()) + "(BACK) → OUT=" + shortName(facing) + "(FRONT)";
    }

    private static String mismatchReport(Level level, BlockPos pos, Domain expected, boolean horizontalOnly) {
        List<String> mismatches = new ArrayList<>();
        Iterable<Direction> directions = horizontalOnly ? Direction.Plane.HORIZONTAL : List.of(Direction.values());
        for (Direction direction : directions) {
            BlockPos neighborPos = pos.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue;
            Domain neighbor = mediumDomain(level.getBlockState(neighborPos));
            if (neighbor != Domain.OTHER && neighbor != expected) {
                mismatches.add(shortName(direction) + ":" + neighbor.label());
            }
        }
        return mismatches.isEmpty() ? "" : " | DOMAIN_MISMATCH=" + String.join(",", mismatches);
    }

    public static Domain mediumDomain(BlockState state) {
        var block = state.getBlock();
        if (block instanceof RedstoneSignalCableBlock || block instanceof RedstoneCableJunctionBlock) return Domain.INSULATED_REDSTONE;
        if (block instanceof CopperWireBlock || block instanceof CopperCableJunctionBlock) return Domain.COPPER;
        if (block instanceof LapisSignalLineBlock) return Domain.LAPIS;
        if (block instanceof QuartzTimingLineBlock) return Domain.QUARTZ;
        if (block instanceof InstrumentCableBlock) return Domain.INSTRUMENT;
        if (block instanceof OpticalFiberBlock || block instanceof OpticalFiberJunctionBlock) return Domain.OPTICAL;
        if (block instanceof AmethystResonanceDustBlock) return Domain.AMETHYST;
        return Domain.OTHER;
    }

    private static String shortName(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case EAST -> "E";
            case SOUTH -> "S";
            case WEST -> "W";
            case UP -> "U";
            case DOWN -> "D";
        };
    }
}
