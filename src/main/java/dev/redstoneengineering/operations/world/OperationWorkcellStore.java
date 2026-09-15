package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-authoritative explicit workcell binding facade.
 * Membership changes are deliberate operator actions; discovery is never proximity based.
 */
public final class OperationWorkcellStore {
    private OperationWorkcellStore() {}

    public enum Verdict {
        BOUND,
        UNBOUND,
        WAIT,
        SAFE_STOP
    }

    public record Decision(Verdict verdict, String reason, OperationWorkcellBinding binding) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean changed() {
            return verdict == Verdict.BOUND || verdict == Verdict.UNBOUND;
        }
    }

    public record ResolvedResource(
            OperationWorkcellBinding.ResourceBinding binding,
            OperationWorldResourceSnapshot snapshot,
            PortQuality quality,
            String reason
    ) {
        public ResolvedResource {
            if (quality == null) quality = PortQuality.FAULT;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean valid() {
            return binding != null
                    && snapshot != null
                    && snapshot.validEvidence()
                    && quality == PortQuality.VALID
                    && binding.expectedResourceId().equals(snapshot.resourceId());
        }
    }

    public static Decision bindResource(ServerLevel level, String workcellId, BlockPos position) {
        String normalizedWorkcell = normalize(workcellId);
        if (normalizedWorkcell.isBlank()) return safeStop("WORKCELL_ID_MISSING", null);
        if (level == null || position == null) return safeStop("RESOURCE_POSITION_MISSING", null);

        Optional<OperationWorldResourceSnapshot> resolved = OperationWorldResourceResolver.resolve(level, position);
        if (resolved.isEmpty()) return safeStop("RESOURCE_PROVIDER_MISSING", null);
        OperationWorldResourceSnapshot resource = resolved.get();
        if (!resource.validEvidence()) return safeStop("RESOURCE_EVIDENCE_INVALID", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        for (OperationWorkcellBinding workcell : data.workcells()) {
            for (OperationWorkcellBinding.ResourceBinding existing : workcell.resourceBindings()) {
                if (existing.position().equals(position)) {
                    return safeStop("RESOURCE_POSITION_ALREADY_BOUND", workcell);
                }
                if (existing.expectedResourceId().equals(resource.resourceId())) {
                    return safeStop("RESOURCE_ID_ALREADY_BOUND", workcell);
                }
            }
        }

        OperationWorkcellBinding current = data.workcell(normalizedWorkcell);
        ArrayList<OperationWorkcellBinding.ResourceBinding> resources = new ArrayList<>();
        if (current != null) resources.addAll(current.resourceBindings());
        resources.add(new OperationWorkcellBinding.ResourceBinding(position, resource.resourceId()));
        OperationWorkcellBinding updated = new OperationWorkcellBinding(normalizedWorkcell, resources);
        if (!updated.validBinding()) return safeStop("WORKCELL_BINDING_INVALID", current);
        if (!data.putWorkcell(updated)) return safeStop("WORKCELL_PERSISTENCE_REJECTED", current);
        return new Decision(Verdict.BOUND, "RESOURCE_BOUND", updated);
    }

    public static Decision unbindResource(ServerLevel level, String workcellId, BlockPos position) {
        String normalizedWorkcell = normalize(workcellId);
        if (normalizedWorkcell.isBlank()) return safeStop("WORKCELL_ID_MISSING", null);
        if (level == null || position == null) return safeStop("RESOURCE_POSITION_MISSING", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationWorkcellBinding current = data.workcell(normalizedWorkcell);
        if (current == null) return waitFor("WORKCELL_NOT_FOUND", null);

        ArrayList<OperationWorkcellBinding.ResourceBinding> resources = new ArrayList<>();
        boolean removed = false;
        for (OperationWorkcellBinding.ResourceBinding resource : current.resourceBindings()) {
            if (resource.position().equals(position)) removed = true;
            else resources.add(resource);
        }
        if (!removed) return waitFor("RESOURCE_NOT_BOUND_TO_WORKCELL", current);

        OperationWorkcellBinding updated = new OperationWorkcellBinding(normalizedWorkcell, resources);
        if (!data.putWorkcell(updated)) return safeStop("WORKCELL_PERSISTENCE_REJECTED", current);
        return new Decision(Verdict.UNBOUND, "RESOURCE_UNBOUND", updated);
    }

    public static List<ResolvedResource> resolveBoundResources(ServerLevel level, String workcellId) {
        String normalizedWorkcell = normalize(workcellId);
        if (level == null || normalizedWorkcell.isBlank()) return List.of();
        OperationWorkcellBinding binding = OperationPlantSavedData.get(level).workcell(normalizedWorkcell);
        if (binding == null || !binding.validBinding()) return List.of();

        ArrayList<ResolvedResource> resolved = new ArrayList<>();
        for (OperationWorkcellBinding.ResourceBinding member : binding.resourceBindings()) {
            Optional<OperationWorldResourceSnapshot> snapshot =
                    OperationWorldResourceResolver.resolve(level, member.position());
            if (snapshot.isEmpty()) {
                resolved.add(new ResolvedResource(member, null, PortQuality.NO_SIGNAL, "RESOURCE_PROVIDER_MISSING"));
                continue;
            }
            OperationWorldResourceSnapshot evidence = snapshot.get();
            if (!member.expectedResourceId().equals(evidence.resourceId())) {
                resolved.add(new ResolvedResource(member, evidence, PortQuality.FAULT, "RESOURCE_ID_MISMATCH"));
                continue;
            }
            if (!evidence.validEvidence()) {
                resolved.add(new ResolvedResource(member, evidence, evidence.evidenceQuality(), "RESOURCE_EVIDENCE_INVALID"));
                continue;
            }
            resolved.add(new ResolvedResource(member, evidence, PortQuality.VALID, "RESOURCE_EVIDENCE_VALID"));
        }
        return List.copyOf(resolved);
    }

    public static Collection<OperationWorkcellBinding> workcells(ServerLevel level) {
        if (level == null) return List.of();
        return OperationPlantSavedData.get(level).workcells();
    }

    public static boolean identitiesUnique(Collection<OperationWorkcellBinding> workcells) {
        if (workcells == null) return false;
        Set<String> workcellIds = new HashSet<>();
        Set<BlockPos> positions = new HashSet<>();
        Set<String> resourceIds = new HashSet<>();
        for (OperationWorkcellBinding workcell : workcells) {
            if (workcell == null || !workcell.validBinding() || !workcellIds.add(workcell.workcellId())) return false;
            for (OperationWorkcellBinding.ResourceBinding resource : workcell.resourceBindings()) {
                if (!positions.add(resource.position())) return false;
                if (!resourceIds.add(resource.expectedResourceId())) return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Decision waitFor(String reason, OperationWorkcellBinding binding) {
        return new Decision(Verdict.WAIT, reason, binding);
    }

    private static Decision safeStop(String reason, OperationWorkcellBinding binding) {
        return new Decision(Verdict.SAFE_STOP, reason, binding);
    }
}
