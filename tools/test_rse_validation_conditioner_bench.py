from __future__ import annotations

from pathlib import Path
import unittest

from tools import rse_validation_factory as factory

ROOT = Path(__file__).resolve().parents[1]


class ConditionerBenchRegressionTests(unittest.TestCase):
    def test_gain_bench_isolates_conditioner_with_direct_indicator(self) -> None:
        test = factory.SELFTESTS["01_basic/signal_conditioner_gain"]
        _size, placements = test.builder()
        by_position = {position: block for position, block in placements}

        self.assertEqual(
            factory._block_name(by_position[(3, 1, 4)]),
            "redstoneengineering:redstone_reference_source",
        )

        conditioner = by_position[(4, 1, 4)]
        conditioner_id, conditioner_props = factory._normalize_block_spec(conditioner)
        self.assertEqual(conditioner_id, "redstoneengineering:signal_conditioner")
        self.assertEqual(dict(conditioner_props)["input_facing"], "west")
        self.assertEqual(dict(conditioner_props)["facing"], "east")

        self.assertEqual(
            factory._block_name(by_position[(5, 1, 4)]),
            "redstoneengineering:analog_indicator",
            "gain self-test should observe the conditioner directly, without an unrelated redstone-wire hop",
        )
        self.assertEqual(test.observe, (5, 1, 4))

        indicator_id, indicator_props = factory._normalize_block_spec(by_position[(5, 1, 4)])
        self.assertEqual(indicator_id, "redstoneengineering:analog_indicator")
        self.assertEqual(dict(indicator_props)["facing"], "east")  # BACK input = west

    def test_saturation_evaluator_preserves_expected_quality_downstream(self) -> None:
        service = (
            ROOT
            / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "private static Evaluation evaluateIndicator(ServerLevel level, BlockPos pos, int expected, PortQuality expectedQuality)",
            service,
        )
        self.assertIn("observation.quality() != expectedQuality", service)
        self.assertIn(
            "evaluateIndicator(level, indicatorPos, expected, expectedQuality)",
            service,
            "conditioner saturation must allow SATURATED to propagate into the direct indicator",
        )


if __name__ == "__main__":
    unittest.main()
