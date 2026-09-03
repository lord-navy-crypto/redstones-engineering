# RSE Alpha 1.0.3 — Interactive Validation Matrix

This matrix is the local `runClient` gate for the Alpha 1.0.3 release candidate. CI proves that the repository verifies, compiles, and builds; this document checks that the engineering behavior is understandable and works in an actual Minecraft world.

## Test environment

Record before testing:

```text
RSE artifact/version: 1.0.3-alpha
Minecraft: 1.21.1
NeoForge: 21.1.249
Java: 21
World type:
Test date:
Tester:
```

Use a fresh Creative test world when possible. Keep vanilla redstone dust, levers/buttons, comparators/repeaters, and visible signal sources nearby so RSE behavior can be compared with ordinary 0–15 redstone.

## Result codes

- `PASS` — behavior matches the documented engineering contract.
- `FAIL` — behavior is reproducibly wrong or crashes.
- `OBSERVE` — behavior works but usability, balance, text, or visualization should be refined.
- `NOT TESTED` — test has not yet been performed.

## 1. Repository / launch smoke test

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Client launch | Run `./gradlew runClient` | NeoForge client reaches title screen without RSE startup error | NOT TESTED |
| Creative inventory | Open the RSE creative tab / locate RSE blocks | Registered RSE devices appear and can be placed | NOT TESTED |
| Resource smoke test | Place several Alpha 1.0.3 blocks | No missing purple/black model caused by missing RSE JSON/model resources | NOT TESTED |
| Save/reload | Save world, exit, reopen | World reopens without registry/load failure | NOT TESTED |

## 2. PID controller

Port contract:

```text
BACK  = setpoint (0..15)
LEFT  = process value (0..15)
FRONT = output (0..15)
RIGHT = inhibit
UP    = mode select: 0=AUTO, >0=MANUAL
DOWN  = manual output (0..15)
```

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Auto response | UP=0; apply different BACK setpoint and LEFT process values | FRONT responds within 0..15 and diagnostics show AUTO | NOT TESTED |
| Manual output | Power UP; vary DOWN from 0..15 | FRONT follows manual command while diagnostics show MANUAL | NOT TESTED |
| Inhibit | Apply RIGHT signal | Output becomes 0 while inhibited | NOT TESTED |
| Output bounds | Force large positive/negative error | FRONT never leaves 0..15 | NOT TESTED |
| Anti-windup | Hold controller at saturation, then reduce error | Recovery does not show runaway integral accumulation | NOT TESTED |
| Bumpless transfer | Stabilize a manual output, then remove UP signal to enter AUTO | Output does not make an unnecessary large one-tick jump solely because mode changed | NOT TESTED |
| Runtime reset | Shift-use controller | Integral/derivative/bias runtime resets and output returns to safe reset behavior | NOT TESTED |
| Tuning presets | Use block repeatedly | P/PI/PID presets cycle and remain functional | NOT TESTED |

## 3. Servo actuator

Port contract:

```text
BACK  = command
UP    = 0 POSITION, >0 VELOCITY
RIGHT = BRAKE
```

Velocity convention:

```text
0..6  = reverse
7     = stop
8..15 = forward
```

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Position mode | UP=0; change BACK command | Servo internal position approaches target with finite slew | NOT TESTED |
| Velocity stop | UP>0; BACK=7 | Applied velocity approaches/stays 0 | NOT TESTED |
| Forward velocity | UP>0; BACK>7 | Position moves in positive direction until soft limit | NOT TESTED |
| Reverse velocity | UP>0; BACK<7 | Position moves in negative direction until soft limit | NOT TESTED |
| Brake | Apply RIGHT while moving | Applied velocity becomes 0 | NOT TESTED |
| Soft limits | Command motion beyond either end | Position remains within 0..15; softLimitHits increases | NOT TESTED |
| Slew setting | Cycle block setting | Different slew settings visibly change maximum motion rate | NOT TESTED |

## 4. 8-bit data bus diagnostics

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Single driver | Connect one valid bus driver | Value is readable and driverCount=1 | NOT TESTED |
| Same-value multi-driver | Connect two physical drivers carrying the same value | Bus remains readable but contention/same-value-multidriver diagnostic increases | NOT TESTED |
| Conflicting drivers | Connect two drivers with different values | Bus becomes invalid/conflict rather than last-writer-wins | NOT TESTED |
| Disconnect recovery | Remove one conflicting driver | Bus returns to a valid single-value state | NOT TESTED |
| Bounded network | Build a moderate multi-node bus | No runaway scan or obvious world freeze | NOT TESTED |

## 5. Radio diagnostics

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Valid link | One transmitter and receiver on same channel within range | Receiver outputs payload and counts valid samples | NOT TESTED |
| Channel mismatch | Move receiver to a different channel | Payload becomes unavailable/undecodable as appropriate | NOT TESTED |
| Same-channel collision | Operate multiple same-channel transmitters in range | Collision count increases and frame is invalid | NOT TESTED |
| Adjacent interference | Add transmitter on adjacent channel | Link/noise diagnostics change without redefining payload | NOT TESTED |
| Obstacle path | Add blocks along radio path | Link quality/noise reflects obstacle penalty | NOT TESTED |
| Dropout | Start with valid link, then disrupt it | Dropout counter increases after valid→invalid transition | NOT TESTED |
| Reset | Shift-use receiver | Accumulated receiver statistics reset | NOT TESTED |

## 6. Pneumatic system

Reference chain:

```text
Air Compressor → Pneumatic Pipe → Proportional Valve → Pipe → Pneumatic Cylinder
```

Safety branch:

```text
Air source/network → Pneumatic Relief Valve
```

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Source/network | Run compressor into connected pipe | Internal network pressure propagates through valid topology | NOT TESTED |
| Line loss | Compare pressure across a longer pipe path | Pressure decreases according to current lumped line-loss model | NOT TESTED |
| Proportional valve | Vary valve opening 0..15 | Downstream pressure changes approximately with opening fraction | NOT TESTED |
| Directionality | Attempt reverse propagation through directional proportional/check components | Reverse path is blocked where documented | NOT TESTED |
| Relief safety | Raise upstream pressure above relief setpoint | Pressure is clamped and relief-trip diagnostics increase | NOT TESTED |
| Cylinder | Feed cylinder with controlled pneumatic pressure | Cylinder behavior responds with finite rather than instantaneous actuator behavior | NOT TESTED |
| Broken topology | Remove a pipe/component | Network recomputes without crash; disconnected section loses appropriate source behavior | NOT TESTED |

## 7. Operations / IOE monitor

Port contract:

```text
DOWN       = RUN state
UP         = completed-cycle pulse
HORIZONTAL = queue/WIP proxy (0..15)
```

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Nominal | RUN active with moderate queue/cycle pulses | State can remain NOMINAL while statistics accumulate | NOT TESTED |
| Starvation | RUN active with queue=0 | starved counter increases | NOT TESTED |
| Blocking proxy | RUN off with queue>0 | blocked/fault proxy increases; state becomes safety-limited and may become FAILED after sustained stop | NOT TESTED |
| Congestion | RUN with high queue | CONGESTED/OVERLOADED classification appears at documented thresholds | NOT TESTED |
| Instability proxy | Repeatedly toggle RUN | Transition metric rises and may classify UNSTABLE | NOT TESTED |
| Queue noise proxy | Rapidly vary queue value | Queue-variation metric rises and may classify NOISY | NOT TESTED |
| Cycle statistics | Pulse UP over several cycles | Throughput and last/avg/max cycle time update | NOT TESTED |
| Reset | Shift-use monitor | accumulated statistics reset | NOT TESTED |

## 8. Vanilla compatibility / 0–15 boundary

| Test | Procedure | Expected result | Result |
| --- | --- | --- | --- |
| Vanilla source input | Drive RSE signal/control blocks from ordinary redstone | RSE reads legal vanilla signal strengths | NOT TESTED |
| RSE output to vanilla | Feed RSE processor output into vanilla redstone | Vanilla network receives a legal 0..15 signal | NOT TESTED |
| Comparator/repeater smoke test | Route representative RSE outputs through vanilla components | No incompatible out-of-range signal behavior | NOT TESTED |
| Large diagnostic values | Exercise bus/radio/IOE/pneumatic diagnostics | High-cardinality measurements do not require 0..255 BlockState variants | NOT TESTED |

## Release gate

Alpha 1.0.3 should not be tagged as a public release candidate until:

1. GitHub Actions static verification passes.
2. GitHub Actions `compileJava` passes.
3. GitHub Actions `clean build` passes.
4. CI artifact checksum is generated.
5. `runClient` launches successfully on the target Mac.
6. PID, Servo, Bus, Radio, Pneumatic, and Operations critical-path tests above have no release-blocking `FAIL` result.

`OBSERVE` findings should be converted into focused follow-up refinements rather than silently changing the engineering contract during release packaging.
