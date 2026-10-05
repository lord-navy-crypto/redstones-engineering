from __future__ import annotations

from pathlib import Path
import unittest

from tools import rse_validation_factory as factory

ROOT = Path(__file__).resolve().parents[1]


class ValidationSystemBenchTests(unittest.TestCase):
    EXPECTED = {
        "03_systems/interlock_trip_restore",
        "03_systems/fault_injector_bias",
        "03_systems/alarm_latch_ack_reset",
    }

    def test_system_level_catalog_exists(self) -> None:
        self.assertTrue(self.EXPECTED.issubset(factory.SELFTESTS))

    def test_interlock_bench_has_three_physical_permissives_and_direct_observer(self) -> None:
        test = factory.SELFTESTS["03_systems/interlock_trip_restore"]
        _size, placements = test.builder()
        by_pos = dict(placements)
        block_id, props = factory._normalize_block_spec(by_pos[(5, 1, 4)])
        self.assertEqual(block_id, "redstoneengineering:safety_interlock")
        self.assertEqual(dict(props)["input_facing"], "west")
        self.assertEqual(dict(props)["facing"], "east")
        self.assertEqual(factory._block_name(by_pos[(4, 1, 4)]), "redstoneengineering:redstone_reference_source")
        self.assertEqual(factory._block_name(by_pos[(5, 1, 3)]), "redstoneengineering:redstone_reference_source")
        self.assertEqual(factory._block_name(by_pos[(5, 1, 5)]), "redstoneengineering:redstone_reference_source")
        self.assertEqual(factory._block_name(by_pos[(6, 1, 4)]), "redstoneengineering:analog_indicator")

    def test_fault_injector_bench_arms_from_right_side_and_observes_faulted_output(self) -> None:
        test = factory.SELFTESTS["03_systems/fault_injector_bias"]
        _size, placements = test.builder()
        by_pos = dict(placements)
        block_id, props = factory._normalize_block_spec(by_pos[(4, 1, 4)])
        self.assertEqual(block_id, "redstoneengineering:fault_injector")
        self.assertEqual(dict(props)["input_facing"], "west")
        self.assertEqual(dict(props)["facing"], "east")
        self.assertEqual(dict(props)["mode"], "2")
        self.assertEqual(factory._block_name(by_pos[(3, 1, 4)]), "redstoneengineering:redstone_reference_source")
        # EAST-facing DUT has RIGHT input on SOUTH, so the arm source sits south and points NORTH.
        arm_id, arm_props = factory._normalize_block_spec(by_pos[(4, 1, 5)])
        self.assertEqual(arm_id, "redstoneengineering:redstone_reference_source")
        self.assertEqual(dict(arm_props)["facing"], "north")
        self.assertEqual(factory._block_name(by_pos[(5, 1, 4)]), "redstoneengineering:analog_indicator")

    def test_alarm_bench_exposes_condition_ack_reset_and_direct_alarm_output(self) -> None:
        test = factory.SELFTESTS["03_systems/alarm_latch_ack_reset"]
        _size, placements = test.builder()
        by_pos = dict(placements)
        alarm_id, alarm_props = factory._normalize_block_spec(by_pos[(5, 1, 4)])
        self.assertEqual(alarm_id, "redstoneengineering:alarm_processor")
        self.assertEqual(dict(alarm_props)["input_facing"], "west")
        self.assertEqual(dict(alarm_props)["facing"], "east")
        self.assertEqual(dict(alarm_props)["severity"], "2")
        self.assertEqual(factory._block_name(by_pos[(4, 1, 4)]), "redstoneengineering:redstone_reference_source")
        ack_id, ack_props = factory._normalize_block_spec(by_pos[(5, 1, 3)])
        reset_id, reset_props = factory._normalize_block_spec(by_pos[(5, 1, 5)])
        self.assertEqual(ack_id, "redstoneengineering:redstone_reference_source")
        self.assertEqual(reset_id, "redstoneengineering:redstone_reference_source")
        self.assertEqual(dict(ack_props)["facing"], "south")
        self.assertEqual(dict(reset_props)["facing"], "north")
        self.assertEqual(factory._block_name(by_pos[(6, 1, 4)]), "redstoneengineering:analog_indicator")

    def test_system_tests_are_part_of_automatic_yard(self) -> None:
        yard = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestYardService.java").read_text(encoding="utf-8")
        for test_id in self.EXPECTED:
            self.assertIn(f'"{test_id}"', yard)


if __name__ == "__main__":
    unittest.main()
