import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class EngineeringWorkbenchV2Tests(unittest.TestCase):
    def setUp(self):
        self.screen = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java").read_text(encoding="utf-8")
        self.catalog = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/EngineeringWorkbenchCatalog.java").read_text(encoding="utf-8")
        self.universal_screen = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java").read_text(encoding="utf-8")
        self.universal_menu = (ROOT / "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java").read_text(encoding="utf-8")

    def test_every_engineering_screen_gets_model_page(self):
        for token in [
            "initialPolicy.pageLabel()",
            "setWorkbenchPage()",
            "renderWorkbenchPage(graphics)",
            "EngineeringWorkbenchCatalog.describe(menu)",
            "Formula / relation",
            "display history only",
            "safeWrappedText",
        ]:
            self.assertIn(token, self.screen)

    def test_catalog_covers_major_engineering_families(self):
        for token in [
            "PidControllerMenu",
            "SignalConditionerMenu",
            "SignalProcessorMenu",
            "QuartzTimingMenu",
            "PneumaticSystemMenu",
            "MagneticSystemMenu",
            "AmethystSystemMenu",
            "OpticalSystemMenu",
            "DigitalCommunicationMenu",
            "RadioLinkMenu",
            "ReliabilitySystemMenu",
            "MediaConversionMenu",
            "CopperCircuitMeterMenu",
            "UniversalFieldDeviceMenu",
        ]:
            self.assertIn(token, self.catalog)
        self.assertIn("server remains authoritative", self.catalog.lower())

    def test_universal_hmi_supports_exact_bounded_targets(self):
        for token in [
            "BUTTON_DIRECT_PRIMARY_BASE",
            "BUTTON_DIRECT_SECONDARY_BASE",
            "applyDirectTarget(boolean primary, int target)",
            "primaryEditableProperty",
            "secondaryEditableProperty",
            "property.getPossibleValues().contains(target)",
            "adjustPrimary(target > current ? 1 : -1)",
            "adjustSecondary(target > current ? 1 : -1)",
        ]:
            self.assertIn(token, self.universal_menu)

    def test_direct_targets_reuse_existing_server_actions(self):
        self.assertNotIn("Minecraft.getInstance().level.setBlock", self.universal_screen)
        self.assertNotIn("RuntimeIntStore", self.universal_screen)
        for token in [
            "EditBox",
            "applyDirectTarget(true)",
            "applyDirectTarget(false)",
            "BUTTON_DIRECT_PRIMARY_BASE",
            "BUTTON_DIRECT_SECONDARY_BASE",
            "Min ",
            "Max ",
        ]:
            self.assertIn(token, self.universal_screen)

    def test_universal_history_is_visual_observation_not_simulation(self):
        for token in [
            "UI OBSERVATION HISTORY • DISPLAY ONLY",
            "EngineeringPlot.analogFrame",
            "EngineeringPlot.analogTrace",
            "recordPortHistory",
            "INVALID_SAMPLE",
            "source data stay server-owned",
        ]:
            self.assertIn(token, self.universal_screen)

    def test_parameter_metadata_covers_core_configurable_devices(self):
        for token in [
            "AnalogComparatorBlock.HYSTERESIS",
            "SignalAmplifierBlock.GAIN_MODE",
            "WatchdogBlock.TIMEOUT",
            "RedstoneReferenceSourceBlock.POWER",
            "MagneticFieldSensorBlock.RADIUS_MODE",
            "MagneticFieldSensorBlock.SAMPLE_MODE",
            "LapisPrecisionRangeSensorBlock.RANGE_INDEX",
            "PwmControllerBlock.PERIOD_MODE",
            "SingleRelayBlock.PICKUP_MODE",
        ]:
            self.assertIn(token, self.universal_menu)


    def test_specialized_hmis_get_shared_bounded_parameter_catalog(self):
        for token in [
            "record ParameterSpec(",
            "PidControllerMenu.BUTTON_TUNING_PREVIOUS",
            "SignalConditionerMenu.BUTTON_PARAM_DECREASE",
            "SignalProcessorMenu.BUTTON_FILTER_FALL_PREVIOUS",
            "SignalProcessorMenu.BUTTON_THRESHOLD_PREVIOUS",
            "RangeSensorMenu.BUTTON_RESPONSE_PREVIOUS",
            "PneumaticSystemMenu.KIND_REGULATOR",
            "AmethystSystemMenu.KIND_TUNED",
            "RadioLinkMenu.BUTTON_CHANNEL_PREVIOUS",
            "DigitalCommunicationMenu.KIND_REGENERATOR",
            "OpticalSystemMenu.KIND_ATTENUATOR",
            "MagneticSystemMenu.KIND_COIL",
        ]:
            self.assertIn(token, self.catalog)

    def test_model_page_has_exact_target_editor_without_world_mutation(self):
        for token in [
            "addWorkbenchControls",
            "workbenchTarget",
            "applyWorkbenchTargetValue",
            "spec.incrementButton()",
            "spec.decrementButton()",
            "Min ",
            "Max ",
            "existing server-authoritative step actions",
        ]:
            self.assertIn(token, self.screen)
        self.assertNotIn("setBlock(", self.screen)
        self.assertNotIn("RuntimeIntStore", self.screen)



    def test_workbench_has_ordered_sweep_and_fraction_presets(self):
        for token in [
            "WORKBENCH_SWEEP_DWELL_TICKS",
            "toggleWorkbenchSweep",
            "tickWorkbenchSweep",
            "applyWorkbenchFraction(0.25)",
            "applyWorkbenchFraction(0.50)",
            "applyWorkbenchFraction(0.75)",
            "Sweep ↑",
            "Measure a real parameter-response sweep",
            "graphics.fill(barX, barY",
        ]:
            self.assertIn(token, self.screen)

    def test_minecraft_first_ui_policy_has_three_tiers(self):
        for token in [
            "enum UiTier { BLOCK, DEVICE, LAB }",
            "record UiPolicy(",
            "universalPolicy",
            "fieldPolicy",
            'block("INFO"',
            'device("MODEL"',
            'lab("LAB"',
            "KIND_REDSTONE_CABLE",
            "KIND_PNEUMATIC_PIPE",
            "KIND_SERVO_ACTUATOR",
            "KIND_AMETHYST_TUNED",
        ]:
            self.assertIn(token, self.catalog)

    def test_experiment_controls_are_semantically_gated(self):
        for token in [
            "fractionPresets",
            "sweepMeaningful",
            "spec.fractionPresets()",
            "policy.experimental()",
            "spec.sweepMeaningful()",
            "Categorical modes/channels never receive this control",
            "UiTier.BLOCK",
            "No sweep, no desktop-style experiment workflow",
        ]:
            self.assertIn(token, self.screen + self.catalog)

    def test_only_selected_parameters_declare_sweep_semantics(self):
        for token in [
            'experimentSpec("Pressure setpoint"',
            'sweepSpec("Natural frequency"',
            'sweepSpec("Target frequency"',
            'experimentSpec("Logic threshold"',
            'experimentSpec("Attenuation"',
            'sweepSpec("Coil turns index"',
        ]:
            self.assertIn(token, self.catalog)
        # Categorical parameters stay plain specs rather than desktop-style numeric experiments.
        for token in [
            'spec("Radio channel"',
            'spec("Transfer mode"',
            'spec("Detection mode"',
            'spec("Response profile"',
        ]:
            self.assertIn(token, self.catalog)

    def test_universal_devices_also_participate_in_model_parameter_workbench(self):
        for token in [
            "universal.editPrimaryAvailable()",
            "universal.editSecondaryAvailable()",
            "UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS",
            "UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_PREVIOUS",
            "universalPrimaryLabel",
            "universalSecondaryLabel",
        ]:
            self.assertIn(token, self.catalog)



if __name__ == "__main__":
    unittest.main()
