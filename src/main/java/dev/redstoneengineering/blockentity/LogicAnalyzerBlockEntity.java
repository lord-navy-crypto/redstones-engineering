package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Four-channel logic analyzer with edge trigger, authoritative timing and bounded capture diagnostics. */
public class LogicAnalyzerBlockEntity extends BlockEntity {
    private static final int CHANNELS = 4;
    private static final int CAPACITY = 32;
    public static final int DISPLAY_SAMPLES = 16;
    public static final int SAMPLE_PERIOD_TICKS = 1;

    private final int[] masks = new int[CAPACITY];
    private final int[] validMasks = new int[CAPACITY];
    private final long[] sampleTimes = new long[CAPACITY];
    private final int[] rising = new int[CHANNELS];
    private final int[] falling = new int[CHANNELS];

    private int index = 0;
    private int count = 0;
    private int lastMask = 0;
    private int lastValidMask = 0;
    private int triggerChannel = 0;
    private int triggerEdge = 1; // 1 rising, 2 falling
    private int cursorA = 0;
    private int cursorB = 8;
    private boolean armed = true;
    private boolean triggered = false;
    private int postTriggerSamples = 0;

    public LogicAnalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(RedstoneEngineering.LOGIC_ANALYZER_BLOCK_ENTITY.get(), pos, state);
        for (int i = 0; i < CAPACITY; i++) sampleTimes[i] = -1L;
    }

    /** Records a thresholded four-channel sample at the real logical-server gameTime. */
    public void addSample(long gameTime, int mask, int validMask) {
        int common = validMask & lastValidMask;
        boolean fire = false;

        for (int channel = 0; channel < CHANNELS; channel++) {
            int bit = 1 << channel;
            if ((common & bit) == 0) continue;
            boolean before = (lastMask & bit) != 0;
            boolean now = (mask & bit) != 0;
            if (!before && now) rising[channel]++;
            if (before && !now) falling[channel]++;
            if (armed && channel == triggerChannel
                    && ((triggerEdge == 1 && !before && now)
                    || (triggerEdge == 2 && before && !now))) {
                fire = true;
            }
        }

        if (armed || triggered) {
            masks[index] = mask;
            validMasks[index] = validMask;
            sampleTimes[index] = Math.max(0L, gameTime);
            if (armed && fire) {
                armed = false;
                triggered = true;
                postTriggerSamples = 0;
            }
            if (triggered) postTriggerSamples++;
            index = (index + 1) % CAPACITY;
            count = Math.min(CAPACITY, count + 1);
            if (triggered && postTriggerSamples >= CAPACITY / 2) triggered = false;
        }

        lastMask = mask;
        lastValidMask = validMask;
        setChanged();
    }

    /** Compatibility helper for tests/tools; production sampling passes gameTime explicitly. */
    public void addSample(int mask, int validMask) {
        long gameTime;
        if (getLevel() != null) {
            gameTime = getLevel().getGameTime();
        } else {
            long latest = latestSampleGameTime();
            gameTime = latest < 0 ? 0L : latest + SAMPLE_PERIOD_TICKS;
        }
        addSample(gameTime, mask, validMask);
    }

    private static boolean validChannel(int channel) {
        return channel >= 0 && channel < CHANNELS;
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            masks[i] = 0;
            validMasks[i] = 0;
            sampleTimes[i] = -1L;
        }
        for (int i = 0; i < CHANNELS; i++) {
            rising[i] = 0;
            falling[i] = 0;
        }
        index = 0;
        count = 0;
        lastMask = 0;
        lastValidMask = 0;
        armed = true;
        triggered = false;
        postTriggerSamples = 0;
        setChanged();
    }

    public void arm() {
        armed = true;
        triggered = false;
        postTriggerSamples = 0;
        setChanged();
    }

    public void cycleTriggerChannel() {
        triggerChannel = (triggerChannel + 1) % CHANNELS;
        arm();
    }

    public void cycleTriggerEdge() {
        triggerEdge = triggerEdge == 1 ? 2 : 1;
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

    public int triggerChannel() {
        return triggerChannel;
    }

    public int triggerEdge() {
        return triggerEdge;
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

    /** Returns -1 for invalid/missing, 0 for LOW and 1 for HIGH in a 16-sample display window. */
    public int displayState(int channel, int slot) {
        if (!validChannel(channel) || slot < 0 || slot >= DISPLAY_SAMPLES) return -1;
        int available = Math.min(DISPLAY_SAMPLES, count);
        int padding = DISPLAY_SAMPLES - available;
        if (slot < padding) return -1;
        int chronological = count - available + (slot - padding);
        int source = sourceIndex(chronological);
        int bit = 1 << channel;
        if ((validMasks[source] & bit) == 0) return -1;
        return (masks[source] & bit) != 0 ? 1 : 0;
    }

    /** Returns the exact server gameTime paired with a displayed sample, or -1 during warm-up. */
    public long displaySampleGameTime(int slot) {
        if (slot < 0 || slot >= DISPLAY_SAMPLES) return -1L;
        int available = Math.min(DISPLAY_SAMPLES, count);
        int padding = DISPLAY_SAMPLES - available;
        if (slot < padding) return -1L;
        int chronological = count - available + (slot - padding);
        return sampleTimes[sourceIndex(chronological)];
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
        return clampTicks(latest - sample);
    }

    public int cursorDeltaSamples() {
        return Math.abs(cursorB - cursorA);
    }

    /** Uses retained server gameTime when both cursors point at valid capture slots. */
    public int cursorDeltaTicks() {
        long a = displaySampleGameTime(cursorA);
        long b = displaySampleGameTime(cursorB);
        if (a < 0 || b < 0) return cursorDeltaSamples() * SAMPLE_PERIOD_TICKS;
        return clampTicks(Math.abs(b - a));
    }

    public String triggerStatus() {
        return "CH" + (triggerChannel + 1) + " "
                + (triggerEdge == 1 ? "RISING" : "FALLING") + " "
                + (armed ? "ARMED" : triggered ? "TRIGGERED" : "HOLD");
    }

    public int sampleCount() {
        return count;
    }

    public int rising(int channel) {
        return validChannel(channel) ? rising[channel] : 0;
    }

    public int falling(int channel) {
        return validChannel(channel) ? falling[channel] : 0;
    }

    public int edgeCount(int channel) {
        return rising(channel) + falling(channel);
    }

    public int highSamples(int channel) {
        if (!validChannel(channel)) return 0;
        int bit = 1 << channel;
        int high = 0;
        for (int i = 0; i < count; i++) {
            int source = sourceIndex(i);
            if ((validMasks[source] & bit) != 0 && (masks[source] & bit) != 0) high++;
        }
        return high;
    }

    public int validSamples(int channel) {
        if (!validChannel(channel)) return 0;
        int bit = 1 << channel;
        int valid = 0;
        for (int i = 0; i < count; i++) {
            if ((validMasks[sourceIndex(i)] & bit) != 0) valid++;
        }
        return valid;
    }

    public int coveragePercent(int channel) {
        return count == 0 ? 0 : (validSamples(channel) * 100) / count;
    }

    public int dutyPercent(int channel) {
        int valid = validSamples(channel);
        return valid == 0 ? 0 : (highSamples(channel) * 100) / valid;
    }

    public int transitionRatePercent(int channel) {
        int valid = validSamples(channel);
        if (valid < 2) return 0;
        return Math.min(100, (edgeCount(channel) * 100) / (valid - 1));
    }

    /** Average rising-edge period from complete, contiguous, timestamped evidence only. */
    public int estimatedPeriodTicks(int channel) {
        if (!validChannel(channel) || count < 3) return -1;
        long lastRise = -1L;
        long total = 0L;
        int intervals = 0;
        int previousState = -1;
        long previousTime = -1L;
        for (int i = 0; i < count; i++) {
            int state = chronologicalState(channel, i);
            long time = chronologicalTime(i);
            if (state < 0 || time < 0 || (previousTime >= 0 && time < previousTime)) {
                previousState = -1;
                previousTime = time;
                lastRise = -1L;
                continue;
            }
            if (previousState == 0 && state == 1) {
                if (lastRise >= 0L && time >= lastRise) {
                    total += time - lastRise;
                    intervals++;
                }
                lastRise = time;
            }
            previousState = state;
            previousTime = time;
        }
        return intervals == 0 ? -1 : clampTicks(Math.max(1L, total / intervals));
    }

    /** Most recent complete HIGH pulse width; missing samples invalidate an unfinished pulse. */
    public int lastCompleteHighPulseTicks(int channel) {
        if (!validChannel(channel) || count < 2) return -1;
        long highStart = -1L;
        long lastWidth = -1L;
        int previousState = -1;
        long previousTime = -1L;
        for (int i = 0; i < count; i++) {
            int state = chronologicalState(channel, i);
            long time = chronologicalTime(i);
            if (state < 0 || time < 0 || (previousTime >= 0 && time < previousTime)) {
                previousState = -1;
                previousTime = time;
                highStart = -1L;
                continue;
            }
            if (previousState == 0 && state == 1) highStart = time;
            if (previousState == 1 && state == 0 && highStart >= 0L && time >= highStart) {
                lastWidth = time - highStart;
                highStart = -1L;
            }
            previousState = state;
            previousTime = time;
        }
        return lastWidth < 0L ? -1 : clampTicks(lastWidth);
    }

    /** Age of the most recent observed rising edge relative to the newest retained sample. */
    public int lastRisingAgeTicks(int channel) {
        long latest = latestSampleGameTime();
        long rise = lastRisingGameTime(channel);
        if (latest < 0 || rise < 0 || rise > latest) return -1;
        return clampTicks(latest - rise);
    }

    private long lastRisingGameTime(int channel) {
        if (!validChannel(channel)) return -1L;
        int previousState = -1;
        long previousTime = -1L;
        long lastRise = -1L;
        for (int i = 0; i < count; i++) {
            int state = chronologicalState(channel, i);
            long time = chronologicalTime(i);
            if (state < 0 || time < 0 || (previousTime >= 0 && time < previousTime)) {
                previousState = -1;
                previousTime = time;
                continue;
            }
            if (previousState == 0 && state == 1) lastRise = time;
            previousState = state;
            previousTime = time;
        }
        return lastRise;
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
        if (count == 0) return "∅";
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, count - DISPLAY_SAMPLES);
        int bit = 1 << channel;
        for (int i = start; i < count; i++) {
            int source = sourceIndex(i);
            builder.append((validMasks[source] & bit) == 0 ? '·' : (masks[source] & bit) != 0 ? '█' : '_');
        }
        return builder.toString();
    }

    private int sourceIndex(int chronologicalIndex) {
        return (index - count + chronologicalIndex + CAPACITY) % CAPACITY;
    }

    private int chronologicalState(int channel, int chronologicalIndex) {
        if (!validChannel(channel) || chronologicalIndex < 0 || chronologicalIndex >= count) return -1;
        int source = sourceIndex(chronologicalIndex);
        int bit = 1 << channel;
        if ((validMasks[source] & bit) == 0) return -1;
        return (masks[source] & bit) != 0 ? 1 : 0;
    }

    private long chronologicalTime(int chronologicalIndex) {
        if (chronologicalIndex < 0 || chronologicalIndex >= count) return -1L;
        return sampleTimes[sourceIndex(chronologicalIndex)];
    }

    private static int clampTicks(long ticks) {
        return ticks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, ticks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copy(tag.getIntArray("masks"), masks);
        copy(tag.getIntArray("validMasks"), validMasks);
        long[] savedTimes = tag.getLongArray("sampleTimes");
        for (int i = 0; i < CAPACITY; i++) sampleTimes[i] = i < savedTimes.length ? savedTimes[i] : -1L;
        copy(tag.getIntArray("rising"), rising);
        copy(tag.getIntArray("falling"), falling);
        index = Math.max(0, Math.min(CAPACITY - 1, tag.getInt("index")));
        count = Math.max(0, Math.min(CAPACITY, tag.getInt("count")));
        lastMask = tag.getInt("lastMask");
        lastValidMask = tag.getInt("lastValidMask");
        triggerChannel = Math.max(0, Math.min(CHANNELS - 1, tag.getInt("triggerChannel")));
        triggerEdge = Math.max(1, Math.min(2, tag.getInt("triggerEdge")));
        cursorA = Math.max(0, Math.min(DISPLAY_SAMPLES - 1, tag.getInt("cursorA")));
        cursorB = Math.max(0, Math.min(DISPLAY_SAMPLES - 1, tag.getInt("cursorB")));
        armed = tag.getBoolean("armed");
        triggered = tag.getBoolean("triggered");
        postTriggerSamples = Math.max(0, Math.min(CAPACITY, tag.getInt("postTriggerSamples")));
    }

    private static void copy(int[] source, int[] destination) {
        for (int i = 0; i < Math.min(source.length, destination.length); i++) destination[i] = source[i];
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("masks", masks);
        tag.putIntArray("validMasks", validMasks);
        tag.putLongArray("sampleTimes", sampleTimes);
        tag.putIntArray("rising", rising);
        tag.putIntArray("falling", falling);
        tag.putInt("index", index);
        tag.putInt("count", count);
        tag.putInt("lastMask", lastMask);
        tag.putInt("lastValidMask", lastValidMask);
        tag.putInt("triggerChannel", triggerChannel);
        tag.putInt("triggerEdge", triggerEdge);
        tag.putInt("cursorA", cursorA);
        tag.putInt("cursorB", cursorB);
        tag.putBoolean("armed", armed);
        tag.putBoolean("triggered", triggered);
        tag.putInt("postTriggerSamples", postTriggerSamples);
    }
}
