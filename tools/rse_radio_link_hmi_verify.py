#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing radio diagnostic contract {token!r}")


kernel_rel = "src/main/java/dev/redstoneengineering/physics/RadioKernel.java"
menu_rel = "src/main/java/dev/redstoneengineering/ui/menu/RadioLinkMenu.java"
screen_rel = "src/main/java/dev/redstoneengineering/client/ui/RadioLinkScreen.java"
receiver_rel = "src/main/java/dev/redstoneengineering/block/RadioReceiverBlock.java"

# RadioKernel remains the single authoritative radio model. The HMI may expose
# existing loss witnesses but must not create a second propagation calculation.
require(
    kernel_rel,
    "public static final int RANGE = 32;",
    "public static final int MIN_DECODE_QUALITY = 20;",
    "int distanceBlocks,",
    "public int decodeMargin()",
    "return quality - MIN_DECODE_QUALITY;",
    "int distanceLoss = (int) Math.round(55.0 * distance / RANGE);",
    "int obstacleLoss = Math.min(25, obstacles * 2);",
    "int fade = deterministicFade(level, transmitterPos, rx);",
    "int interferencePenalty = Math.min(30, adjacent * 8);",
    "boolean collision = drivers > 1;",
    "boolean valid = coverageComplete && drivers == 1 && quality >= MIN_DECODE_QUALITY;",
    "bestDistance = (int) Math.ceil(distance);",
)

# Preserve deterministic fading; do not replace it with nondeterministic/random noise.
kernel = read(kernel_rel)
for forbidden in ("RandomSource", "Math.random(", "new Random(", "ThreadLocalRandom"):
    if forbidden in kernel:
        errors.append(f"{kernel_rel}: radio model introduced nondeterministic noise via {forbidden!r}")

# Menu is the server-side synchronization boundary. It may read RadioKernel and retained
# counters; client screens should consume these slots instead of sampling the world.
require(
    menu_rel,
    "public static final int RANGE_BLOCKS = RadioKernel.RANGE;",
    "public static final int MIN_DECODE_QUALITY = RadioKernel.MIN_DECODE_QUALITY;",
    "private final DataSlot linkQuality = trackedInt();",
    "private final DataSlot adjacentAggressors = trackedInt();",
    "private final DataSlot obstacleHits = trackedInt();",
    "private final DataSlot distanceBlocks = trackedInt();",
    "private final DataSlot decodeMargin = trackedInt();",
    "linkQuality.set(reception.quality());",
    "adjacentAggressors.set(reception.interference());",
    "obstacleHits.set(reception.obstacles());",
    "distanceBlocks.set(reception.distanceBlocks());",
    "decodeMargin.set(reception.decodeMargin());",
    "public int availabilityPercent()",
)

# Receiver tick counters remain authoritative retained chronology.
require(
    receiver_rel,
    "diagnostics[0]++;",
    "if (rx.valid()) diagnostics[1]++;",
    "else if (rx.collision()) diagnostics[3]++;",
    "diagnostics[4] += droppedThisTick;",
    "diagnostics[8] = noiseStrength;",
)

# Client is observer-only: no physics package import, world sampling, retained-runtime access,
# propagation calculation, or fabricated packet history.
screen = read(screen_rel)
require(
    screen_rel,
    '"SAME-CHANNEL COLLISION"',
    '"BELOW DECODE MARGIN"',
    '"MARGINAL LINK"',
    '"VALID • ADJACENT INTERFERENCE"',
    '"VALID • OBSTRUCTED PATH"',
    '"HEALTHY LINK"',
    '"NEXT • move one same-channel transmitter',
    '"NEXT • separate adjacent channels first',
    '"NEXT • improve line-of-sight or shorten the path',
    '"Counters are receiver-tick evidence; the client does not fabricate packet history."',
)
for forbidden in (
    "dev.redstoneengineering.physics",
    "RadioKernel.receivePacket",
    "RuntimeIntStore",
    "level.getBlockState(",
    "level.hasChunkAt(",
    "Math.sqrt(",
    "distanceLoss",
    "obstacleLoss",
    "interferencePenalty",
    "deterministicFade",
):
    if forbidden in screen:
        errors.append(f"{screen_rel}: client-side radio authority leak via {forbidden!r}")

# Zero remains a valid payload when evidence quality is VALID.
require(screen_rel, '"Payload 0 is a valid frame when source evidence is VALID."')

if errors:
    print("RSE RADIO LINK HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE RADIO LINK HMI VERIFY: PASS")
print("  authoritative distance/obstacle/adjacent/fading model retained: PASS")
print("  receiver distance + decode-margin synchronization: PASS")
print("  collision/interference/obstruction diagnoses + NEXT guidance: PASS")
print("  client observer-only / no second radio solver: PASS")
print("  deterministic radio evidence / no random noise: PASS")
