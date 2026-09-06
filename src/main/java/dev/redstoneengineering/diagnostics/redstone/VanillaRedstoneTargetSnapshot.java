package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

/** Immutable component-level presentation snapshot for one vanilla-redstone target. */
public record VanillaRedstoneTargetSnapshot(
        BlockPos target,
        int kind,
        int signalValue,
        int activeState,
        int configuredDelayGameTicks,
        int mode,
        int facing,
        int locked
) {
    public static final int OTHER = 0;
    public static final int DUST = 1;
    public static final int REPEATER = 2;
    public static final int COMPARATOR = 3;
    public static final int OBSERVER = 4;
    public static final int PISTON = 5;
    public static final int DISPENSER_DROPPER = 6;
    public static final int SOURCE = 7;

    public static VanillaRedstoneTargetSnapshot inspect(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        int kind = kindOf(state);
        int signal = state.is(Blocks.REDSTONE_WIRE) ? state.getValue(RedStoneWireBlock.POWER) : activeSignal(state);
        int active = signal < 0 ? -1 : signal > 0 ? 1 : 0;
        int delay = intProperty(state, "delay", -1);
        if (delay >= 0 && state.is(Blocks.REPEATER)) delay *= 2;
        int mode = switch (stringProperty(state, "mode", "")) {
            case "compare" -> 0;
            case "subtract" -> 1;
            default -> -1;
        };
        int facing = directionProperty(state, "facing");
        int locked = boolProperty(state, "locked", -1);
        return new VanillaRedstoneTargetSnapshot(pos.immutable(), kind, signal, active, delay, mode, facing, locked);
    }

    public static int kindOf(BlockState state) {
        if (state.is(Blocks.REDSTONE_WIRE)) return DUST;
        if (state.is(Blocks.REPEATER)) return REPEATER;
        if (state.is(Blocks.COMPARATOR)) return COMPARATOR;
        if (state.is(Blocks.OBSERVER)) return OBSERVER;
        if (state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) return PISTON;
        if (state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)) return DISPENSER_DROPPER;
        if (state.is(Blocks.REDSTONE_BLOCK) || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.LEVER)) return SOURCE;
        return OTHER;
    }

    /** Dust returns its literal 0..15 power; other supported active-state devices return 0/15. */
    public static int observedSignal(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) ? state.getValue(RedStoneWireBlock.POWER) : activeSignal(state);
    }

    private static int activeSignal(BlockState state) {
        int powered = boolProperty(state, "powered", -1);
        if (powered >= 0) return powered == 1 ? 15 : 0;
        int lit = boolProperty(state, "lit", -1);
        if (lit >= 0) return lit == 1 ? 15 : 0;
        int extended = boolProperty(state, "extended", -1);
        if (extended >= 0) return extended == 1 ? 15 : 0;
        return state.is(Blocks.REDSTONE_BLOCK) ? 15 : -1;
    }

    private static int intProperty(BlockState state, String name, int fallback) {
        Comparable<?> value = propertyValue(state, name);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static int boolProperty(BlockState state, String name, int fallback) {
        Comparable<?> value = propertyValue(state, name);
        return value instanceof Boolean b ? (b ? 1 : 0) : fallback;
    }

    private static String stringProperty(BlockState state, String name, String fallback) {
        Comparable<?> value = propertyValue(state, name);
        return value == null ? fallback : value.toString();
    }

    private static int directionProperty(BlockState state, String name) {
        Comparable<?> value = propertyValue(state, name);
        return value instanceof Direction direction ? direction.ordinal() : -1;
    }

    private static Comparable<?> propertyValue(BlockState state, String name) {
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            if (entry.getKey().getName().equals(name)) return entry.getValue();
        }
        return null;
    }
}
