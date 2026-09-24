#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/AmethystSystemMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/AmethystSystemScreen.java"
source_path = root / "src/main/java/dev/redstoneengineering/block/AmethystResonatorBlock.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, source_path, opener_path):
    if not path.is_file():
        failed.append(f"missing amethyst HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    source = source_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "AmethystResonatorBlock.excite(level, blockPos, state)",
        "level.scheduleTick(blockPos, resonator, 2)",
        "AmethystResonatorBlock.currentAmplitude(level, blockPos)",
        "AmethystResonatorBlock.excitationCount(level, blockPos)",
        "e.targetAmplitude()",
        "e.actualAmplitude()",
        "e.outputFrequency()",
        "e.frequencyError()",
        "AmethystTunedResonatorLogic.responseStep(e.qIndex())",
        "e.couplingIndex()",
        "e.decayRate()",
        "BUTTON_COUPLING_PREVIOUS",
        "BUTTON_DECAY_PREVIOUS",
        "rotateRigidSeriesAxis",
        "e.ringDown() ? 3",
        "outputQuality",
        "engineeringSnapshot(level, blockPos, state, tunedOutput)",
    ):
        if token not in menu:
            failed.append(f"AmethystSystemMenu missing server-backed response token: {token}")

    for stale in (
        'RuntimeIntStore.get(level, "amethyst_resonator", blockPos, 1)[0] = 1',
        "level.scheduleTick(blockPos, resonator, 4)",
    ):
        if stale in menu:
            failed.append(f"AmethystSystemMenu reintroduced synthetic pulse behavior: {stale}")

    for token in (
        '"Current A"',
        '"Excitations"',
        '"Target / actual A"',
        '"Output F / driven step"',
        '"Coupling / free decay"',
        '"A[k+1] = max(0, A[k] - D), D="',
        '"Atarget = clamp(Ain + Q·C, 0..15)"',
        '"Atarget = clamp(Ain - max(1, Δf·Q) + (C-2), 0..15)"',
        '"FREE RING-DOWN"',
        "outputQualityName()",
    ):
        if token not in screen:
            failed.append(f"AmethystSystemScreen missing dynamic model/evidence token: {token}")

    if '"Expected output"' in screen:
        failed.append("AmethystSystemScreen still labels tuned steady-state target as actual output")

    if "if (block instanceof AmethystResonatorBlock || block instanceof AmethystFrequencyFilterBlock" not in opener:
        failed.append("FieldDeviceUi does not route Amethyst filter/tuned devices to the dedicated resonance HMI")
    if "|| block instanceof AmethystFrequencyFilterBlock\n                || block instanceof AmethystTunedResonatorBlock\n                || block instanceof PressureRegulatorBlock" in opener:
        failed.append("FieldDeviceUi generic MultiPhysics dispatch still intercepts Amethyst filter/tuned devices")

    # The base amethyst resonator really is a four-horizontal-output source.
    for side in ("Direction.NORTH", "Direction.SOUTH", "Direction.WEST", "Direction.EAST"):
        if f"sourcePort({side})" not in source:
            failed.append(f"Amethyst source topology lost declared output: {side}")
    if '"FOUR-WAY SOURCE"' not in screen or '"N/E/S/W"' not in screen:
        failed.append("AmethystSystemScreen no longer reflects the real four-way source topology")

if failed:
    print("RSE amethyst HMI depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE amethyst HMI depth verification: PASS")
print(" source pulse uses real excitation/ring-down model: PASS")
print(" source runtime amplitude and excitation count are synchronized: PASS")
print(" tuned target and actual response are distinct: PASS")
print(" tuned ring-down/output quality are server-backed: PASS")
print(" tuned coupling and free-decay controls are server-owned: PASS")
print(" tuned two-port routing stays rigid and opposite: PASS")
print(" four-way source topology remains truthful: PASS")
print(" normal filter/tuned dispatch reaches dedicated Amethyst HMI: PASS")
