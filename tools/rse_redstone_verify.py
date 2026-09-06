from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
checks = []


def require(path, text, label):
    source = (root / path).read_text(encoding="utf-8")
    checks.append((text in source, label))


def require_regex(path, pattern, label):
    source = (root / path).read_text(encoding="utf-8")
    checks.append((re.search(pattern, source, re.MULTILINE | re.DOTALL) is not None, label))


def forbid(path, text, label):
    source = (root / path).read_text(encoding="utf-8")
    checks.append((text not in source, label))


require(
    Path("src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java"),
    "isEngineeringPort(state, direction.getOpposite())",
    "Directional redstone port query maps to physical side",
)
require(
    Path("src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java"),
    "direction == outputSide(state).getOpposite()",
    "Directional output uses backwards redstone query convention",
)
for name in ["EdgeDetectorBlock.java", "PulseShaperBlock.java"]:
    require(
        Path("src/main/java/dev/redstoneengineering/block") / name,
        "if (remaining > 0)",
        f"{name} schedules final clear tick",
    )
forbid(
    Path("src/main/java/dev/redstoneengineering/block/SignalTapBlock.java"),
    "direction == outputSide(state) || direction == leftOf(facing)",
    "Signal Tap no longer emits on reversed sides",
)

# Verify the behavior rather than a historical minified variable spelling. The
# same terminal instance that defines vanillaSide(...) must be used for the
# neighbor notification on that physical output position.
require_regex(
    Path("src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java"),
    r"(?P<level>\w+)\.updateNeighborsAt\(\s*(?P<pos>\w+)\.relative\(\s*(?P<terminal>\w+)\.vanillaSide\(\s*(?P<state>\w+)\s*\)\s*\)\s*,\s*(?P=terminal)\s*\)",
    "Cable output terminal notifies vanilla output neighbor",
)

failed = False
for ok, label in checks:
    print(("OK  " if ok else "FAIL") + ": " + label)
    failed |= not ok
if failed:
    sys.exit(1)
print("PASS: redstone processing static verification complete")
