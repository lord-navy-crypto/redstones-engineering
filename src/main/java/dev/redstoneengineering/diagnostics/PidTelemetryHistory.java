package dev.redstoneengineering.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded, transient, server-owned PID trend evidence.
 *
 * <p>This store observes completed controller cycles only. It never feeds values back into the
 * controller and therefore cannot alter PID physics, tuning, update order or redstone output.</p>
 */
public final class PidTelemetryHistory {
    public static final int DISPLAY_SAMPLES = 24;

    private static final Map<Level, Map<Long, History>> DATA = new WeakHashMap<>();

    private PidTelemetryHistory() {}

    /** Records one completed authoritative controller cycle. */
    public static void capture(Level level, BlockPos pos, CommissioningSnapshot snapshot, long gameTime) {
        if (level.isClientSide || snapshot == null || !snapshot.available()) return;
        captureSample(
                level,
                pos,
                gameTime,
                snapshot.setpoint(),
                snapshot.processValue(),
                snapshot.controlOutput(),
                snapshot.error(),
                snapshot.saturationEvents()
        );
    }

    /**
     * Bounded capture primitive used by production and deterministic GameTest evidence.
     * saturationEvents is the authoritative cumulative anti-windup event counter.
     */
    public static synchronized void captureSample(
            Level level,
            BlockPos pos,
            long gameTime,
            int setpoint,
            int processValue,
            int controlOutput,
            int error,
            int saturationEvents
    ) {
        if (level.isClientSide) return;
        History history = DATA
                .computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos.asLong(), ignored -> new History());
        history.capture(gameTime, setpoint, processValue, controlOutput, error, saturationEvents);
    }

    /** Read-only chronological snapshot padded on the left with N/A samples. */
    public static synchronized Snapshot snapshot(Level level, BlockPos pos) {
        Map<Long, History> byPos = DATA.get(level);
        History history = byPos == null ? null : byPos.get(pos.asLong());
        return history == null ? Snapshot.empty() : history.snapshot();
    }

    public static synchronized void clear(Level level, BlockPos pos) {
        Map<Long, History> byPos = DATA.get(level);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) DATA.remove(level);
    }

    public record Snapshot(
            int count,
            long latestGameTime,
            int timeSpanTicks,
            int saturationHits,
            int maxAbsError,
            int meanAbsError100,
            int recentAbsError100,
            int[] setpoints,
            int[] processValues,
            int[] outputs,
            int[] errors,
            int[] saturationOutputs,
            long[] sampleTimes
    ) {
        private static Snapshot empty() {
            int[] analog = new int[DISPLAY_SAMPLES];
            int[] errors = new int[DISPLAY_SAMPLES];
            long[] times = new long[DISPLAY_SAMPLES];
            Arrays.fill(analog, -1);
            Arrays.fill(errors, -16);
            Arrays.fill(times, -1L);
            return new Snapshot(
                    0, -1L, 0, 0, 0, 0, -1,
                    analog.clone(), analog.clone(), analog.clone(), errors, analog.clone(), times
            );
        }
    }

    private static final class History {
        private final long[] times = new long[DISPLAY_SAMPLES];
        private final int[] setpoints = new int[DISPLAY_SAMPLES];
        private final int[] processValues = new int[DISPLAY_SAMPLES];
        private final int[] outputs = new int[DISPLAY_SAMPLES];
        private final int[] errors = new int[DISPLAY_SAMPLES];
        private final int[] saturationOutputs = new int[DISPLAY_SAMPLES];
        private int write;
        private int count;
        private int lastSaturationEvents = -1;
        private long lastGameTime = Long.MIN_VALUE;

        private History() {
            Arrays.fill(times, -1L);
            Arrays.fill(setpoints, -1);
            Arrays.fill(processValues, -1);
            Arrays.fill(outputs, -1);
            Arrays.fill(errors, -16);
            Arrays.fill(saturationOutputs, -1);
        }

        private void capture(
                long gameTime,
                int setpoint,
                int processValue,
                int controlOutput,
                int error,
                int saturationEvents
        ) {
            setpoint = clamp(setpoint, 0, 15);
            processValue = clamp(processValue, 0, 15);
            controlOutput = clamp(controlOutput, 0, 15);
            error = clamp(error, -15, 15);
            saturationEvents = Math.max(0, saturationEvents);

            boolean newSaturation = lastSaturationEvents >= 0 && saturationEvents > lastSaturationEvents;
            lastSaturationEvents = saturationEvents;

            // A repeated controller evaluation in the same game tick replaces the latest point
            // rather than inventing extra time resolution.
            if (count > 0 && gameTime == lastGameTime) {
                int slot = Math.floorMod(write - 1, DISPLAY_SAMPLES);
                setpoints[slot] = setpoint;
                processValues[slot] = processValue;
                outputs[slot] = controlOutput;
                errors[slot] = error;
                if (newSaturation) saturationOutputs[slot] = controlOutput;
                return;
            }

            int slot = write;
            times[slot] = gameTime;
            setpoints[slot] = setpoint;
            processValues[slot] = processValue;
            outputs[slot] = controlOutput;
            errors[slot] = error;
            saturationOutputs[slot] = newSaturation ? controlOutput : -1;
            write = (write + 1) % DISPLAY_SAMPLES;
            count = Math.min(DISPLAY_SAMPLES, count + 1);
            lastGameTime = gameTime;
        }

        private Snapshot snapshot() {
            int[] outSp = filledAnalog();
            int[] outPv = filledAnalog();
            int[] outOut = filledAnalog();
            int[] outErr = new int[DISPLAY_SAMPLES];
            int[] outSat = filledAnalog();
            long[] outTimes = new long[DISPLAY_SAMPLES];
            Arrays.fill(outErr, -16);
            Arrays.fill(outTimes, -1L);

            int padding = DISPLAY_SAMPLES - count;
            int saturationHits = 0;
            int absErrorSum = 0;
            int maxAbsError = 0;
            int recentAbsErrorSum = 0;
            int recentCount = Math.min(5, count);
            for (int i = 0; i < count; i++) {
                int source = Math.floorMod(write - count + i, DISPLAY_SAMPLES);
                int target = padding + i;
                outSp[target] = setpoints[source];
                outPv[target] = processValues[source];
                outOut[target] = outputs[source];
                outErr[target] = errors[source];
                outSat[target] = saturationOutputs[source];
                outTimes[target] = times[source];

                int abs = Math.abs(errors[source]);
                absErrorSum += abs;
                maxAbsError = Math.max(maxAbsError, abs);
                if (i >= count - recentCount) recentAbsErrorSum += abs;
                if (saturationOutputs[source] >= 0) saturationHits++;
            }

            long first = count == 0 ? -1L : outTimes[padding];
            long latest = count == 0 ? -1L : outTimes[DISPLAY_SAMPLES - 1];
            int span = count < 2 ? 0 : durationTicks(latest - first);
            int meanAbsError100 = count == 0 ? 0 : (absErrorSum * 100 + count / 2) / count;
            int recentAbsError100 = count < 5 ? -1 : (recentAbsErrorSum * 100 + recentCount / 2) / recentCount;
            return new Snapshot(
                    count,
                    latest,
                    span,
                    saturationHits,
                    maxAbsError,
                    meanAbsError100,
                    recentAbsError100,
                    outSp,
                    outPv,
                    outOut,
                    outErr,
                    outSat,
                    outTimes
            );
        }

        private static int[] filledAnalog() {
            int[] values = new int[DISPLAY_SAMPLES];
            Arrays.fill(values, -1);
            return values;
        }
    }

    private static int durationTicks(long ticks) {
        if (ticks <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
