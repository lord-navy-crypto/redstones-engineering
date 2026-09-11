package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultLatchBlock;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.block.RedundantVoterBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Shared no-inventory menu base for RSE engineering instruments and controllers. */
public abstract class EngineeringDeviceMenu extends AbstractContainerMenu {
    public static final int HEALTH_NOMINAL = 0;
    public static final int HEALTH_ACTIVE = 1;
    public static final int HEALTH_PROTECTIVE = 2;
    public static final int HEALTH_DEGRADED = 3;
    public static final int HEALTH_FAULT = 4;

    public static final int TOPOLOGY_UNKNOWN = 0;
    public static final int TOPOLOGY_SERIES = 1;
    public static final int TOPOLOGY_SOURCE = 2;
    public static final int TOPOLOGY_SINK = 3;
    public static final int TOPOLOGY_OBSERVER = 4;
    public static final int TOPOLOGY_PASSIVE = 5;
    public static final int TOPOLOGY_EXPLICIT_JUNCTION = 6;
    public static final int TOPOLOGY_MULTIPORT = 7;
    public static final int TOPOLOGY_CONTROLLED_SOURCE = 8;
    public static final int TOPOLOGY_CONTROLLED_SERIES = 9;
    public static final int TOPOLOGY_PASSIVE_SERIES = 10;

    public static final int EVIDENCE_UNOBSERVED = 0;
    public static final int EVIDENCE_VALID = 1;
    public static final int EVIDENCE_NO_SIGNAL = 2;
    public static final int EVIDENCE_SATURATED = 3;
    public static final int EVIDENCE_STALE = 4;
    public static final int EVIDENCE_FAULT = 5;
    public static final int EVIDENCE_DOMAIN_MISMATCH = 6;
    public static final int EVIDENCE_TOPOLOGY_ERROR = 7;

    protected final Inventory playerInventory;
    protected final Level level;
    protected final BlockPos blockPos;
    private final Block expectedBlock;
    private final DataSlot operationalHealth;
    private final DataSlot topologyRole;
    private final DataSlot evidenceState;

    protected EngineeringDeviceMenu(
            MenuType<?> type,
            int containerId,
            Inventory playerInventory,
            BlockPos blockPos,
            Block expectedBlock
    ) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.level = playerInventory.player.level();
        this.blockPos = blockPos;
        this.expectedBlock = expectedBlock;
        this.operationalHealth = trackedInt();
        this.topologyRole = trackedInt();
        this.evidenceState = trackedInt();
    }

    protected DataSlot trackedInt() {
        DataSlot slot = DataSlot.standalone();
        addDataSlot(slot);
        return slot;
    }

    /** Allocate a compact fixed-size synchronized integer vector for bounded engineering telemetry. */
    protected DataSlot[] trackedInts(int count) {
        int bounded = Math.max(0, count);
        DataSlot[] slots = new DataSlot[bounded];
        for (int i = 0; i < bounded; i++) slots[i] = trackedInt();
        return slots;
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    /**
     * Operational/safety state is intentionally independent from evidence validity.
     * A watchdog timeout, relief event, latched fault, degraded vote, or commanded brake
     * can all be authoritative data while the device is in a non-nominal operating state.
     */
    public int operationalHealth() {
        return operationalHealth.get();
    }

    public String operationalHealthLabel() {
        return switch (operationalHealth()) {
            case HEALTH_ACTIVE -> "ACTIVE";
            case HEALTH_PROTECTIVE -> "PROTECTIVE";
            case HEALTH_DEGRADED -> "DEGRADED";
            case HEALTH_FAULT -> "FAULT";
            default -> "NOMINAL";
        };
    }

    /** Server-derived physical role from the formal Engineering Port contract. */
    public int topologyRole() {
        return topologyRole.get();
    }

    public String topologyRoleLabel() {
        return switch (topologyRole()) {
            case TOPOLOGY_SERIES -> "SERIES";
            case TOPOLOGY_SOURCE -> "SOURCE";
            case TOPOLOGY_SINK -> "SINK";
            case TOPOLOGY_OBSERVER -> "OBSERVER";
            case TOPOLOGY_PASSIVE -> "PASSIVE";
            case TOPOLOGY_EXPLICIT_JUNCTION -> "EXPLICIT JUNCTION";
            case TOPOLOGY_MULTIPORT -> "MULTIPORT";
            case TOPOLOGY_CONTROLLED_SOURCE -> "CONTROLLED SOURCE";
            case TOPOLOGY_CONTROLLED_SERIES -> "CONTROLLED SERIES";
            case TOPOLOGY_PASSIVE_SERIES -> "PASSIVE SERIES";
            default -> "UNCLASSIFIED";
        };
    }

    /** Aggregated runtime evidence quality from formal Engineering Port snapshots only. */
    public int evidenceState() {
        return evidenceState.get();
    }

    public String evidenceStateLabel() {
        return switch (evidenceState()) {
            case EVIDENCE_VALID -> "VALID";
            case EVIDENCE_NO_SIGNAL -> "NO SIGNAL";
            case EVIDENCE_SATURATED -> "SATURATED";
            case EVIDENCE_STALE -> "STALE";
            case EVIDENCE_FAULT -> "FAULT";
            case EVIDENCE_DOMAIN_MISMATCH -> "DOMAIN MISMATCH";
            case EVIDENCE_TOPOLOGY_ERROR -> "TOPOLOGY ERROR";
            default -> "UNOBSERVED";
        };
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(blockPos).is(expectedBlock)) return false;
        double dx = player.getX() - (blockPos.getX() + 0.5D);
        double dy = player.getY() - (blockPos.getY() + 0.5D);
        double dz = player.getZ() - (blockPos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) {
            refreshAuthoritativeSnapshot();
            refreshOperationalHealth();
            refreshTopologyRole();
            refreshEvidenceState();
        }
        super.broadcastChanges();
    }

    private void refreshOperationalHealth() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        int health = HEALTH_NOMINAL;

        if (block instanceof PneumaticReliefValveBlock) {
            health = PneumaticReliefValveBlock.venting(level, blockPos)
                    ? HEALTH_PROTECTIVE : HEALTH_NOMINAL;
        } else if (block instanceof WatchdogBlock) {
            health = state.getValue(DirectionalSignalBlock.OUTPUT) > 0
                    ? HEALTH_PROTECTIVE : HEALTH_NOMINAL;
        } else if (block instanceof ServoActuatorBlock) {
            health = ServoActuatorBlock.braking(level, blockPos)
                    ? HEALTH_PROTECTIVE : HEALTH_ACTIVE;
        } else if (block instanceof RedundantVoterBlock) {
            health = RedundantVoterBlock.degraded(level, blockPos)
                    ? HEALTH_DEGRADED : HEALTH_NOMINAL;
        } else if (block instanceof FaultLatchBlock) {
            health = FaultLatchBlock.latched(level, blockPos)
                    ? HEALTH_FAULT : HEALTH_NOMINAL;
        } else if (block instanceof OperationsMonitorBlock) {
            OperationsMonitorBlock.SystemState systemState = OperationsMonitorBlock.SystemState.values()[
                    Math.max(0, Math.min(
                            OperationsMonitorBlock.SystemState.values().length - 1,
                            OperationsMonitorBlock.stateOrdinal(level, blockPos)
                    ))
            ];
            health = switch (systemState) {
                case NOMINAL -> HEALTH_NOMINAL;
                case CONGESTED, NOISY, UNSTABLE -> HEALTH_DEGRADED;
                case OVERLOADED, SAFETY_LIMITED -> HEALTH_PROTECTIVE;
                case FAILED -> HEALTH_FAULT;
            };
        }

        operationalHealth.set(health);
    }

    private void refreshTopologyRole() {
        topologyRole.set(classifyTopologyRole(level.getBlockState(blockPos)));
    }

    private void refreshEvidenceState() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        if (!(block instanceof EngineeringPortProvider provider)) {
            evidenceState.set(EVIDENCE_UNOBSERVED);
            return;
        }

        int aggregate = EVIDENCE_UNOBSERVED;
        for (EngineeringPort port : provider.engineeringPorts(state)) {
            var snapshot = provider.engineeringSnapshot(level, blockPos, state, port.side());
            if (snapshot.isEmpty()) continue;
            aggregate = Math.max(aggregate, evidenceCode(snapshot.get().quality()));
        }
        evidenceState.set(aggregate);
    }

    private static int evidenceCode(PortQuality quality) {
        return switch (quality) {
            case VALID -> EVIDENCE_VALID;
            case NO_SIGNAL -> EVIDENCE_NO_SIGNAL;
            case SATURATED -> EVIDENCE_SATURATED;
            case STALE -> EVIDENCE_STALE;
            case FAULT -> EVIDENCE_FAULT;
            case DOMAIN_MISMATCH -> EVIDENCE_DOMAIN_MISMATCH;
            case TOPOLOGY_ERROR -> EVIDENCE_TOPOLOGY_ERROR;
        };
    }

    /**
     * Conservative physical-role projection derived only from the formal Engineering Port contract.
     * It does not solve networks or infer hidden virtual ports.
     */
    public static int classifyTopologyRole(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof EngineeringPortProvider provider)) return TOPOLOGY_UNKNOWN;

        List<EngineeringPort> ports = provider.engineeringPorts(state);
        if (ports.isEmpty()) return TOPOLOGY_PASSIVE;
        if (block.getClass().getSimpleName().contains("Junction")) return TOPOLOGY_EXPLICIT_JUNCTION;

        int receivers = 0;
        int transmitters = 0;
        int controlReceivers = 0;
        boolean observational = true;
        boolean bidirectional = false;
        for (EngineeringPort port : ports) {
            if (port.canReceive()) {
                receivers++;
                if (port.kind() == PortKind.CONTROL) controlReceivers++;
            }
            if (port.canTransmit()) transmitters++;
            bidirectional |= port.canReceive() && port.canTransmit();
            observational &= port.kind() == PortKind.TAP || port.kind() == PortKind.MEASUREMENT;
        }

        if (observational && transmitters == 0) return TOPOLOGY_OBSERVER;
        if (receivers == 0 && transmitters > 0) return TOPOLOGY_SOURCE;
        if (receivers > 0 && transmitters == 0) return TOPOLOGY_SINK;
        if (bidirectional && receivers == ports.size() && transmitters == ports.size()) {
            return ports.size() == 2 ? TOPOLOGY_PASSIVE_SERIES : TOPOLOGY_PASSIVE;
        }
        if (ports.size() == 2 && receivers > 0 && transmitters > 0) return TOPOLOGY_SERIES;

        // Directional-domain devices keep a strict BACK→FRONT process path; extra ports are controls,
        // not permission for implicit parallel routing through the process medium.
        if (block instanceof DirectionalDomainBlock && receivers > 0 && transmitters > 0 && ports.size() > 2) {
            return TOPOLOGY_CONTROLLED_SERIES;
        }

        // A single explicit control input feeding one or more outputs is a controlled source, not
        // a generic multipoint processor. Fan-out is still explicit in the formal port list.
        if (receivers == 1 && controlReceivers == 1 && transmitters > 0) {
            return TOPOLOGY_CONTROLLED_SOURCE;
        }

        if (receivers > 0 || transmitters > 0) return TOPOLOGY_MULTIPORT;
        return TOPOLOGY_PASSIVE;
    }

    protected abstract void refreshAuthoritativeSnapshot();
}
