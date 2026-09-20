package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent, server-owned high-cardinality engineering parameters.
 *
 * <p>Values that belong to an instrument model but would create excessive BlockState
 * cardinality live here. BlockState remains responsible for physical topology and a
 * small compatibility/default preset; this store owns precise engineering settings.</p>
 */
public final class EngineeringDeviceParameters extends SavedData {
    private static final String DATA_NAME = "rse_engineering_device_parameters_v1";
    private static final int SCHEMA_VERSION = 1;
    private final Map<String, Integer> lapisLowPassAlphaPercent = new LinkedHashMap<>();
    private final Map<String, PidParameters> pidParameters = new LinkedHashMap<>();

    public record PidParameters(int kp, int kiDivisor, int kd, int derivativeSmoothing, int riseLimit, int fallLimit) {
        public PidParameters {
            kp = clamp(kp, 0, 12);
            kiDivisor = clamp(kiDivisor, 0, 64);
            kd = clamp(kd, 0, 12);
            derivativeSmoothing = clamp(derivativeSmoothing, 1, 16);
            riseLimit = clamp(riseLimit, 1, 15);
            fallLimit = clamp(fallLimit, 1, 15);
        }
    }

    public static EngineeringDeviceParameters get(ServerLevel level) {
        if (level == null || level.getServer() == null) throw new IllegalArgumentException("server level required");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EngineeringDeviceParameters::new, EngineeringDeviceParameters::load),
                DATA_NAME
        );
    }

    public static EngineeringDeviceParameters load(CompoundTag tag, HolderLookup.Provider registries) {
        EngineeringDeviceParameters data = new EngineeringDeviceParameters();
        ListTag rows = tag.getList("LapisLowPass", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            String key = row.getString("Key");
            int alpha = row.getInt("AlphaPercent");
            if (!key.isBlank() && alpha >= 1 && alpha <= 99) data.lapisLowPassAlphaPercent.put(key, alpha);
        }
        ListTag pidRows = tag.getList("PidControllers", Tag.TAG_COMPOUND);
        for (int i = 0; i < pidRows.size(); i++) {
            CompoundTag row = pidRows.getCompound(i);
            String key = row.getString("Key");
            if (key.isBlank()) continue;
            data.pidParameters.put(key, new PidParameters(
                    row.getInt("Kp"),
                    row.getInt("KiDivisor"),
                    row.getInt("Kd"),
                    row.getInt("DerivativeSmoothing"),
                    row.getInt("RiseLimit"),
                    row.getInt("FallLimit")
            ));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag rows = new ListTag();
        for (Map.Entry<String, Integer> entry : lapisLowPassAlphaPercent.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Key", entry.getKey());
            row.putInt("AlphaPercent", entry.getValue());
            rows.add(row);
        }
        tag.put("LapisLowPass", rows);

        ListTag pidRows = new ListTag();
        for (Map.Entry<String, PidParameters> entry : pidParameters.entrySet()) {
            PidParameters value = entry.getValue();
            CompoundTag row = new CompoundTag();
            row.putString("Key", entry.getKey());
            row.putInt("Kp", value.kp());
            row.putInt("KiDivisor", value.kiDivisor());
            row.putInt("Kd", value.kd());
            row.putInt("DerivativeSmoothing", value.derivativeSmoothing());
            row.putInt("RiseLimit", value.riseLimit());
            row.putInt("FallLimit", value.fallLimit());
            pidRows.add(row);
        }
        tag.put("PidControllers", pidRows);
        return tag;
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    public int lapisLowPassAlphaPercent(ServerLevel level, BlockPos pos, int fallbackPercent) {
        return lapisLowPassAlphaPercent.getOrDefault(key(level, pos), clamp(fallbackPercent, 1, 99));
    }

    public boolean setLapisLowPassAlphaPercent(ServerLevel level, BlockPos pos, int alphaPercent) {
        int bounded = clamp(alphaPercent, 1, 99);
        String key = key(level, pos);
        Integer previous = lapisLowPassAlphaPercent.put(key, bounded);
        if (previous != null && previous == bounded) return false;
        setDirty();
        return true;
    }

    public boolean removeLapisLowPass(ServerLevel level, BlockPos pos) {
        if (lapisLowPassAlphaPercent.remove(key(level, pos)) == null) return false;
        setDirty();
        return true;
    }

    public PidParameters pidParameters(ServerLevel level, BlockPos pos, PidParameters fallback) {
        return pidParameters.getOrDefault(key(level, pos), fallback);
    }

    public boolean setPidParameters(ServerLevel level, BlockPos pos, PidParameters parameters) {
        String key = key(level, pos);
        PidParameters previous = pidParameters.put(key, parameters);
        if (parameters.equals(previous)) return false;
        setDirty();
        return true;
    }

    public boolean removePidParameters(ServerLevel level, BlockPos pos) {
        if (pidParameters.remove(key(level, pos)) == null) return false;
        setDirty();
        return true;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
