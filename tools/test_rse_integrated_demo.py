import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class IntegratedDemoPneumaticCellTests(unittest.TestCase):
    def setUp(self):
        self.service = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseIntegratedDemoService.java").read_text(encoding="utf-8")
        self.module = (ROOT / "src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java").read_text(encoding="utf-8")
        self.renderer = (ROOT / "src/main/java/dev/redstoneengineering/client/MechatronicsGeoModel.java").read_text(encoding="utf-8")
        self.geo_path = ROOT / "src/main/resources/assets/redstoneengineering/geo/block/pneumatic_cylinder.geo.json"

    def test_demo_exposes_fifteen_stages(self):
        self.assertIn("private static final int STAGE_COUNT = 15;", self.service)
        self.assertIn("IntegerArgumentType.integer(1, 15)", self.module)
        for stage in range(11, 16):
            self.assertIn(f"case {stage} -> stage{stage}(level, s);", self.service)
            self.assertIn(f"private static StageResult stage{stage}", self.service)

    def test_pneumatic_cell_contains_real_instrument_chain(self):
        required = [
            "PNEU_COMMAND_SOURCE",
            "PNEU_COMPRESSOR",
            "PNEU_PIPE_UP",
            "PNEU_RESERVOIR",
            "PNEU_REGULATOR",
            "PNEU_FLOW_METER",
            "PNEU_PRESSURE_RX",
            "PNEU_PRESSURE_DISPLAY",
            "PNEU_CYLINDER",
            "PNEU_POSITION_DISPLAY",
            "Blocks.REDSTONE_BLOCK.defaultBlockState()",
            "RedstoneEngineering.AIR_COMPRESSOR",
            "RedstoneEngineering.AIR_RESERVOIR",
            "RedstoneEngineering.PRESSURE_REGULATOR",
            "RedstoneEngineering.PNEUMATIC_FLOW_METER",
            "RedstoneEngineering.PNEUMATIC_RECEIVER",
            "RedstoneEngineering.PNEUMATIC_CYLINDER",
            "PneumaticNetwork.recompute(level, origin.offset(PNEU_COMPRESSOR))",
        ]
        for token in required:
            self.assertIn(token, self.service)

    def test_directional_ports_match_physical_layout(self):
        self.assertIn(
            ".setValue(DirectionalSignalBlock.FACING, Direction.SOUTH)\n"
            "                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.NORTH)",
            self.service,
        )
        self.assertIn(
            "PNEU_CYLINDER, RedstoneEngineering.PNEUMATIC_CYLINDER.get().defaultBlockState()\n"
            "                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)\n"
            "                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)",
            self.service,
        )
        self.assertIn(
            "PNEU_POSITION_DISPLAY, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()\n"
            "                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)",
            self.service,
        )

    def test_stages_require_dynamic_evidence_not_presence_only(self):
        required = [
            "AirCompressorBlock.startCount(level, pos)",
            "s.reservoirCharged",
            "measurement.sampleCount() < 2",
            "s.pneumaticReceiverSeen",
            "s.cylinderMoved",
            "PneumaticCylinderBlock.travel(level, pos)",
            "PneumaticNetwork.actuatorPathEvidence(level, pos)",
        ]
        for token in required:
            self.assertIn(token, self.service)

    def test_cylinder_visual_has_explicit_translation_and_head(self):
        self.assertIn("state.position01() * 10.0", self.renderer)
        geo = json.loads(self.geo_path.read_text(encoding="utf-8"))
        bones = geo["minecraft:geometry"][0]["bones"]
        names = {bone["name"] for bone in bones}
        self.assertIn("rod", names)
        self.assertIn("piston_head", names)
        head = next(b for b in bones if b["name"] == "piston_head")
        self.assertEqual(head.get("parent"), "rod")


if __name__ == "__main__":
    unittest.main()
