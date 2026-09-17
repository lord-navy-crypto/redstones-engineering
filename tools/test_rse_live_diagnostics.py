from __future__ import annotations

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/dev/redstoneengineering"
LIVE = JAVA_ROOT / "diagnostics/RseLiveDiagnostics.java"
EVENT = JAVA_ROOT / "diagnostics/RseLiveDiagnosticEvent.java"
HEALTH = JAVA_ROOT / "diagnostics/RseLiveDeviceHealth.java"
LOG_CAPTURE = JAVA_ROOT / "client/diagnostics/RseLogCapture.java"
SCREEN = JAVA_ROOT / "client/ui/RseDiagnosticsScreen.java"
MEGA_REPORTER = JAVA_ROOT / "validation/RseMegaDiagnosticReporter.java"
VALIDATION_MODULE = JAVA_ROOT / "validation/RseValidationFactoryModule.java"


class RseLiveDiagnosticsTests(unittest.TestCase):
    def test_structured_live_telemetry_core_exists_and_is_bounded(self) -> None:
        self.assertTrue(EVENT.exists(), "structured live diagnostic event record must exist")
        self.assertTrue(HEALTH.exists(), "live device health record must exist")
        self.assertTrue(LIVE.exists(), "live diagnostics registry must exist")
        source = LIVE.read_text(encoding="utf-8")
        for token in (
            "MAX_EVENTS",
            "MAX_ACTIVE_DEVICES",
            "DEVICE_STALE_TICKS",
            "recordEvent(",
            "refreshDevice(",
            "snapshotEvents(",
            "snapshotDevices(",
            "pruneStale(",
            "coalesce",
        ):
            self.assertIn(token, source)
        for forbidden in (
            "getAllLevels(",
            "getChunkSource().chunkMap",
            "players().forEach",
            "getAllEntities(",
        ):
            self.assertNotIn(forbidden, source)

    def test_live_diagnostics_exports_copyable_latest_file(self) -> None:
        self.assertTrue(LIVE.exists())
        source = LIVE.read_text(encoding="utf-8")
        for token in (
            "run/rse-diagnostics",
            "rse-live-latest.txt",
            "rse-live-latest.tmp",
            "StandardCharsets.UTF_8",
            "Files.createDirectories",
            "exportReport(",
            "exportLatest(",
            "[RSE-LIVE]",
        ):
            self.assertIn(token, source)

    def test_existing_log_capture_feeds_live_stream(self) -> None:
        source = LOG_CAPTURE.read_text(encoding="utf-8")
        self.assertIn("RseDiagnostics.record(", source)
        self.assertIn("RseLiveDiagnostics.recordLogEvent(", source)

    def test_red_cross_console_has_five_live_views(self) -> None:
        source = SCREEN.read_text(encoding="utf-8")
        for label in ("OVERVIEW", "LIVE_EVENTS", "SYSTEMS", "MEGA_FACTORY", "EXPORT"):
            self.assertIn(label, source)
        self.assertIn("RseLiveDiagnostics", source)
        self.assertIn("exportLatest", source)
        self.assertIn("Copy Report", source)

    def test_mega_reporter_publishes_structured_snapshot(self) -> None:
        self.assertTrue(MEGA_REPORTER.exists(), "Mega diagnostic reporter must be split out")
        reporter = MEGA_REPORTER.read_text(encoding="utf-8")
        self.assertIn("RseLiveDiagnostics.publishMegaSnapshot", reporter)
        for heading in (
            "ROOT BLOCKERS",
            "CASCADE",
            "CELL SUMMARY",
            "ALL 40 DUT",
            "HISTORY",
            "ACCEPTANCE",
        ):
            self.assertIn(heading, reporter)

    def test_mega_diagnose_command_routes_through_reporter(self) -> None:
        module = VALIDATION_MODULE.read_text(encoding="utf-8")
        self.assertIn('Commands.literal("diagnose")', module)
        self.assertIn("RseMegaDiagnosticReporter.diagnose(source.getLevel())", module)


if __name__ == "__main__":
    unittest.main()
