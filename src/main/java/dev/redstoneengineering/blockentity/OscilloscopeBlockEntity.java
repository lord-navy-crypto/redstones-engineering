package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Two-channel scope with trigger, cursors, authoritative timing and capture-quality metrics. */
public class OscilloscopeBlockEntity extends BlockEntity {
    private static final int CHANNELS = 2;
    private static final int CAPACITY = 32;
    public static final int DISPLAY_SAMPLES = 16;
    public static final int SAMPLE_PERIOD_TICKS = 2;

    private final int[][] history = new int[CHANNELS][CAPACITY];
    private final long[] sampleTimes = new long[CAPACITY];
    private int index = 0;
    private int count = 0;
    private int triggerLevel = 8;
    private int triggerChannel = 0;
    private int triggerMode = 1; // 0 free, 1 rising, 2 falling
    private boolean armed = true;
    private boolean triggered = false;
    private int cursorA = 0;
    private int cursorB = 8;
    private int lastA = -1;
    private int lastB = -1;
    private int samplesSinceTrigger = 0;

    public OscilloscopeBlockEntity(BlockPos pos, BlockState state) {
        super(RedstoneEngineering.OSCILLOSCOPE_BLOCK_ENTITY.get(), pos, state);
        clearHistoryOnly();
    }

    /** Records one simultaneous two-channel sample at the real logical-server gameTime. */
    public void addSample(long gameTime, int a, int b) {
        int na = normalize(a);
        int nb = normalize(b);
        int current = triggerChannel == 0 ? na : nb;
        int before = triggerChannel == 0 ? lastA : lastB;
        boolean edge = before >= 0 && current >= 0
                && ((triggerMode == 1 && before < triggerLevel && current >= triggerLevel)
                || (triggerMode == 2 && before >= triggerLevel && current < triggerLevel));

        if (triggerMode == 0 || armed || triggered) {
            history[0][index] = na;
            history[1][index] = nb;
            sampleTimes[index] = Math.max(0L, gameTime);
            if (armed && triggerMode != 0 && edge) {
                triggered = true;
                armed = false;
                samplesSinceTrigger = 0;
            }
            if (triggered) samplesSinceTrigger++;
            index = (index + 1) % CAPACITY;
            count = Math.min(CAPACITY, count + 1);
            if (triggered && samplesSinceTrigger >= CAPACITY / 2) triggered = false;
        }

        lastA = na;
        lastB = nb;
        setChanged();
    }

    /** Compatibility helper for tests/tools; production sampling passes gameTime explicitly. */
    public void addSample(int a, int b) {
        long gameTime;
        if (getLevel() != null) {
            gameTime = getLevel().getGameTime();
        } else {
            long latest = latestSampleGameTime();
            gameTime = latest < 0 ? 0L : latest + SAMPLE_PERIOD_TICKS;
        }
        addSample(gameTime, a, b);
    }

    private static int normalize(int value) {
        return value < 0 ? -1 : Math.max(0, Math.min(15, value));
    }

    private static boolean validChannel(int channel) {
        return channel >= 0 && channel < CHANNELS;
    }

    private void clearHistoryOnly() {
        for (int c = 0; c < CHANNELS; c++) {
            for (int i = 0; i < CAPACITY; i++) history[c][i] = -1;
        }
        for (int i = 0; i < CAPACITY; i++) sampleTimes[i] = -1L;
        index = 0;
        count = 0;
        lastA = -1;
        lastB = -1;
        samplesSinceTrigger = 0;
    }

    public void clear() {
        clearHistoryOnly();
        armed = true;
        triggered = false;
        setChanged();
    }

    public void arm() {
        armed = true;
        triggered = false;
        samplesSinceTrigger = 0;
        setChanged();
    }

    public void cycleTriggerMode() {
        triggerMode = (triggerMode + 1) % 3;
        arm();
    }

    public void cycleTriggerChannel() {
        triggerChannel = (triggerChannel + 1) % 2;
        arm();
    }

    public void cycleTriggerLevel() {
        triggerLevel = triggerLevel >= 15 ? 1 : triggerLevel + 1;
        arm();
    }

    public void moveCursorA() {
        cursorA = (cursorA + 1) % DISPLAY_SAMPLES;
        setChanged();
    }

    public void moveCursorB() {
        cursorB = (cursorB + 1) % DISPLAY_SAMPLES;
        setChanged();
    }

    public int triggerLevel() {
        return triggerLevel;
    }

    public int triggerChannel() {
        return triggerChannel;
    }

    public int triggerMode() {
        return triggerMode;
    }

    public boolean armed() {
        return armed;
    }

    public boolean triggered() {
        return triggered;
    }

    public int cursorA() {
        return cursorA;
    }

    public int cursorB() {
        return cursorB;
    }

    /**
     * Returns one of the last 16 chronological display samples. Missing warm-up slots are -1.
     * This bounded accessor lets the server menu synchronize the real capture without exposing
     * the internal 32-sample ring buffer to client code.
     */
    public int displaySample(int channel, int slot) {
        if (!validChannel(channel) || slot < 0 || slot >= DISPLAY_SAMPLES) return -1;
        int[] values = recent(channel);
        int available = Math.min(DISPLAY_SAMPLES, values.length);
        int padding = DISPLAY_SAMPLES - available;
        if (slot < padding) return -1;
        int source = values.length - available + (slot - padding);
        return values[source];
    }

    /** Returns the exact server gameTime paired with a displayed sample, or -1 during warm-up. */
    public long displaySampleGameTime(int slot) {
        if (slot < 0 || slot >= DISPLAY_SAMPLES) return -1L;
        long[] times = recentTimes();
        int available = Math.min(DISPLAY_SAMPLES, times.length);
        int padding = DISPLAY_SAMPLES - available;
        if (slot < padding) return -1L;
        int source = times.length - available + (slot - padding);
        return times[source];
    }

    public long latestSampleGameTime() {
        if (count == 0) return -1L;
        return sampleTimes[(index - 1 + CAPACITY) % CAPACITY];
    }

    /** Compact synchronization form: age from the latest authoritative sample in ticks. */
    public int displaySampleAgeTicks(int slot) {
        long latest = latestSampleGameTime();
        long sample = displaySampleGameTime(slot);
        if (latest < 0 || sample < 0 || sample > latest) return -1;
        long age = latest - sample;
        return age > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) age;
    }

    public String triggerStatus() {
        String mode = triggerMode == 0 ? "FREE" : triggerMode == 1 ? "RISING" : "FALLING";
        return mode + " CH" + (triggerChannel == 0 ? "A" : "B") + " @" + triggerLevel + " "
                + (armed ? "ARMED" : triggered ? "TRIGGERED" : "HOLD");
    }

    public int cursorDeltaSamples() {
        return Math.abs(cursorB - cursorA);
    }

    /** Uses captured gameTime when both cursors point at retained samples. */
    public int cursorDeltaTicks() {
        long a = displaySampleGameTime(cursorA);
        long b = displaySampleGameTime(cursorB);
        if (a < 0 || b < 0) return cursorDeltaSamples() * SAMPLE_PERIOD_TICKS;
        long delta = Math.abs(b - a);
        return delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
    }

    public int cursorValue(int channel, boolean second) {
        if (!validChannel(channel)) return -1;
        int[] values = recent(channel);
        if (values.length == 0) return -1;
        int base = Math.max(0, values.length - DISPLAY_SAMPLES);
        int sampleIndex = base + (second ? cursorB : cursorA);
        sampleIndex = Math.min(values.length - 1, sampleIndex);
        return values[sampleIndex];
    }

    public int sampleCount() {
        return count;
    }

    public int current(int channel) {
        if (!validChannel(channel) || count == 0) return -1;
        return history[channel][(index - 1 + CAPACITY) % CAPACITY];
    }

    public int validSamples(int channel) {
        if (!validChannel(channel)) return 0;
        int valid = 0;
        for (int value : recent(channel)) if (value >= 0) valid++;
        return valid;
    }

    public int coveragePercent(int channel) {
        if (!validChannel(channel) || count == 0) return 0;
        return (validSamples(channel) * 100) / count;
    }

    public int minimum(int channel) {
        if (!validChannel(channel)) return -1;
        int min = 16;
        for (int value : recent(channel)) if (value >= 0) min = Math.min(min, value);
        return min == 16 ? -1 : min;
    }

    public int maximum(int channel) {
        if (!validChannel(channel)) return -1;
        int max = -1;
        for (int value : recent(channel)) if (value >= 0) max = Math.max(max, value);
        return max;
    }

    public int peakToPeak(int channel) {
        int lo = minimum(channel);
        int hi = maximum(channel);
        return lo < 0 || hi < 0 ? -1 : hi - lo;
    }

    public int average100(int channel) {
        if (!validChannel(channel)) return -1;
        int total = 0;
        int valid = 0;
        for (int value : recent(channel)) {
            if (value < 0) continue;
            total += value;
            valid++;
        }
        return valid == 0 ? -1 : (total * 100 + valid / 2) / valid;
    }

    /** Mean absolute step between adjacent valid samples, scaled by 100. */
    public int meanStep100(int channel) {
        if (!validChannel(channel)) return -1;
        int total = 0;
        int pairs = 0;
        int previous = -1;
        for (int value : recent(channel)) {
            if (value < 0) {
                previous = -1;
                continue;
            }
            if (previous >= 0) {
                total += Math.abs(value - previous);
                pairs++;
            }
            previous = value;
        }
        return pairs == 0 ? 0 : (total * 100 + pairs / 2) / pairs;
    }

    public int estimatedPeriodSamples(int channel) {
        if (!validChannel(channel)) return -1;
        int[] values = recent(channel);
        if (values.length < 4) return -1;
        int threshold = 8;
        int last = -1;
        int total = 0;
        int intervals = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] >= 0 && values[i] >= 0
                    && values[i - 1] < threshold && values[i] >= threshold) {
                if (last >= 0) {
                    total += i - last;
                    intervals++;
                }
                last = i;
            }
        }
        return intervals == 0 ? -1 : Math.max(1, total / intervals);
    }

    /** Estimates a complete rising-edge period from real captured server gameTime intervals. */
    public int estimatedPeriodTicks(int channel) {
        if (!validChannel(channel)) return -1;
        int[] values = recent(channel);
        long[] times = recentTimes();
        if (values.length < 4 || times.length != values.length) return -1;
        int threshold = 8;
        long last = -1L;
        long total = 0L;
        int intervals = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] >= 0 && values[i] >= 0 && times[i] >= 0
                    && values[i - 1] < threshold && values[i] >= threshold) {
                if (last >= 0 && times[i] >= last) {
                    total += times[i] - last;
                    intervals++;
                }
                last = times[i];
            }
        }
        if (intervals == 0) return -1;
        long average = Math.max(1L, total / intervals);
        return average > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) average;
    }

    public String captureQuality(int channel) {
        if (!validChannel(channel) || count == 0) return "NO_DATA";
        int coverage = coveragePercent(channel);
        if (coverage == 100) return count < 8 ? "WARMUP" : "COMPLETE";
        if (coverage >= 75) return "PARTIAL";
        return "POOR_COVERAGE";
    }

    public String waveform(int channel) {
        if (!validChannel(channel)) return "?";
        String[] bars = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        int[] values = recent(channel);
        if (values.length == 0) return "∅";
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, values.length - DISPLAY_SAMPLES);
        for (int i = start; i < values.length; i++) {
            if (values[i] < 0) builder.append("·");
            else builder.append(bars[Math.max(0, Math.min(7, (int) Math.round(values[i] / 15.0 * 7.0)))]);
        }
        return builder.toString();
    }

    private int[] recent(int channel) {
        if (!validChannel(channel)) return new int[0];
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = history[channel][(index - count + i + CAPACITY) % CAPACITY];
        }
        return values;
    }

    private long[] recentTimes() {
        long[] times = new long[count];
        for (int i = 0; i < count; i++) {
            times[i] = sampleTimes[(index - count + i + CAPACITY) % CAPACITY];
        }
        return times;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int c = 0; c < CHANNELS; c++) {
            int[] saved = tag.getIntArray("history" + c);
            for (int i = 0; i < CAPACITY; i++) history[c][i] = i < saved.length ? saved[i] : -1;
        }
        long[] savedTimes = tag.getLongArray("sampleTimes");
        for (int i = 0; i < CAPACITY; i++) sampleTimes[i] = i < savedTimes.length ? savedTimes[i] : -1L;
        index = Math.max(0, Math.min(CAPACITY - 1, tag.getInt("index")));
        count = Math.max(0, Math.min(CAPACITY, tag.getInt("count")));
        triggerLevel = Math.max(1, Math.min(15, tag.getInt("triggerLevel")));
        triggerChannel = Math.max(0, Math.min(1, tag.getInt("triggerChannel")));
        triggerMode = Math.max(0, Math.min(2, tag.getInt("triggerMode")));
        armed = tag.getBoolean("armed");
        triggered = tag.getBoolean("triggered");
        cursorA = Math.max(0, Math.min(DISPLAY_SAMPLES - 1, tag.getInt("cursorA")));
        cursorB = Math.max(0, Math.min(DISPLAY_SAMPLES - 1, tag.getInt("cursorB")));
        lastA = tag.contains("lastA") ? tag.getInt("lastA") : -1;
        lastB = tag.contains("lastB") ? tag.getInt("lastB") : -1;
        samplesSinceTrigger = Math.max(0, Math.min(CAPACITY, tag.getInt("samplesSinceTrigger")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int c = 0; c < CHANNELS; c++) tag.putIntArray("history" + c, history[c]);
        tag.putLongArray("sampleTimes", sampleTimes);
        tag.putInt("index", index);
        tag.putInt("count", count);
        tag.putInt("triggerLevel", triggerLevel);
        tag.putInt("triggerChannel", triggerChannel);
        tag.putInt("triggerMode", triggerMode);
        tag.putBoolean("armed", armed);
        tag.putBoolean("triggered", triggered);
        tag.putInt("cursorA", cursorA);
        tag.putInt("cursorB", cursorB);
        tag.putInt("lastA", lastA);
        tag.putInt("lastB", lastB);
        tag.putInt("samplesSinceTrigger", samplesSinceTrigger);
    }
}
