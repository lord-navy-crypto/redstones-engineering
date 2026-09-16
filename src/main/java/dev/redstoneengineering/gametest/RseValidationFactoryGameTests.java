package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.operations.world.OperationMaintenanceWorldState;
import dev.redstoneengineering.operations.world.OperationPlantEvent;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationQueueWorldState;
import dev.redstoneengineering.validation.RseValidationFactoryService;
import dev.redstoneengineering.validation.RseValidationSelfTestService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
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

        int validationRobots = level.getEntities(
                EntityTypeTest.forClass(EngineeringMobileRobotEntity.class),
                robot -> robot.getTags().contains(RseValidationFactoryService.ROBOT_TAG)
        ).size();
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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void selfTestReferenceSourceUsesRealWorldEvidence(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        var placed = RseValidationSelfTestService.place(level, origin, "01_basic/reference_source");
        if (!placed.success()) {
            helper.fail("Reference-source self-test placement failed: " + placed.lines());
            return;
        }

        BlockPos sourcePos = origin.offset(3, 1, 4);
        if (!(level.getBlockState(sourcePos).getBlock() instanceof RedstoneReferenceSourceBlock)
                || level.getBlockState(sourcePos).getValue(RedstoneReferenceSourceBlock.POWER) != 7) {
            helper.fail("Self-test did not place the configured real RSE reference source");
            return;
        }

        helper.runAfterDelay(6, () -> {
            var checked = RseValidationSelfTestService.check(level, "01_basic/reference_source");
            if (!checked.success()) {
                helper.fail("Reference-source self-test check failed: " + checked.lines());
                return;
            }
            boolean waitOff = level.getBlockState(origin.offset(8, 2, 2)).isAir();
            boolean passOn = level.getBlockState(origin.offset(9, 2, 2)).is(Blocks.REDSTONE_BLOCK);
            boolean failOff = level.getBlockState(origin.offset(10, 2, 2)).isAir();
            if (!waitOff || !passOn || !failOff) {
                helper.fail("PASS verdict must select exactly the green status channel");
                return;
            }
            helper.succeed();
        });
    }
}
