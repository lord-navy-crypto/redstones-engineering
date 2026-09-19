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
 * Persistent server-owned PID parameter overrides.
 *
 * The selected BlockState preset remains the durable baseline. Once a player edits an individual
 * coefficient, this store retains the bounded custom values without exploding BlockState
 * cardinality. Controller runtime (integrator, derivative history, slew state) remains transient.
 */
public final class PidTuningSavedData extends SavedData {
    private static final String DATA_NAME = "rse_pid_tuning";

    public static final int FIELD_KP = 0;
    public static final int FIELD_KI_DIVISOR = 1;
    public static final int FIELD_KD = 2;
    public static final int FIELD_D_SMOOTHING = 3;
    public static final int FIELD_RISE_LIMIT = 4;
    public static final int FIELD_FALL_LIMIT = 5;

    public record Config(int kp, int kiDivisor, int kd, int derivativeSmoothing, int riseLimit, int fallLimit) {
        public Config {
            kp = clamp(kp, 0, 8);
            kiDivisor = clamp(kiDivisor, 0, 64);
            kd = clamp(kd, 0, 8);
            derivativeSmoothing = clamp(derivativeSmoothing, 1, 8);
            riseLimit = clamp(riseLimit, 1, 15);
            fallLimit = clamp(fallLimit, 1, 15);
        }

        public int value(int field) {
            return switch (field) {
                case FIELD_KP -> kp;
                case FIELD_KI_DIVISOR -> kiDivisor;
                case FIELD_KD -> kd;
                case FIELD_D_SMOOTHING -> derivativeSmoothing;
                case FIELD_RISE_LIMIT -> riseLimit;
                case FIELD_FALL_LIMIT -> fallLimit;
                default -> throw new IllegalArgumentException("unknown PID parameter field " + field);
            };
        }

        public Config withValue(int field, int value) {
            return switch (field) {
                case FIELD_KP -> new Config(value, kiDivisor, kd, derivativeSmoothing, riseLimit, fallLimit);
                case FIELD_KI_DIVISOR -> new Config(kp, value, kd, derivativeSmoothing, riseLimit, fallLimit);
                case FIELD_KD -> new Config(kp, kiDivisor, value, derivativeSmoothing, riseLimit, fallLimit);
                case FIELD_D_SMOOTHING -> new Config(kp, kiDivisor, kd, value, riseLimit, fallLimit);
                case FIELD_RISE_LIMIT -> new Config(kp, kiDivisor, kd, derivativeSmoothing, value, fallLimit);
                case FIELD_FALL_LIMIT -> new Config(kp, kiDivisor, kd, derivativeSmoothing, riseLimit, value);
                default -> throw new IllegalArgumentException("unknown PID parameter field " + field);
            };
        }

        public int[] asArray() {
            return new int[]{kp, kiDivisor, kd, derivativeSmoothing, riseLimit, fallLimit};
        }

        public static Config fromPreset(int[] preset) {
            if (preset == null || preset.length < 6) throw new IllegalArgumentException("PID preset requires six values");
            return new Config(preset[0], preset[1], preset[2], preset[3], preset[4], preset[5]);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private final Map<String, Config> configs = new LinkedHashMap<>();

    public static PidTuningSavedData get(ServerLevel level) {
        if (level == null || level.getServer() == null) throw new IllegalArgumentException("server level required");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PidTuningSavedData::new, PidTuningSavedData::load),
                DATA_NAME
        );
    }

    public static PidTuningSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PidTuningSavedData data = new PidTuningSavedData();
        ListTag rows = tag.getList("Configs", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            String dimension = row.getString("Dimension");
            if (dimension.isBlank()) continue;
            BlockPos pos = BlockPos.of(row.getLong("Pos"));
            Config config = new Config(
                    row.getInt("Kp"),
                    row.getInt("KiDivisor"),
                    row.getInt("Kd"),
                    row.getInt("DerivativeSmoothing"),
                    row.getInt("RiseLimit"),
                    row.getInt("FallLimit")
            );
            data.configs.putIfAbsent(key(dimension, pos), config);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Map.Entry<String, Config> entry : configs.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            if (parts.length != 2) continue;
            long posLong;
            try {
                posLong = Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            Config config = entry.getValue();
            CompoundTag row = new CompoundTag();
            row.putString("Dimension", parts[0]);
            row.putLong("Pos", posLong);
            row.putInt("Kp", config.kp());
            row.putInt("KiDivisor", config.kiDivisor());
            row.putInt("Kd", config.kd());
            row.putInt("DerivativeSmoothing", config.derivativeSmoothing());
            row.putInt("RiseLimit", config.riseLimit());
            row.putInt("FallLimit", config.fallLimit());
            rows.add(row);
        }
        tag.put("Configs", rows);
        return tag;
    }

    public Config config(ServerLevel level, BlockPos pos) {
        return configs.get(key(level, pos));
    }

    public Config configOrPreset(ServerLevel level, BlockPos pos, int[] preset) {
        Config existing = config(level, pos);
        return existing != null ? existing : Config.fromPreset(preset);
    }

    public Config set(ServerLevel level, BlockPos pos, Config config) {
        configs.put(key(level, pos), config);
        setDirty();
        return config;
    }

    public Config adjust(ServerLevel level, BlockPos pos, int field, int delta, int[] preset) {
        Config current = configOrPreset(level, pos, preset);
        Config next = current.withValue(field, current.value(field) + delta);
        if (next.equals(current)) return current;
        configs.put(key(level, pos), next);
        setDirty();
        return next;
    }

    public boolean remove(ServerLevel level, BlockPos pos) {
        if (configs.remove(key(level, pos)) == null) return false;
        setDirty();
        return true;
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return key(level.dimension().location().toString(), pos);
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }
}
