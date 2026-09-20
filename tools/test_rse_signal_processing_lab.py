import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SignalProcessingLabContractTests(unittest.TestCase):
    def setUp(self):
        self.service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseSignalProcessingLabService.java").read_text(encoding="utf-8")
        self.module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.catalog = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/EngineeringWorkbenchCatalog.java").read_text(encoding="utf-8")
        self.universal_menu = (ROOT / "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java").read_text(encoding="utf-8")
        self.universal_screen = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java").read_text(encoding="utf-8")
        self.conversion_screen = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java").read_text(encoding="utf-8")

    def test_four_unit_cells_and_one_integrated_chain_exist(self):
        for token in [
            "U1_FILTER",
            "U2_OSC",
            "U3_SAMPLER",
            "U4_QUANTIZER",
            "C_FILTER",
            "C_CLOCK",
            "C_SAMPLER",
            "C_QUANTIZER",
            "4 unit cells + integrated four-device signal chain",
        ]:
            self.assertIn(token, self.service)

    def test_exact_four_teaching_devices_are_used_in_integrated_chain(self):
        for token in [
            "RedstoneEngineering.LAPIS_LOW_PASS_FILTER",
            "RedstoneEngineering.QUARTZ_LAB_OSCILLATOR",
            "RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER",
            "RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER",
        ]:
            self.assertIn(token, self.service)
        self.assertNotIn("PID_CONTROLLER", self.service)
        self.assertNotIn("SERVO_ACTUATOR", self.service)
        self.assertNotIn("AMETHYST_", self.service)
        self.assertNotIn("PNEUMATIC_", self.service)
        self.assertNotIn("QUARTZ_CLOCK_DIVIDER", self.service)

    def test_unit_lpf_requires_real_dynamic_step_response(self):
        for token in [
            "STIMULUS | U1 low-pass source step 20 -> 80",
            "s.unitLpfDynamicSeen",
            "y[k]=0.75y[k-1]+0.25x[k]",
            "20->80 step",
        ]:
            self.assertIn(token, self.service)

    def test_clock_validation_uses_real_edges_and_period(self):
        for token in [
            "unitOscRisingEdges",
            "unitOscPeriodWitness",
            "tick - s.unitOscLastRisingTick == 8L",
            "lastHalfInterval() != 4",
            "lastJitterOffset() != 0",
        ]:
            self.assertIn(token, self.service)

    def test_sampler_validation_is_rising_edge_capture_based(self):
        for token in [
            "QuartzTriggeredLapisSamplerBlock.acceptedCaptures",
            "QuartzTriggeredLapisSamplerBlock.heldValue",
            "QuartzTriggeredLapisSamplerBlock.heldQuality",
            "rising-edge captures=",
            "combinedPostStepCaptureSeen",
        ]:
            self.assertIn(token, self.service)

    def test_quantizer_validation_matches_actual_transfer_math(self):
        for token in [
            "CoreMediaDiagnostics.redstoneFromLapis(held)",
            "CoreMediaDiagnostics.lapisReconstructedFromRedstone(power)",
            "CoreMediaDiagnostics.quantizationError(75)",
            "q=round(15x/100)",
            "x=75 -> q=11/15",
        ]:
            self.assertIn(token, self.service)

    def test_integrated_chain_has_stage_and_end_to_end_validation(self):
        self.assertIn("private static final int STAGE_COUNT = 9;", self.service)
        for stage in range(1, 10):
            self.assertIn(f"case {stage} ->", self.service)
        self.assertIn("evaluateEndToEnd", self.service)
        self.assertIn("all four real devices contributed", self.service)

    def test_signal_lab_is_registered_and_ticks(self):
        for token in [
            'Commands.literal("signal")',
            "RseSignalProcessingLabService.tick(event.getServer())",
            "RseSignalProcessingLabService.place(player, origin)",
            "RseSignalProcessingLabService.status(source.getPlayerOrException())",
            "RseSignalProcessingLabService.stage(source.getPlayerOrException(), number)",
            "RseSignalProcessingLabService.retest(source.getPlayerOrException())",
            "IntegerArgumentType.integer(1, 9)",
        ]:
            self.assertIn(token, self.module)

    def test_showcase_story_and_bay_zoning_are_explicit(self):
        for token in [
            "SHOWCASE MODE",
            "STORY: SMOOTH → TIME → CAPTURE → ENCODE",
            "SHOWCASE STORY | SMOOTH -> TIME -> CAPTURE -> ENCODE",
            "CHAIN MATH | y+=alpha(x-y) | Quartz rising edge -> hold | q=round(15x/100)",
            "Blocks.BLUE_CONCRETE",
            "Blocks.WHITE_CONCRETE",
            "Blocks.CYAN_CONCRETE",
            "Blocks.RED_CONCRETE",
        ]:
            self.assertIn(token, self.service)

    def test_three_dynamic_showcase_blocks_have_real_lab_profiles(self):
        for token in [
            "How strongly does alpha smooth a real Lapis step input?",
            "How do period and jitter settings change a real server clock?",
            "Does the sample-and-hold change only on genuine Quartz rising edges?",
            "CONFIG PENDING • edited timing will latch on the next genuine edge",
            "rejected acquisition preserves the previous trustworthy sample",
        ]:
            self.assertIn(token, self.catalog)

    def test_universal_snapshot_exposes_showcase_runtime_evidence(self):
        for token in [
            "LapisLowPassFilterBlock.FilterState filter",
            "filter.output()",
            "filter.quality().ordinal()",
            "LapisLowPassFilterBlock.retainedHistory",
            "QuartzTriggeredLapisSamplerBlock.heldQuality",
        ]:
            self.assertIn(token, self.universal_menu)
        for token in [
            'CONFIG_LAPIS_LOW_PASS -> "Alpha"',
            'CONFIG_QUARTZ_LAB_OSCILLATOR -> "Jitter"',
            "opening this page never creates a Quartz edge",
        ]:
            self.assertIn(token, self.universal_screen)

    def test_quantizer_showcase_explains_information_loss(self):
        for token in [
            "q = round(15x/100)",
            "x_hat = 100q/15",
            "Quantization error",
            "Quantization intentionally compresses Lapis precision",
        ]:
            self.assertIn(token, self.conversion_screen)

    def test_signal_lab_publishes_copy_ready_feedback(self):
        for token in [
            "RseLiveDiagnostics.publishValidationRun(",
            "feedbackLines(level, s)",
            "List.copyOf(s.runLog)",
            "SIGNAL LAB ",
            "OVERALL ",
        ]:
            self.assertIn(token, self.service)


if __name__ == "__main__":
    unittest.main()
