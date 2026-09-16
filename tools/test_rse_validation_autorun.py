from __future__ import annotations

from pathlib import Path
import unittest

from tools import rse_validation_factory as factory

ROOT = Path(__file__).resolve().parents[1]


class ValidationSelfTestAutorunTests(unittest.TestCase):
    def test_every_selftest_has_physical_retest_button(self) -> None:
        for test_id, selftest in factory.SELFTESTS.items():
            with self.subTest(test_id=test_id):
                self.assertTrue(hasattr(selftest, "retest_button"), f"{test_id} missing retest_button metadata")
                _size, placements = selftest.builder()
                by_position = dict(placements)
                button = by_position.get(selftest.retest_button)
                self.assertIsNotNone(button, f"{test_id} missing physical RETEST button")
                block_id, properties = factory._normalize_block_spec(button)
                self.assertEqual(block_id, "minecraft:stone_button")
                self.assertEqual(dict(properties).get("powered"), "false")

    def test_server_tick_owns_automatic_selftest_progression(self) -> None:
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java").read_text(encoding="utf-8")
        saved = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestSavedData.java").read_text(encoding="utf-8")

        self.assertIn("ServerTickEvent.Post", module)
        self.assertIn("RseValidationSelfTestService.tickAll(", module)
        self.assertIn("tickAll(", service)
        self.assertIn("tickAutomatic(", service)
        self.assertIn("BlockStateProperties.POWERED", service)
        self.assertIn("resetForRetest(", saved)
        self.assertIn("retestPressed", saved)

    def test_manual_check_is_debug_fallback_not_required_by_place_message(self) -> None:
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java").read_text(encoding="utf-8")
        self.assertIn("Automatic self-test armed", service)
        self.assertNotIn("Run /rsevalidation selftest check", service)

    def test_one_command_can_place_complete_automatic_selftest_yard(self) -> None:
        module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("place-all")', module)
        self.assertIn("RseValidationSelfTestService.placeAll(", module)
        self.assertIn("placeAll(", service)
        self.assertIn("GRID_COLUMNS", service)
        self.assertIn("GRID_SPACING_X", service)
        self.assertIn("GRID_SPACING_Z", service)


if __name__ == "__main__":
    unittest.main()
