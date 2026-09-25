#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed: list[str] = []


def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")


logic = "src/main/java/dev/redstoneengineering/signal/PrecisionFilterLogic.java"
block = "src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java"
entity = "src/main/java/dev/redstoneengineering/blockentity/PrecisionFilterBlockEntity.java"
registration = "src/main/java/dev/redstoneengineering/RedstoneEngineering.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java"

require(logic,
        "MIN_RATE = 1",
        "MAX_RATE = 4",
        "public static int boundedRate",
        "public static int step(",
        "riseRate",
        "fallRate",
        "settleTicks")
require(block,
        "implements EntityBlock",
        "PrecisionFilterBlockEntity",
        "fallRate",
        "stepFallRate",
        "settleTicks",
        "trackingError",
        'IntegerProperty.create("rate", 1, 4)',
        "PrecisionFilterLogic.boundedRate",
        "PrecisionFilterLogic.step")
require(entity,
        "class PrecisionFilterBlockEntity",
        "fallRate",
        "stepFallRate",
        "loadAdditional",
        "saveAdditional",
        "PrecisionFilterLogic.boundedRate",
        "setChanged")
require(registration,
        "PrecisionFilterBlockEntity",
        "PRECISION_FILTER_BLOCK_ENTITY",
        '"precision_filter"')
require(menu,
        "BUTTON_FILTER_FALL_PREVIOUS",
        "BUTTON_FILTER_FALL_NEXT",
        "PrecisionFilterBlock.fallRate",
        "PrecisionFilterBlock.stepFallRate",
        "PrecisionFilterBlock.settleTicks")
require(screen,
        "Rise rate",
        "Fall rate",
        "Allowed rates",
        "Response law",
        "ETA model",
        "PrecisionFilterLogic.MIN_RATE",
        "PrecisionFilterLogic.MAX_RATE",
        "Settle ETA",
        "Tracking error")

block_text = (root / block).read_text(errors="ignore") if (root / block).is_file() else ""
if "IntegerProperty FALL_RATE" in block_text:
    failed.append("PrecisionFilter fall-rate must not multiply BlockState variants")
if "Math.min(15, value)" in block_text or "Math.min(15, EngineeringDeviceParameters" in block_text:
    failed.append("PrecisionFilter rise-rate authority regressed to fake 1..15 range")

entity_text = (root / entity).read_text(errors="ignore") if (root / entity).is_file() else ""
if "Math.min(15, value)" in entity_text or "fallRate >= 15" in entity_text:
    failed.append("PrecisionFilter fall-rate persistence regressed to fake 1..15 range")

logic_path = root / logic
if logic_path.is_file():
    harness = r'''
import dev.redstoneengineering.signal.PrecisionFilterLogic;

public final class PrecisionFilterHarness {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(PrecisionFilterLogic.step(4, 12, 3, 1) == 7,
                "rising path uses rise rate");
        check(PrecisionFilterLogic.step(12, 4, 3, 2) == 10,
                "falling path uses fall rate");
        check(PrecisionFilterLogic.step(14, 15, 4, 1) == 15,
                "rise clamps to target");
        check(PrecisionFilterLogic.step(1, 0, 4, 3) == 0,
                "fall clamps to target");
        check(PrecisionFilterLogic.settleTicks(4, 12, 3, 1) == 3,
                "rise ETA uses ceil(error/rate)");
        check(PrecisionFilterLogic.settleTicks(12, 4, 3, 2) == 4,
                "fall ETA uses ceil(error/rate)");
        check(PrecisionFilterLogic.settleTicks(8, 8, 3, 2) == 0,
                "settled ETA is zero");
        check(PrecisionFilterLogic.step(0, 15, 99, 1) == 4,
                "legacy over-range rise values clamp to effective MAX_RATE");
        check(PrecisionFilterLogic.step(15, 0, 1, 99) == 11,
                "legacy over-range fall values clamp to effective MAX_RATE");
        check(PrecisionFilterLogic.settleTicks(0, 15, 99, 1) == 4,
                "ETA uses effective MAX_RATE for legacy over-range settings");
        check(PrecisionFilterLogic.boundedRate(0) == PrecisionFilterLogic.MIN_RATE,
                "below-range rate clamps to MIN_RATE");
        check(PrecisionFilterLogic.boundedRate(99) == PrecisionFilterLogic.MAX_RATE,
                "above-range rate clamps to MAX_RATE");
        System.out.println("PrecisionFilterLogic semantic harness: PASS");
    }
}
'''
    try:
        with tempfile.TemporaryDirectory(prefix="rse-filter-") as td:
            td = Path(td)
            harness_path = td / "PrecisionFilterHarness.java"
            harness_path.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(harness_path)],
                text=True, capture_output=True)
            if compile_run.returncode != 0:
                failed.append("javac semantic harness failed: "
                              + (compile_run.stderr or compile_run.stdout).strip())
            else:
                execute = subprocess.run(
                    ["java", "-cp", str(td), "PrecisionFilterHarness"],
                    text=True, capture_output=True)
                if execute.returncode != 0:
                    failed.append("semantic harness failed: "
                                  + (execute.stderr or execute.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for PrecisionFilter semantic harness")

if failed:
    print("RSE precision-filter engineering-depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE precision-filter engineering-depth verification: PASS")
print(" asymmetric rise/fall slew semantics: PASS")
print(" settle ETA/tracking evidence: PASS")
print(" persistent fall-rate without BlockState multiplication: PASS")
print(" HMI independent rise/fall authority + exact 1..4 model equations: PASS")
print(" legacy over-range persisted rates clamp to the effective 1..4 contract: PASS")
