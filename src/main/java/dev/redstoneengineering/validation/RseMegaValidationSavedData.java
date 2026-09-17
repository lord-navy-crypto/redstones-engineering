package dev.redstoneengineering.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;

/** Persistent run/evidence state for the 40-DUT Mega Validation Factory v2.1. */
public final class RseMegaValidationSavedData extends SavedData {
    private static final String DATA_NAME = "rse_mega_validation_factory_v2";
    public static final int STATION_COUNT = 40;
    public static final int CELL_COUNT = 8;

    public record Placement(
            BlockPos origin,
            long placedTick,
            int phase,
            long phaseStartedTick,
            boolean retestPressed
    ) {
        public Placement {
            if (origin == null) throw new IllegalArgumentException("origin required");
            if (placedTick < 0 || phaseStartedTick < 0) throw new IllegalArgumentException("ticks must be nonnegative");
            if (phase < 0) throw new IllegalArgumentException("phase must be nonnegative");
        }

        static Placement fresh(BlockPos origin, long tick) {
            return new Placement(origin, tick, 0, tick, false);
        }

        Placement advance(long tick) {
            return new Placement(origin, placedTick, phase + 1, tick, retestPressed);
        }

        Placement reset(long tick, boolean pressed) {
            return new Placement(origin, tick, 0, tick, pressed);
        }

        Placement withRetestPressed(boolean pressed) {
            return new Placement(origin, placedTick, phase, phaseStartedTick, pressed);
        }
    }

    private Placement placement;
    private int runNumber;

    // Station arrays are 1-based; index zero is intentionally unused.
    private final String[] stationVerdict = new String[STATION_COUNT + 1];
    private final String[] stationDetail = new String[STATION_COUNT + 1];
    private final boolean[] stationEverPassed = new boolean[STATION_COUNT + 1];
    private final boolean[] stationEverFailed = new boolean[STATION_COUNT + 1];
    private final String[] firstFailurePhase = new String[STATION_COUNT + 1];
    private final String[] firstFailureDetail = new String[STATION_COUNT + 1];

    private final String[] cellVerdict = new String[CELL_COUNT];
    private final String[] cellDetail = new String[CELL_COUNT];

    private long completedPhases;
    private long enduranceStartTick = -1L;
    private long enduranceHealthyTicks;

    public RseMegaValidationSavedData() {
        clearAllEvidence();
    }

    public static RseMegaValidationSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) throw new IllegalArgumentException("server level required");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RseMegaValidationSavedData::new, RseMegaValidationSavedData::load),
                DATA_NAME
        );
    }

    public static RseMegaValidationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RseMegaValidationSavedData data = new RseMegaValidationSavedData();
        data.runNumber = Math.max(0, tag.getInt("RunNumber"));
        if (tag.getBoolean("Present")) {
            try {
                BlockPos origin = BlockPos.of(tag.getLong("Origin"));
                long placedTick = Math.max(0L, tag.getLong("PlacedTick"));
                int phase = Math.max(0, tag.getInt("Phase"));
                long phaseStartedTick = tag.contains("PhaseStartedTick")
                        ? Math.max(0L, tag.getLong("PhaseStartedTick"))
                        : placedTick;
                data.placement = new Placement(
                        origin, placedTick, phase, phaseStartedTick, tag.getBoolean("RetestPressed"));
                if (data.runNumber <= 0) data.runNumber = 1;
            } catch (IllegalArgumentException ignored) {
                data.placement = null;
            }
        }
        data.completedPhases = tag.getLong("CompletedPhases");
        data.enduranceStartTick = tag.contains("EnduranceStartTick") ? tag.getLong("EnduranceStartTick") : -1L;
        data.enduranceHealthyTicks = Math.max(0L, tag.getLong("EnduranceHealthyTicks"));

        for (int station = 1; station <= STATION_COUNT; station++) {
            String prefix = "Station" + station + ".";
            data.stationVerdict[station] = nonBlank(tag.getString(prefix + "Verdict"), "WAIT");
            data.stationDetail[station] = nonBlank(tag.getString(prefix + "Detail"), "not evaluated");
            data.stationEverPassed[station] = tag.getBoolean(prefix + "EverPassed");
            data.stationEverFailed[station] = tag.getBoolean(prefix + "EverFailed");
            data.firstFailurePhase[station] = tag.getString(prefix + "FirstFailurePhase");
            data.firstFailureDetail[station] = tag.getString(prefix + "FirstFailureDetail");
        }
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            String prefix = "Cell" + cell + ".";
            data.cellVerdict[cell] = nonBlank(tag.getString(prefix + "Verdict"), "WAIT");
            data.cellDetail[cell] = nonBlank(tag.getString(prefix + "Detail"), "not evaluated");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Placement current = placement;
        tag.putBoolean("Present", current != null);
        tag.putInt("RunNumber", runNumber);
        if (current != null) {
            tag.putLong("Origin", current.origin().asLong());
            tag.putLong("PlacedTick", current.placedTick());
            tag.putInt("Phase", current.phase());
            tag.putLong("PhaseStartedTick", current.phaseStartedTick());
            tag.putBoolean("RetestPressed", current.retestPressed());
        }
        tag.putLong("CompletedPhases", completedPhases);
        tag.putLong("EnduranceStartTick", enduranceStartTick);
        tag.putLong("EnduranceHealthyTicks", enduranceHealthyTicks);

        for (int station = 1; station <= STATION_COUNT; station++) {
            String prefix = "Station" + station + ".";
            tag.putString(prefix + "Verdict", stationVerdict[station]);
            tag.putString(prefix + "Detail", stationDetail[station]);
            tag.putBoolean(prefix + "EverPassed", stationEverPassed[station]);
            tag.putBoolean(prefix + "EverFailed", stationEverFailed[station]);
            tag.putString(prefix + "FirstFailurePhase", firstFailurePhase[station]);
            tag.putString(prefix + "FirstFailureDetail", firstFailureDetail[station]);
        }
        for (int cell = 0; cell < CELL_COUNT; cell++) {
            String prefix = "Cell" + cell + ".";
            tag.putString(prefix + "Verdict", cellVerdict[cell]);
            tag.putString(prefix + "Detail", cellDetail[cell]);
        }
        return tag;
    }

    public Placement placement() {
        return placement;
    }

    public int runNumber() {
        return runNumber;
    }

    public Placement place(BlockPos origin, long tick) {
        placement = Placement.fresh(origin, tick);
        runNumber = 1;
        clearAllEvidence();
        setDirty();
        return placement;
    }

    public Placement advancePhase(long tick) {
        if (placement == null) return null;
        placement = placement.advance(tick);
        setDirty();
        return placement;
    }

    public Placement resetForRetest(long tick, boolean pressed) {
        if (placement == null) return null;
        placement = placement.reset(tick, pressed);
        runNumber = Math.max(1, runNumber + 1);
        clearCurrentRunEvidence();
        setDirty();
        return placement;
    }

    public Placement setRetestPressed(boolean pressed) {
        if (placement == null || placement.retestPressed() == pressed) return placement;
        placement = placement.withRetestPressed(pressed);
        setDirty();
        return placement;
    }

    public void recordStation(
            int station,
            RseValidationSelfTestService.Verdict verdict,
            String detail,
            String phaseName
    ) {
        requireStation(station);
        String value = verdict == null ? "WAIT" : verdict.name();
        stationVerdict[station] = value;
        stationDetail[station] = detail == null || detail.isBlank() ? "no detail" : detail;
        if (verdict == RseValidationSelfTestService.Verdict.PASS) stationEverPassed[station] = true;
        if (verdict == RseValidationSelfTestService.Verdict.FAIL) {
            stationEverFailed[station] = true;
            if (firstFailurePhase[station].isBlank()) {
                firstFailurePhase[station] = phaseName == null ? "UNKNOWN" : phaseName;
                firstFailureDetail[station] = stationDetail[station];
            }
        }
        setDirty();
    }

    public void recordCell(int cellIndex, RseValidationSelfTestService.Verdict verdict, String detail) {
        requireCell(cellIndex);
        cellVerdict[cellIndex] = verdict == null ? "WAIT" : verdict.name();
        cellDetail[cellIndex] = detail == null || detail.isBlank() ? "no detail" : detail;
        setDirty();
    }

    public void markPhaseComplete(int phaseIndex) {
        if (phaseIndex < 0 || phaseIndex >= Long.SIZE) return;
        completedPhases |= 1L << phaseIndex;
        setDirty();
    }

    public boolean phaseCompleted(int phaseIndex) {
        return phaseIndex >= 0 && phaseIndex < Long.SIZE && (completedPhases & (1L << phaseIndex)) != 0L;
    }

    public long completedPhases() {
        return completedPhases;
    }

    public void beginEndurance(long tick) {
        if (enduranceStartTick < 0L) {
            enduranceStartTick = Math.max(0L, tick);
            setDirty();
        }
    }

    public void addHealthyEndurance(long ticks) {
        if (ticks <= 0L) return;
        enduranceHealthyTicks += ticks;
        setDirty();
    }

    public long enduranceStartTick() {
        return enduranceStartTick;
    }

    public long enduranceHealthyTicks() {
        return enduranceHealthyTicks;
    }

    public String stationVerdict(int station) {
        requireStation(station);
        return stationVerdict[station];
    }

    public String stationDetail(int station) {
        requireStation(station);
        return stationDetail[station];
    }

    public boolean stationEverPassed(int station) {
        requireStation(station);
        return stationEverPassed[station];
    }

    public boolean stationEverFailed(int station) {
        requireStation(station);
        return stationEverFailed[station];
    }

    public String firstFailurePhase(int station) {
        requireStation(station);
        return firstFailurePhase[station];
    }

    public String firstFailureDetail(int station) {
        requireStation(station);
        return firstFailureDetail[station];
    }

    public String cellVerdict(int cellIndex) {
        requireCell(cellIndex);
        return cellVerdict[cellIndex];
    }

    public String cellDetail(int cellIndex) {
        requireCell(cellIndex);
        return cellDetail[cellIndex];
    }

    public int stationsEverPassedCount() {
        int count = 0;
        for (int station = 1; station <= STATION_COUNT; station++) if (stationEverPassed[station]) count++;
        return count;
    }

    public int stationsCurrentlyFailedCount() {
        int count = 0;
        for (int station = 1; station <= STATION_COUNT; station++) if ("FAIL".equals(stationVerdict[station])) count++;
        return count;
    }

    private void clearAllEvidence() {
        Arrays.fill(stationEverPassed, false);
        Arrays.fill(stationEverFailed, false);
        Arrays.fill(firstFailurePhase, "");
        Arrays.fill(firstFailureDetail, "");
        clearCurrentRunEvidence();
    }

    private void clearCurrentRunEvidence() {
        Arrays.fill(stationVerdict, "WAIT");
        Arrays.fill(stationDetail, "not evaluated");
        Arrays.fill(cellVerdict, "WAIT");
        Arrays.fill(cellDetail, "not evaluated");
        completedPhases = 0L;
        enduranceStartTick = -1L;
        enduranceHealthyTicks = 0L;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void requireStation(int station) {
        if (station < 1 || station > STATION_COUNT) throw new IllegalArgumentException("station must be 1..40");
    }

    private static void requireCell(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= CELL_COUNT) throw new IllegalArgumentException("cell must be 0..7");
    }
}
