package dev.redstoneengineering.integration.jade;

import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptance;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceIssue;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptancePresentation;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyFaceSnapshot;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import net.minecraft.core.Direction;
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
 * Server-backed Jade view of the targeted face, read-only topology, and PID acceptance evidence.
 *
 * <p>The provider serializes only presentation data derived from native RSE contracts. Runtime
 * values, topology ownership, commissioning, and acceptance remain server-authoritative; the HUD
 * never becomes a controller, sampler, or topology solver.</p>
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
    private static final String KEY_TOPOLOGY_CONNECTED = "rse_ep_topology_connected";
    private static final String KEY_TOPOLOGY_ISSUES = "rse_ep_topology_issues";
    private static final String KEY_TOPOLOGY_FACE_PREFIX = "rse_ep_topology_face_";

    private static final String KEY_ACCEPTANCE_PRESENT = "rse_acceptance_present";
    private static final String KEY_ACCEPTANCE_STATUS = "rse_acceptance_status";
    private static final String KEY_COMMISSIONING_STATUS = "rse_acceptance_commissioning_status";
    private static final String KEY_COMMISSIONING_SCORE = "rse_acceptance_commissioning_score";
    private static final String KEY_ACCEPTANCE_ISSUE_COUNT = "rse_acceptance_issue_count";
    private static final String KEY_ACCEPTANCE_ISSUE_CODE = "rse_acceptance_issue_code";
    private static final String KEY_ACCEPTANCE_ISSUE_DETAIL = "rse_acceptance_issue_detail";
    private static final String KEY_ACCEPTANCE_HEADLINE = "rse_acceptance_headline";
    private static final String KEY_ACCEPTANCE_ISSUE_LINE = "rse_acceptance_issue_line";
    private static final String KEY_ACCEPTANCE_TRACE = "rse_acceptance_trace";

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

        TopologyVisualizationSnapshot topology = EngineeringTopologyView.inspect(
                accessor.getLevel(), accessor.getPosition(), state);
        data.putInt(KEY_TOPOLOGY_CONNECTED, topology.connectedCount());
        data.putInt(KEY_TOPOLOGY_ISSUES, topology.issueCount());
        for (TopologyFaceSnapshot face : topology.faces()) {
            data.putString(
                    KEY_TOPOLOGY_FACE_PREFIX + face.side().getName(),
                    face.hasPort() ? face.compact() : ""
            );
        }

        if (accessor.getBlock() instanceof PidControllerBlock) {
            appendAcceptanceServerData(data, accessor, topology);
        }

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

    private static void appendAcceptanceServerData(
            CompoundTag data,
            BlockAccessor accessor,
            TopologyVisualizationSnapshot topology
    ) {
        CommissioningSnapshot commissioning = ClosedLoopCommissioning.inspectPid(
                accessor.getLevel(), accessor.getPosition());
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(topology, commissioning);

        data.putBoolean(KEY_ACCEPTANCE_PRESENT, true);
        data.putString(KEY_ACCEPTANCE_STATUS, acceptance.status().name());
        data.putString(KEY_COMMISSIONING_STATUS, acceptance.commissioningStatus().name());
        data.putInt(KEY_COMMISSIONING_SCORE, acceptance.commissioningScore());
        data.putInt(KEY_ACCEPTANCE_ISSUE_COUNT, acceptance.issues().size());
        data.putString(KEY_ACCEPTANCE_HEADLINE, EngineeringAcceptancePresentation.headline(acceptance));
        data.putString(KEY_ACCEPTANCE_ISSUE_LINE, EngineeringAcceptancePresentation.firstIssueLine(acceptance));
        data.putString(KEY_ACCEPTANCE_TRACE, acceptance.traceKey());

        if (!acceptance.issues().isEmpty()) {
            EngineeringAcceptanceIssue issue = acceptance.issues().get(0);
            data.putString(KEY_ACCEPTANCE_ISSUE_CODE, issue.code());
            data.putString(KEY_ACCEPTANCE_ISSUE_DETAIL, issue.detail());
        }
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
            appendTopology(tooltip, data, count);
            appendAcceptance(tooltip, data);
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
        appendTopology(tooltip, data, count);
        appendAcceptance(tooltip, data);
    }

    private static void appendTopology(ITooltip tooltip, CompoundTag data, int portCount) {
        tooltip.add(Component.literal(
                "Topology: connected " + data.getInt(KEY_TOPOLOGY_CONNECTED) + "/" + portCount
                        + " | issues " + data.getInt(KEY_TOPOLOGY_ISSUES)
        ));
        for (Direction direction : Direction.values()) {
            String face = data.getString(KEY_TOPOLOGY_FACE_PREFIX + direction.getName());
            if (!face.isBlank()) {
                tooltip.add(Component.literal("• " + face));
            }
        }
    }

    private static void appendAcceptance(ITooltip tooltip, CompoundTag data) {
        if (!data.getBoolean(KEY_ACCEPTANCE_PRESENT)) return;
        tooltip.add(Component.literal(data.getString(KEY_ACCEPTANCE_HEADLINE)));
        if (data.getInt(KEY_ACCEPTANCE_ISSUE_COUNT) > 0) {
            String issueLine = data.getString(KEY_ACCEPTANCE_ISSUE_LINE);
            if (!issueLine.isBlank()) tooltip.add(Component.literal(issueLine));
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
