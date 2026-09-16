package dev.redstoneengineering.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Places the complete automatic validation series as a walkable 4x4 yard. */
public final class RseValidationSelfTestYardService {
    private RseValidationSelfTestYardService() {}

    public static final int GRID_COLUMNS = 4;
    public static final int GRID_SPACING_X = 16;
    public static final int GRID_SPACING_Z = 12;

    private static final List<String> TEST_ORDER = List.of(
            "01_basic/reference_source",
            "01_basic/signal_probe",
            "01_basic/signal_analyzer_tap",
            "01_basic/signal_analyzer_inline",
            "01_basic/analog_indicator",
            "01_basic/signal_conditioner_gain",
            "01_basic/directional_io",
            "01_basic/instrument_bus",
            "02_signal/conditioner_saturation",
            "02_signal/precision_filter",
            "02_signal/sample_hold",
            "02_signal/edge_detector",
            "02_signal/pulse_shaper",
            "02_signal/pwm_control",
            "02_signal/noise_vs_filter",
            "02_signal/quantizer_scaler"
    );

    public static RseValidationFactoryService.Result placeAll(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) {
            return RseValidationFactoryService.Result.fail("Self-test yard failed: level/origin missing");
        }
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Automatic self-test yard currently requires the overworld.");
        }

        // Preflight every template before mutating the world so a missing asset cannot leave a half yard.
        for (String id : TEST_ORDER) {
            var resource = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    dev.redstoneengineering.RedstoneEngineering.MOD_ID,
                    "validation/selftest/" + id
            );
            if (level.getStructureManager().get(resource).isEmpty()) {
                return RseValidationFactoryService.Result.fail("Self-test yard preflight missing structure: " + resource);
            }
        }

        ArrayList<Component> detail = new ArrayList<>();
        int placed = 0;
        for (int index = 0; index < TEST_ORDER.size(); index++) {
            String id = TEST_ORDER.get(index);
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            BlockPos benchOrigin = origin.offset(
                    column * GRID_SPACING_X,
                    0,
                    row * GRID_SPACING_Z
            );
            RseValidationFactoryService.Result result = RseValidationSelfTestService.place(level, benchOrigin, id);
            if (!result.success()) {
                return RseValidationFactoryService.Result.fail(
                        "Self-test yard stopped after " + placed + "/" + TEST_ORDER.size() + " benches.",
                        "Failed: " + id,
                        "Origin: " + benchOrigin.toShortString()
                );
            }
            placed++;
        }

        detail.add(Component.literal("Placed automatic RSE self-test yard: " + placed + " benches."));
        detail.add(Component.literal("Layout: 4 columns x 4 rows; every bench starts YELLOW WAIT and resolves automatically."));
        detail.add(Component.literal("GREEN=PASS, RED=FAIL. Press a bench's stone RETEST button to rerun only that test."));
        detail.add(Component.literal("Yard origin: " + origin.toShortString()));
        return new RseValidationFactoryService.Result(true, detail);
    }
}
