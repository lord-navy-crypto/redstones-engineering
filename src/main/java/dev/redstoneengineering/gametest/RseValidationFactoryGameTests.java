package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.operations.world.OperationMaintenanceWorldState;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationQueueWorldState;
import dev.redstoneengineering.validation.RseValidationFactoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Local/manual smoke coverage for the playable Operations + AMR validation environment. */
public final class RseValidationFactoryGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseValidationFactoryGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void validationFactoryBuildsPhysicalStations(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        OperationPlantSavedData before = OperationPlantSavedData.get(level);
        long terminalLogisticsBefore = before.plantEvents(OperationPlantEvent.Type.LOGISTICS).stream()
                .filter(event -> event.detail().contains("DELIVERED"))
                .count();
        long completedJobsBefore = before.jobLifecycles().stream()
                .filter(record -> record.status().name().equals("COMPLETED"))
                .count();

        var result = RseValidationFactoryService.build(level, origin);
        if (!result.success()) {
            helper.fail("Validation Factory build failed: " + result.lines());
            return;
        }

        var input = OperationIndustrialBufferState.snapshot(level, RseValidationFactoryService.INPUT_BUFFER_ID);
        var output = OperationIndustrialBufferState.snapshot(level, RseValidationFactoryService.OUTPUT_BUFFER_ID);
        if (input == null || output == null || !input.lots().isEmpty() || !output.lots().isEmpty()) {
            helper.fail("Validation input/output buffers are missing or not empty at baseline");
            return;
        }

        var queue = OperationQueueWorldState.snapshot(level, RseValidationFactoryService.MAIN_QUEUE_ID);
        if (queue == null || queue.wip() != 0 || queue.capacity() <= 0) {
            helper.fail("Validation queue is missing or not empty at baseline");
            return;
        }

        var ready = OperationMaintenanceWorldState.snapshot(level, RseValidationFactoryService.READY_RESOURCE_ID);
        var due = OperationMaintenanceWorldState.snapshot(level, RseValidationFactoryService.DUE_RESOURCE_ID);
        if (ready == null
                || ready.state() != OperationResourceMaintenanceSnapshot.State.AVAILABLE
                || !ready.productionReady()) {
            helper.fail("Validation READY maintenance evidence is missing");
            return;
        }
        if (due == null
                || due.state() != OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE
                || due.productionReady()) {
            helper.fail("Validation MAINTENANCE_DUE evidence is missing");
            return;
        }

        int validationRobots = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EngineeringMobileRobotEntity
                    && entity.getTags().contains(RseValidationFactoryService.ROBOT_TAG)) {
                validationRobots++;
            }
        }
        if (validationRobots != 1) {
            helper.fail("Expected exactly one tagged validation AMR, found " + validationRobots);
            return;
        }

        OperationPlantSavedData after = OperationPlantSavedData.get(level);
        long terminalLogisticsAfter = after.plantEvents(OperationPlantEvent.Type.LOGISTICS).stream()
                .filter(event -> event.detail().contains("DELIVERED"))
                .count();
        long completedJobsAfter = after.jobLifecycles().stream()
                .filter(record -> record.status().name().equals("COMPLETED"))
                .count();
        if (terminalLogisticsAfter != terminalLogisticsBefore || completedJobsAfter != completedJobsBefore) {
            helper.fail("Building validation fixtures fabricated completed/delivered production history");
            return;
        }

        helper.succeed();
    }
}
