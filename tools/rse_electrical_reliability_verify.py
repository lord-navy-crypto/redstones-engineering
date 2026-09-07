from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
assessment = (ROOT / "src/main/java/dev/redstoneengineering/diagnostics/ElectricalReliabilityAssessment.java").read_text()
menu = (ROOT / "src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java").read_text()
screen = (ROOT / "src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java").read_text()
tests = (ROOT / "src/main/java/dev/redstoneengineering/gametest/RseElectricalReliabilityGameTests.java").read_text()
registration = (ROOT / "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java").read_text()

checks = {
    "server evidence projection": all(x in assessment for x in ["SystemEventTimeline.within", "ELECTRICAL_TRIP", "ELECTRICAL_READY", "electricalDowntimeTicks", "repeatTripCount"]),
    "no premature formal reliability claim": "MTBF/MTTR" in assessment and "durable operating exposure" in assessment,
    "synchronized operations readback": all(x in menu for x in ["ElectricalReliabilityAssessment.inspect", "electricalTripCount", "electricalDowntimeTicks", "electricalLastRecoveryDurationTicks"]),
    "operator reliability evidence UI": all(x in screen for x in ["Electrical trips / recovered", "Electrical downtime", "Protection status", "MTBF/MTTR withheld"]),
    "runtime chronology contracts": all(x in tests for x in ["electricalReliabilityPairsTripRecoveryAndDowntime", "electricalReliabilityTracksRepeatAndOpenProtection", "electricalDowntimeTicks() != 9L"]),
    "GameTest registration": "event.register(RseElectricalReliabilityGameTests.class);" in registration,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit("Electrical reliability verification failed: " + ", ".join(failed))
print("Electrical reliability evidence verification passed.")
