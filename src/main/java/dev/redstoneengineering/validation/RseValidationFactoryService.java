package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.RoboticsEntityModule;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.operations.world.OperationMaintenanceWorldState;
import dev.redstoneengineering.operations.world.OperationQueueWorldState;
import dev.redstoneengineering.operations.world.OperationRobotTransportWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local/manual world builder for the RSE Operations + AMR Validation Factory.
 *
 * <p>The service owns only test-fixture placement and baseline creation. It intentionally delegates
 * durable Operations state to {@link OperationIndustrialBufferState}, {@link OperationQueueWorldState},
 * and {@link OperationMaintenanceWorldState}. Real transport progression remains owned by
 * {@link OperationRobotTransportWorldState} and {@link EngineeringMobileRobotEntity}; building the
 * factory never fabricates a successful mission.</p>
 */
public final class RseValidationFactoryService {
    private RseValidationFactoryService() {}

    public static final String VALIDATION_PREFIX = "validation:";
    public static final String INPUT_BUFFER_ID = "validation:input_buffer";
    public static final String OUTPUT_BUFFER_ID = "validation:output_buffer";
    public static final String MAIN_QUEUE_ID = "validation:main_queue";
    public static final String READY_RESOURCE_ID = "validation:workcell_ready";
    public static final String DUE_RESOURCE_ID = "validation:workcell_due";
    public static final String DUE_MAINTENANCE_ID = "validation:pm_due";
    public static final String ROBOT_TAG = "rse_validation_robot";
    public static final String ROBOT_ENTITY_ID = "redstoneengineering:engineering_mobile_robot";

    private static final String FULL_FACTORY_TEMPLATE = "validation/full_factory";
    private static final int BUFFER_CAPACITY = 32;
    private static final int QUEUE_CAPACITY = 8;

    private static final Map<String, BlockPos> STATION_OFFSETS;
    static {
        LinkedHashMap<String, BlockPos> offsets = new LinkedHashMap<>();
        // Station structure floors overlay the full-factory floor at the same Y level.
        offsets.put("material_release", new BlockPos(2, 0, 2));
        offsets.put("queue_dispatch", new BlockPos(15, 0, 2));
        offsets.put("maintenance_hold", new BlockPos(28, 0, 2));
        offsets.put("quality_output", new BlockPos(2, 0, 14));
        offsets.put("operations_monitor", new BlockPos(15, 0, 14));
        offsets.put("amr_lane", new BlockPos(2, 0, 28));
        STATION_OFFSETS = Map.copyOf(offsets);
    }

    public record Result(boolean success, List<Component> lines) {
        public Result {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public static Result ok(String... messages) {
            ArrayList<Component> lines = new ArrayList<>();
            for (String message : messages) lines.add(Component.literal(message));
            return new Result(true, lines);
        }

        public static Result fail(String... messages) {
            ArrayList<Component> lines = new ArrayList<>();
            for (String message : messages) lines.add(Component.literal(message));
            return new Result(false, lines);
        }
    }

    public static Result build(ServerLevel level, BlockPos origin) {
        if (level == null) return Result.fail("Validation build failed: server level missing");
        if (origin == null) return Result.fail("Validation build failed: origin missing");

        Result shell = placeResource(level, origin, FULL_FACTORY_TEMPLATE);
        if (!shell.success()) return shell;
        for (Map.Entry<String, BlockPos> entry : STATION_OFFSETS.entrySet()) {
            Result station = place(level, origin.offset(entry.getValue()), entry.getKey());
            if (!station.success()) return station;
        }

        Result fixtures = ensureFixtures(level, origin);
        if (!fixtures.success()) return fixtures;
        Result robot = ensureSingleValidationRobot(level, robotSpawn(origin));
        if (!robot.success()) return robot;

        return Result.ok(
                "RSE Validation Factory built.",
                "Origin: " + origin.toShortString(),
                "Fixtures: input/output buffers, persistent queue, READY/DUE maintenance evidence.",
                "AMR: real idle " + ROBOT_ENTITY_ID + " spawned/tagged; no transport success was fabricated."
        );
    }

    public static Result reset(ServerLevel level, BlockPos origin, String station) {
        if (level == null) return Result.fail("Validation reset failed: server level missing");
        if (origin == null) return Result.fail("Validation reset failed: origin missing");
        String normalized = station == null ? "" : station.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return Result.fail("Validation reset failed: station missing");

        if ("all".equals(normalized)) {
            Result removed = removeMutableFixtures(level);
            if (!removed.success()) return removed;
            discardValidationRobots(level);
            return build(level, origin);
        }

        BlockPos offset = STATION_OFFSETS.get(normalized);
        if (offset == null) {
            return Result.fail("Unknown validation station: " + normalized,
                    "Known: " + String.join(", ", STATION_OFFSETS.keySet()));
        }

        if ("material_release".equals(normalized) || "quality_output".equals(normalized) || "amr_lane".equals(normalized)) {
            OperationBufferSnapshot input = OperationIndustrialBufferState.snapshot(level, INPUT_BUFFER_ID);
            OperationBufferSnapshot output = OperationIndustrialBufferState.snapshot(level, OUTPUT_BUFFER_ID);
            if ((input != null && !input.lots().isEmpty()) || (output != null && !output.lots().isEmpty())) {
                return Result.fail("Station reset blocked: validation buffer contains WIP.");
            }
        }
        if ("queue_dispatch".equals(normalized)) {
            OperationQueueSnapshot queue = OperationQueueWorldState.snapshot(level, MAIN_QUEUE_ID);
            if (queue != null && queue.wip() > 0) {
                return Result.fail("Station reset blocked: validation queue contains WIP.");
            }
        }

        Result placed = place(level, origin.offset(offset), normalized);
        if (!placed.success()) return placed;

        if ("maintenance_hold".equals(normalized)) {
            Result maintenance = seedMaintenance(level);
            if (!maintenance.success()) return maintenance;
        }
        if ("amr_lane".equals(normalized)) {
            discardValidationRobots(level);
            Result robot = ensureSingleValidationRobot(level, robotSpawn(origin));
            if (!robot.success()) return robot;
        }
        return Result.ok("Reset validation station: " + normalized);
    }

    public static Result status(ServerLevel level) {
        if (level == null) return Result.fail("Validation status failed: server level missing");
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE Validation Factory status (observer-only)"));

        OperationBufferSnapshot input = OperationIndustrialBufferState.snapshot(level, INPUT_BUFFER_ID);
        OperationBufferSnapshot output = OperationIndustrialBufferState.snapshot(level, OUTPUT_BUFFER_ID);
        OperationQueueSnapshot queue = OperationQueueWorldState.snapshot(level, MAIN_QUEUE_ID);
        OperationResourceMaintenanceSnapshot ready = OperationMaintenanceWorldState.snapshot(level, READY_RESOURCE_ID);
        OperationResourceMaintenanceSnapshot due = OperationMaintenanceWorldState.snapshot(level, DUE_RESOURCE_ID);

        lines.add(Component.literal("Input buffer: " + bufferStatus(input)));
        lines.add(Component.literal("Output buffer: " + bufferStatus(output)));
        lines.add(Component.literal("Queue: " + queueStatus(queue)));
        lines.add(Component.literal("READY resource: " + maintenanceStatus(ready)));
        lines.add(Component.literal("DUE resource: " + maintenanceStatus(due)));

        List<EngineeringMobileRobotEntity> robots = validationRobots(level);
        if (robots.isEmpty()) {
            lines.add(Component.literal("AMR: MISSING"));
        } else {
            EngineeringMobileRobotEntity robot = robots.getFirst();
            lines.add(Component.literal("AMR: count=" + robots.size()
                    + " state=" + robot.robotState().name()
                    + " route=" + robot.routeReason()
                    + " safety=" + robot.safetyVerdict().name()));
        }
        return new Result(true, lines);
    }

    private static Result ensureFixtures(ServerLevel level, BlockPos origin) {
        BlockPos inputPos = origin.offset(STATION_OFFSETS.get("material_release")).offset(2, 1, 4);
        BlockPos outputPos = origin.offset(STATION_OFFSETS.get("amr_lane")).offset(18, 1, 6);

        OperationBufferSnapshot input = OperationIndustrialBufferState.snapshot(level, INPUT_BUFFER_ID);
        if (input == null) {
            var created = OperationIndustrialBufferState.create(level, INPUT_BUFFER_ID, inputPos, BUFFER_CAPACITY);
            if (created.verdict() != OperationIndustrialBufferState.Verdict.CREATED) {
                return Result.fail("Input buffer fixture rejected: " + created.reason());
            }
        } else if (!input.location().equals(inputPos) || input.capacityUnits() != BUFFER_CAPACITY) {
            return Result.fail("Input buffer fixture conflicts with existing validation identity.");
        }

        OperationBufferSnapshot output = OperationIndustrialBufferState.snapshot(level, OUTPUT_BUFFER_ID);
        if (output == null) {
            var created = OperationIndustrialBufferState.create(level, OUTPUT_BUFFER_ID, outputPos, BUFFER_CAPACITY);
            if (created.verdict() != OperationIndustrialBufferState.Verdict.CREATED) {
                return Result.fail("Output buffer fixture rejected: " + created.reason());
            }
        } else if (!output.location().equals(outputPos) || output.capacityUnits() != BUFFER_CAPACITY) {
            return Result.fail("Output buffer fixture conflicts with existing validation identity.");
        }

        OperationQueueSnapshot queue = OperationQueueWorldState.snapshot(level, MAIN_QUEUE_ID);
        if (queue == null) {
            var created = OperationQueueWorldState.create(level, MAIN_QUEUE_ID, QUEUE_CAPACITY);
            if (created.verdict() != OperationQueueWorldState.Verdict.CREATED) {
                return Result.fail("Queue fixture rejected: " + created.reason());
            }
        } else if (queue.capacity() != QUEUE_CAPACITY) {
            return Result.fail("Queue fixture conflicts with existing validation capacity.");
        }

        return seedMaintenance(level);
    }

    private static Result seedMaintenance(ServerLevel level) {
        long tick = level.getGameTime();
        OperationResourceMaintenanceSnapshot ready = new OperationResourceMaintenanceSnapshot(
                READY_RESOURCE_ID,
                OperationResourceMaintenanceSnapshot.State.AVAILABLE,
                null,
                PortQuality.VALID,
                false
        );
        OperationResourceMaintenanceSnapshot due = new OperationResourceMaintenanceSnapshot(
                DUE_RESOURCE_ID,
                OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE,
                DUE_MAINTENANCE_ID,
                PortQuality.VALID,
                false
        );
        var readyDecision = OperationMaintenanceWorldState.observe(level, ready, tick, "VALIDATION_BASELINE_READY");
        if (readyDecision.verdict() != OperationMaintenanceWorldState.Verdict.OBSERVED) {
            return Result.fail("READY maintenance fixture rejected: " + readyDecision.reason());
        }
        var dueDecision = OperationMaintenanceWorldState.observe(level, due, tick, "VALIDATION_BASELINE_DUE");
        if (dueDecision.verdict() != OperationMaintenanceWorldState.Verdict.OBSERVED) {
            return Result.fail("DUE maintenance fixture rejected: " + dueDecision.reason());
        }
        return Result.ok("Validation maintenance fixtures seeded.");
    }

    private static Result removeMutableFixtures(ServerLevel level) {
        OperationBufferSnapshot input = OperationIndustrialBufferState.snapshot(level, INPUT_BUFFER_ID);
        if (input != null) {
            var removed = OperationIndustrialBufferState.remove(level, INPUT_BUFFER_ID);
            if (removed.verdict() != OperationIndustrialBufferState.Verdict.REMOVED) {
                return Result.fail("Reset blocked by input buffer: " + removed.reason());
            }
        }

        OperationBufferSnapshot output = OperationIndustrialBufferState.snapshot(level, OUTPUT_BUFFER_ID);
        if (output != null) {
            var removed = OperationIndustrialBufferState.remove(level, OUTPUT_BUFFER_ID);
            if (removed.verdict() != OperationIndustrialBufferState.Verdict.REMOVED) {
                return Result.fail("Reset blocked by output buffer: " + removed.reason());
            }
        }

        OperationQueueSnapshot queue = OperationQueueWorldState.snapshot(level, MAIN_QUEUE_ID);
        if (queue != null) {
            var removed = OperationQueueWorldState.remove(level, MAIN_QUEUE_ID);
            if (removed.verdict() != OperationQueueWorldState.Verdict.REMOVED) {
                return Result.fail("Reset blocked by queue: " + removed.reason());
            }
        }
        return Result.ok("Validation mutable fixtures removed through production facades.");
    }

    private static Result place(ServerLevel level, BlockPos position, String structureName) {
        return placeResource(level, position, "validation/" + structureName);
    }

    private static Result placeResource(ServerLevel level, BlockPos position, String resourcePath) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, resourcePath);
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        if (template == null) {
            return Result.fail("Missing validation structure: " + id);
        }
        boolean placed = template.placeInWorld(
                level,
                position,
                position,
                new StructurePlaceSettings(),
                level.getRandom(),
                2
        );
        return placed
                ? Result.ok("Placed " + id)
                : Result.fail("Failed to place validation structure: " + id);
    }

    private static Result ensureSingleValidationRobot(ServerLevel level, BlockPos spawn) {
        List<EngineeringMobileRobotEntity> robots = validationRobots(level);
        if (robots.size() > 1) {
            return Result.fail("Validation AMR fixture is ambiguous: " + robots.size() + " tagged robots found.");
        }
        if (robots.size() == 1) return Result.ok("Validation AMR already present.");

        EngineeringMobileRobotEntity robot = new EngineeringMobileRobotEntity(
                RoboticsEntityModule.ENGINEERING_MOBILE_ROBOT.get(), level);
        robot.setPos(spawn.getX() + 0.5D, spawn.getY() + 0.05D, spawn.getZ() + 0.5D);
        robot.addTag(ROBOT_TAG);
        robot.setCustomName(Component.literal("RSE Validation AMR"));
        robot.setCustomNameVisible(true);
        if (!level.addFreshEntity(robot)) {
            return Result.fail("Failed to spawn real validation AMR entity.");
        }
        return Result.ok("Validation AMR spawned.");
    }

    private static List<EngineeringMobileRobotEntity> validationRobots(ServerLevel level) {
        return List.copyOf(level.getEntities(
                EntityTypeTest.forClass(EngineeringMobileRobotEntity.class),
                robot -> robot.getTags().contains(ROBOT_TAG)
        ));
    }

    private static void discardValidationRobots(ServerLevel level) {
        for (EngineeringMobileRobotEntity robot : validationRobots(level)) {
            robot.discard();
        }
    }

    private static BlockPos robotSpawn(BlockPos origin) {
        return origin.offset(STATION_OFFSETS.get("amr_lane")).offset(2, 1, 4);
    }

    private static String bufferStatus(OperationBufferSnapshot snapshot) {
        if (snapshot == null) return "MISSING";
        return snapshot.bufferId() + " units=" + snapshot.usedUnits() + "/" + snapshot.capacityUnits();
    }

    private static String queueStatus(OperationQueueSnapshot snapshot) {
        if (snapshot == null) return "MISSING";
        return MAIN_QUEUE_ID + " queued=" + snapshot.queued().size()
                + " active=" + snapshot.active().size()
                + " capacity=" + snapshot.capacity();
    }

    private static String maintenanceStatus(OperationResourceMaintenanceSnapshot snapshot) {
        if (snapshot == null) return "MISSING";
        return snapshot.resourceId() + " state=" + snapshot.state().name()
                + " productionReady=" + snapshot.productionReady();
    }
}
