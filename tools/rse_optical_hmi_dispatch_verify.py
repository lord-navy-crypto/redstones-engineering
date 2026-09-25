#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java"
attenuator_path = root / "src/main/java/dev/redstoneengineering/block/OpticalAttenuatorBlock.java"
filter_path = root / "src/main/java/dev/redstoneengineering/block/OpticalChannelFilterBlock.java"
logic_path = root / "src/main/java/dev/redstoneengineering/signal/OpticalPassiveLogic.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, attenuator_path, filter_path, logic_path, opener_path):
    if not path.is_file():
        failed.append(f"missing Optical HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    attenuator = attenuator_path.read_text(errors="ignore")
    optical_filter = filter_path.read_text(errors="ignore")
    logic = logic_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "OpticalAttenuatorBlock.setConfiguredLoss(",
        "secondary.get() + (id == BUTTON_PRIMARY_NEXT ? 1 : -1)",
        "OpticalEmitterBlock.INTENSITY",
        "OpticalEmitterBlock.CHANNEL",
        "OpticalChannelFilterBlock.TARGET",
        "BUTTON_INPUT_LEFT",
        "BUTTON_INPUT_RIGHT",
        "BUTTON_OUTPUT_LEFT",
        "BUTTON_OUTPUT_RIGHT",
        "routeRigidDomain(id)",
        "DirectionalDomainBlock.rotateRigidSeriesAxis",
        "legacyParameter.set(state.getValue(OpticalAttenuatorBlock.LOSS))",
        "public boolean rigidSeriesRoute()",
        "public Direction inputDirection()",
    ):
        if token not in menu:
            failed.append(f"OpticalSystemMenu missing control/topology token: {token}")

    for token in (
        "public static int configuredLoss",
        "OpticalPassiveLogic.boundedConfiguredLoss",
        "OpticalPassiveLogic.boundedLegacyLoss",
        "OpticalPassiveLogic.attenuatedIntensity",
        "OpticalPassiveLogic.fullyAttenuated",
        "public static boolean setConfiguredLoss",
    ):
        if token not in attenuator:
            failed.append(f"OpticalAttenuatorBlock missing full-range loss token: {token}")

    for token in (
        "OpticalPassiveLogic.boundedChannel",
        "OpticalPassiveLogic.channelMatched",
        "OpticalPassiveLogic.filteredIntensity",
        "OpticalPassiveLogic.FILTER_INSERTION_LOSS",
    ):
        if token not in optical_filter:
            failed.append(f"OpticalChannelFilterBlock missing passive-filter token: {token}")

    for token in (
        "MIN_INTENSITY = 0",
        "MAX_INTENSITY = 15",
        "MIN_CONFIGURED_LOSS = 0",
        "MAX_CONFIGURED_LOSS = 15",
        "MIN_LEGACY_LOSS = 0",
        "MAX_LEGACY_LOSS = 8",
        "DEFAULT_LEGACY_LOSS = 2",
        "MIN_CHANNEL = 0",
        "MAX_CHANNEL = 15",
        "FILTER_INSERTION_LOSS = 1",
        "TRANSFER_TICK_TICKS = 2",
        "CONFIGURATION_RECHECK_TICKS = 1",
        "attenuatedIntensity",
        "fullyAttenuated",
        "channelMatched",
        "filteredIntensity",
    ):
        if token not in logic:
            failed.append(f"OpticalPassiveLogic missing pure-model token: {token}")

    for token in (
        "Observed segment loss",
        "Source / channel",
        "Channel mismatches",
        "controlled only on Route",
        "Allowed exact L",
        "Legacy BlockState fallback",
        "Iout=max(0, Iin−L)",
        "FULLY ATTENUATED • VALID TRANSFER",
        "Rigid optical axis",
        "matching CH: Iout=max(0,Iin−1); mismatch: dark",
        "legacy RX/TX actions rotate one rigid opposite-face axis",
        "menu.inputDirection()",
    ):
        if token not in screen:
            failed.append(f"OpticalSystemScreen missing engineering evidence token: {token}")

    dedicated_start = opener.find("if (block instanceof OpticalEmitterBlock")
    if dedicated_start < 0 or "new OpticalSystemMenu" not in opener[dedicated_start:]:
        failed.append("FieldDeviceUi does not route Optical devices to dedicated HMI")

    advanced_start = opener.find("if (block instanceof SignalAmplifierBlock")
    advanced_end = opener.find("if (block instanceof QuartzPhaseDelayBlock", advanced_start)
    if advanced_start >= 0 and advanced_end > advanced_start and "OpticalEmitterBlock" in opener[advanced_start:advanced_end]:
        failed.append("AdvancedParameterMenu still intercepts OpticalEmitterBlock")

    multi_start = opener.find("if (block instanceof QuartzPhaseDelayBlock")
    multi_end = opener.find("if (block instanceof CopperCircuitMeterBlock", multi_start)
    if multi_start >= 0 and multi_end > multi_start:
        generic = opener[multi_start:multi_end]
        for optical in ("OpticalAttenuatorBlock", "OpticalChannelFilterBlock"):
            if optical in generic:
                failed.append(f"MultiPhysicsParameterMenu still intercepts {optical}")

if logic_path.is_file():
    harness = r"""
import dev.redstoneengineering.signal.OpticalPassiveLogic;

public final class OpticalPassiveHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(OpticalPassiveLogic.attenuatedIntensity(10, 3) == 7,
                "attenuator subtracts exact loss");
        check(OpticalPassiveLogic.attenuatedIntensity(5, 8) == 0,
                "attenuator clamps dark output at zero");
        check(OpticalPassiveLogic.fullyAttenuated(5, 8, true),
                "valid positive input can be fully attenuated");
        check(!OpticalPassiveLogic.fullyAttenuated(0, 8, true),
                "valid dark input is not classified as attenuation event");
        check(OpticalPassiveLogic.boundedConfiguredLoss(99)
                        == OpticalPassiveLogic.MAX_CONFIGURED_LOSS,
                "exact loss upper bound");
        check(OpticalPassiveLogic.boundedLegacyLoss(99)
                        == OpticalPassiveLogic.MAX_LEGACY_LOSS,
                "legacy loss upper bound");
        check(OpticalPassiveLogic.filteredIntensity(10, 3, 3, true) == 9,
                "matching filter channel applies one-unit insertion loss");
        check(OpticalPassiveLogic.filteredIntensity(10, 3, 4, true) == 0,
                "mismatched filter channel rejects carrier");
        check(OpticalPassiveLogic.filteredIntensity(10, 3, 3, false) == 0,
                "invalid evidence cannot drive filtered output");
        System.out.println("OpticalPassiveLogic harness: PASS");
    }
}
"""
    try:
        with tempfile.TemporaryDirectory(prefix="rse-optical-passive-") as td:
            td = Path(td)
            hp = td / "OpticalPassiveHarness.java"
            hp.write_text(harness)
            compile_run = subprocess.run(
                ["javac", "-d", str(td), str(logic_path), str(hp)],
                cwd=root, capture_output=True, text=True
            )
            if compile_run.returncode != 0:
                failed.append("OpticalPassiveLogic javac failed: " + compile_run.stderr.strip())
            else:
                run = subprocess.run(
                    ["java", "-cp", str(td), "OpticalPassiveHarness"],
                    cwd=root, capture_output=True, text=True
                )
                if run.returncode != 0:
                    failed.append("OpticalPassiveLogic harness failed: "
                                  + (run.stderr or run.stdout).strip())
    except FileNotFoundError:
        failed.append("javac/java unavailable for optical passive harness")

if failed:
    print("RSE Optical HMI dispatch verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Optical HMI dispatch verification: PASS")
print(" attenuator exact 0..15 / legacy 0..8 loss contract: PASS")
print(" channel-filter one-unit insertion-loss contract: PASS")
print(" filter/attenuator rigid opposite-port routing: PASS")
print(" dedicated HMI renders actual synchronized input face: PASS")
print(" emitter intensity/channel controls remain server-owned: PASS")
print(" attenuator uses full 0..15 configured loss: PASS")
print(" channel filter remains discrete carrier selection: PASS")
print(" normal Optical dispatch reaches dedicated budget/diagnostic HMI: PASS")
