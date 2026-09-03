package dev.redstoneengineering.integration.jade;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-backed Jade view of the face currently targeted by the player.
 *
 * <p>The provider serializes only presentation data derived from the native RSE
 * contract. Runtime cable values and other server-owned observations therefore
 * do not need to be guessed on the client.</p>
 */
public enum EngineeringPortJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.parse("redstoneengineering:engineering_ports");

    private static final String KEY_PRESENT = "rse_ep_present";
    private static final String KEY_COUNT = "rse_ep_count";
    private static final String KEY_SIDE = "rse_ep_side";
    private static final String KEY_HAS_PORT = "rse_ep_has_port";
    private static final String KEY_LABEL = "rse_ep_label";
    private static final String KEY_DOMAIN = "rse_ep_domain";
    private static final String KEY_KIND = "rse_ep_kind";
    private static final String KEY_DIRECTION = "rse_ep_direction";
    private static final String KEY_UNIT = "rse_ep_unit";
    private static final String KEY_REDSTONE = "rse_ep_redstone";
    private static final String KEY_HAS_SNAPSHOT = "rse_ep_has_snapshot";
    private static final String KEY_VALUE = "rse_ep_value";
    private static final String KEY_MINIMUM = "rse_ep_minimum";
    private static final String KEY_MAXIMUM = "rse_ep_maximum";
    private static final String KEY_NORMALIZED = "rse_ep_normalized";
    private static final String KEY_QUALITY = "rse_ep_quality";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlock() instanceof EngineeringPortProvider provider)) {
            return;
        }

        BlockState state = accessor.getBlockState();
        List<EngineeringPort> ports = provider.engineeringPorts(state);
        data.putBoolean(KEY_PRESENT, true);
        data.putInt(KEY_COUNT, ports.size());
        data.putString(KEY_SIDE, accessor.getSide().getName());

        Optional<EngineeringPort> port = provider.engineeringPort(state, accessor.getSide());
        if (port.isEmpty()) {
            data.putBoolean(KEY_HAS_PORT, false);
            return;
        }

        EngineeringPort descriptor = port.get();
        data.putBoolean(KEY_HAS_PORT, true);
        data.putString(KEY_LABEL, descriptor.label());
        data.putString(KEY_DOMAIN, descriptor.domain().label());
        data.putString(KEY_KIND, descriptor.kind().name());
        data.putString(KEY_DIRECTION, descriptor.direction().name());
        data.putString(KEY_UNIT, descriptor.unit());
        data.putBoolean(KEY_REDSTONE, descriptor.redstoneConnectable());

        Optional<EngineeringPortSnapshot> snapshot = provider.engineeringSnapshot(
                accessor.getLevel(),
                accessor.getPosition(),
                state,
                accessor.getSide()
        );
        if (snapshot.isEmpty()) {
            data.putBoolean(KEY_HAS_SNAPSHOT, false);
            return;
        }

        EngineeringPortSnapshot observation = snapshot.get();
        data.putBoolean(KEY_HAS_SNAPSHOT, true);
        data.putDouble(KEY_VALUE, observation.value());
        data.putDouble(KEY_MINIMUM, observation.minimum());
        data.putDouble(KEY_MAXIMUM, observation.maximum());
        data.putDouble(KEY_NORMALIZED, observation.normalized());
        data.putString(KEY_QUALITY, observation.quality().name());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(KEY_PRESENT)) {
            return;
        }

        int count = data.getInt(KEY_COUNT);
        String side = data.getString(KEY_SIDE).toUpperCase(Locale.ROOT);
        if (!data.getBoolean(KEY_HAS_PORT)) {
            tooltip.add(Component.literal("RSE ports: " + count + " | " + side + " = ISOLATED"));
            return;
        }

        tooltip.add(Component.literal(
                "RSE " + side + " • " + data.getString(KEY_LABEL)
        ));
        tooltip.add(Component.literal(
                data.getString(KEY_DOMAIN)
                        + " • " + data.getString(KEY_KIND)
                        + " • " + data.getString(KEY_DIRECTION)
        ));

        if (data.getBoolean(KEY_HAS_SNAPSHOT)) {
            double value = data.getDouble(KEY_VALUE);
            double minimum = data.getDouble(KEY_MINIMUM);
            double maximum = data.getDouble(KEY_MAXIMUM);
            int percent = (int) Math.round(data.getDouble(KEY_NORMALIZED) * 100.0);
            tooltip.add(Component.literal(
                    "Value " + format(value)
                            + " " + data.getString(KEY_UNIT)
                            + " | range " + format(minimum) + ".." + format(maximum)
                            + " | " + percent + "%"
            ));
            tooltip.add(Component.literal("Quality: " + data.getString(KEY_QUALITY)));
        } else {
            tooltip.add(Component.literal("Value: structural / multi-channel port"));
        }

        if (data.getBoolean(KEY_REDSTONE)) {
            tooltip.add(Component.literal("Vanilla redstone attachment: YES"));
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0e-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
