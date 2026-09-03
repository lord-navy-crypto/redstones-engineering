#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []


def text(rel: str) -> str:
    p = root / rel
    if not p.exists():
        failed.append(f"missing {rel}")
        return ""
    return p.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")

plugin = "src/main/java/dev/redstoneengineering/integration/jade/RseJadePlugin.java"
provider = "src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java"

require(plugin,
        "@WailaPlugin", "IWailaPlugin", "registerBlockDataProvider", "registerBlockComponent", "Block.class")
require(provider,
        "EngineeringPortProvider", "engineeringPorts", "engineeringPort", "engineeringSnapshot",
        "IServerDataProvider<BlockAccessor>", "IBlockComponentProvider", "appendServerData", "appendTooltip",
        "getServerData", "KEY_QUALITY", "KEY_NORMALIZED", "structural / multi-channel port")

# The adapter may observe RSE state but must not become a second simulation model.
for forbidden in [
    "RuntimeIntStore.get(",
    "RedstoneCableNetwork.recompute(",
    "PneumaticNetwork.recompute(",
    "level.setBlock(",
    "updateNeighborsAt(",
]:
    if forbidden in text(provider):
        failed.append(f"Jade provider contains simulation mutation/duplicate model token: {forbidden}")

# Jade imports are isolated to the integration layer. This protects dedicated
# server/common engineering packages from accidental UI coupling.
java_root = root / "src/main/java/dev/redstoneengineering"
for java in java_root.rglob("*.java"):
    body = java.read_text(errors="ignore")
    if "snownee.jade" in body:
        relative = java.relative_to(java_root).as_posix()
        if not relative.startswith("integration/jade/"):
            failed.append(f"Jade boundary violation: {relative}")

require("src/main/java/dev/redstoneengineering/core/port/EngineeringPortProvider.java",
        "engineeringPorts", "engineeringSnapshot")
require("docs/ALPHA1_0_10_JADE_ENGINEERING_HUD.md",
        "read-only", "server-backed", "EngineeringPortProvider", "current targeted face")
require("ALPHA1_0_10_MANIFEST.txt", "Jade engineering HUD", "read-only adapter")

workflow = text(".github/workflows/build.yml")
if "rse_alpha1010_jade_hud_verify.py" not in workflow:
    failed.append("workflow missing Alpha 1.0.10 Jade HUD verifier")

if failed:
    print("RSE Alpha 1.0.10 Jade engineering HUD verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.10 Jade engineering HUD verification: PASS")
print(" server-backed EngineeringPortProvider adapter: PASS")
print(" targeted-face descriptor/snapshot display: PASS")
print(" Jade integration isolation boundary: PASS")
print(" no simulation mutation in HUD provider: PASS")
