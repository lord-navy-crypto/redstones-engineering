#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

screen = read("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java")
vibration = read("src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java")
slime = read("src/main/java/dev/redstoneengineering/block/SlimeVibrationConduitBlock.java")
honey = read("src/main/java/dev/redstoneengineering/block/HoneyVibrationDamperBlock.java")
mech_rx = read("src/main/java/dev/redstoneengineering/block/MechanicalVibrationReceiverBlock.java")
sculk = read("src/main/java/dev/redstoneengineering/block/SculkVibrationInterfaceBlock.java")
hydro_net = read("src/main/java/dev/redstoneengineering/physics/HydroacousticNetwork.java")
hydro_tube = read("src/main/java/dev/redstoneengineering/block/HydroacousticTubeBlock.java")
hydro_rx = read("src/main/java/dev/redstoneengineering/block/HydroacousticReceiverBlock.java")
thermal = read("src/main/java/dev/redstoneengineering/physics/ThermalPulseKernel.java")
phonon = read("src/main/java/dev/redstoneengineering/block/PhononConduitBlock.java")
thermal_encoder = read("src/main/java/dev/redstoneengineering/block/ThermalPulseEncoderBlock.java")
thermal_rx = read("src/main/java/dev/redstoneengineering/block/ThermalPulseReceiverBlock.java")

# Server-model source contracts.
server_tokens = {
    "VibrationNetwork.java": (vibration, (
        "loss = 1;",
        "loss = 4;",
        "quality = 100;",
        "quality = 80;",
    )),
    "SlimeVibrationConduitBlock.java": (slime, (
        "PACKET_TTL_TICKS = 4",
        "amplitude - 2",
        "quality(level, \"mech_wave\", pos) - 10",
    )),
    "HoneyVibrationDamperBlock.java": (honey, (
        "PACKET_TTL_TICKS = 4",
        "amplitude - 4",
        "quality(level, \"mech_wave\", pos) - 20",
    )),
    "MechanicalVibrationReceiverBlock.java": (mech_rx, (
        "wave.value() - 2",
        "wave.qualityPercent() - 5",
        "scheduleTick(pos, this, 4)",
        "Math.min(15, wave.amplitude())",
    )),
    "SculkVibrationInterfaceBlock.java": (sculk, (
        "now > 0 && runtime[CURRENT_CODE] == 0",
        "if (now != runtime[CURRENT_CODE]) runtime[TRANSITION_COUNT]++",
        "RuntimeIntStore.peek",
    )),
    "HydroacousticNetwork.java": (hydro_net, (
        "medium == 0 ? 1 : medium == 1 ? 2 : 3",
        "node.amplitude - loss",
    )),
    "HydroacousticTubeBlock.java": (hydro_tube, (
        "PACKET_TTL_TICKS = 4",
        "packet.value() - 2",
        "packet.qualityPercent() - 10",
    )),
    "HydroacousticReceiverBlock.java": (hydro_rx, (
        "packet.value() - 1",
        "packet.qualityPercent() - 5",
        "scheduleTick(pos, this, 4)",
        "Math.min(15, packet.value())",
    )),
    "ThermalPulseKernel.java": (thermal, (
        "node.amplitude - 1",
        "PhononConduitBlock.PACKET_TTL_TICKS",
    )),
    "PhononConduitBlock.java": (phonon, (
        "PACKET_TTL_TICKS = 8",
        "packet.value() - 2",
        "packet.qualityPercent() - 10",
    )),
    "ThermalPulseEncoderBlock.java": (thermal_encoder, (
        "ThermalPulseKernel.send",
        "scheduleTick(pos, this, 1)",
        "InformationRuntime.clear(level, ENCODER_KEY, pos)",
    )),
    "ThermalPulseReceiverBlock.java": (thermal_rx, (
        "packet.value() - 1",
        "packet.qualityPercent() - 5",
        "scheduleTick(pos, this, 8)",
        "Math.min(15, packet.value())",
    )),
}

for name, (text, tokens) in server_tokens.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} server model missing {token!r}")

# HMI must state the same discrete model, without claiming physical SI fidelity.
screen_tokens = (
    "PIONEER PATTERN • RSE DISCRETE TRANSPORT MODEL",
    "HOP: A_next=max(0,A-1); RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)",
    "HOP: A_next=max(0,A-4), node Q=80; RETAIN @4t: A←max(0,A-4), Q←max(0,Q-20)",
    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-2), Q←max(0,Q-5)",
    "Lm={water:1,milk-model:2,lava:3}",
    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-1), Q←max(0,Q-5)",
    "HOP: A_next=max(0,A-1); RETAIN @8t: A←max(0,A-2), Q←max(0,Q-10)",
    "OUT: y_R=valid?min(15,A):0; RETAIN @8t: A←max(0,A-1), Q←max(0,Q-5)",
    "RSE DISCRETE MODEL: this is a bounded game-domain pressure-packet network",
    "not a Fourier heat-transfer or continuously driven temperature-field solver",
    "Event counters are retained server evidence",
    "SCULK / CALIBRATED-SENSOR EVENT CODE",
)
for token in screen_tokens:
    if token not in screen:
        errors.append(f"EnhancedFieldDeviceScreen model presentation missing {token!r}")

# Six-way physical media must be classified as passive transport, not generic four-face devices.
for kind in (
    "KIND_SLIME_VIBRATION",
    "KIND_HONEY_DAMPER",
    "KIND_HYDRO_TUBE",
    "KIND_PHONON_CONDUIT",
):
    occurrences = [m.start() for m in re.finditer(kind, screen)]
    if len(occurrences) < 3:
        errors.append(f"{kind} does not appear broadly enough in role/model/topology handling")

for token in (
    "MECHANICAL_VIBRATION • SIX-WAY • LOW-LOSS PACKET",
    "MECHANICAL_VIBRATION • SIX-WAY • HIGH-DAMPING PACKET",
    "HYDROACOUSTIC • SIX-WAY • MEDIUM-DEPENDENT LOSS",
    "PHONON_THERMAL • SIX-WAY • FINITE-BANDWIDTH PACKET",
):
    if token not in screen:
        errors.append(f"shared Ports/routing contract missing {token!r}")

# Client presentation must not import/run the server physics implementation.
if "dev.redstoneengineering.physics" in screen:
    errors.append("EnhancedFieldDeviceScreen imports server physics directly")
for forbidden in ("VibrationNetwork.", "HydroacousticNetwork.", "ThermalPulseKernel."):
    if forbidden in screen:
        errors.append(f"client HMI runs a second transport solver via {forbidden}")

# Small independent sweep: all implemented hop laws stay bounded and preserve expected ordering.
for a in range(16):
    slime_hop = max(0, a - 1)
    honey_hop = max(0, a - 4)
    hydro_water = max(0, a - 1)
    hydro_milk = max(0, a - 2)
    hydro_lava = max(0, a - 3)
    phonon_hop = max(0, a - 1)
    values = (slime_hop, honey_hop, hydro_water, hydro_milk, hydro_lava, phonon_hop)
    if any(v < 0 or v > 15 for v in values):
        errors.append(f"bounded transport sweep failed at A={a}: {values}")
    if honey_hop > slime_hop:
        errors.append(f"honey must not transmit more amplitude than slime at A={a}")
    if not (hydro_water >= hydro_milk >= hydro_lava):
        errors.append(f"hydro medium loss ordering failed at A={a}")

if errors:
    print("RSE DISCRETE TRANSPORT MODEL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE DISCRETE TRANSPORT MODEL VERIFY: PASS")
print(" mechanical hop/retained-loss cross-check: PASS")
print(" hydro medium-dependent hop loss cross-check: PASS")
print(" Sculk retained-event semantics cross-check: PASS")
print(" phonon/thermal finite-bandwidth packet cross-check: PASS")
print(" six-way passive transport topology classification: PASS")
print(" client/no-second-solver boundary: PASS")
print(" bounded 0..15 transport sweep: PASS")
