# RSE Alpha 1.0.4 — Interactive Validation Matrix

This matrix validates the Alpha 1.0.4 instrumentation/topology refinements in a real NeoForge client. CI compile/build is necessary but does not replace in-game behavior testing.

## Result codes

- `PASS` — matches the documented engineering contract.
- `FAIL` — reproducibly violates the contract or crashes.
- `OBSERVE` — functional, but UX/balance/visualization should be refined.
- `NOT TESTED` — not yet tested.

## Environment

```text
RSE: 1.0.4-alpha
Minecraft: 1.21.1
NeoForge: 21.1.249
Java: 21
Test world:
Test date:
```

## 1. Signal Analyzer — TAP mode

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Default topology | Place analyzer against a redstone node | Starts in TAP; does not attach as a redstone conductor | NOT TESTED |
| Dust-node accuracy | Build 15 source → dust POWER=14; test the dust | Analyzer reports 14, not adjacent 15 | NOT TESTED |
| Directional source face | Attach analyzer to a non-output face of a directional source | Reports the tested face rather than strongest unrelated face | NOT TESTED |
| Continuous statistics | Change tested value several times | samples/min/max/change/rising/falling statistics update | NOT TESTED |
| Stable time | Hold a constant value | `stableFor` increases | NOT TESTED |
| Six-side survey | Shift-use any non-UP face | Reports N/S/E/W/U/D values and strongest side | NOT TESTED |
| Reset | Shift-use UP face | Analyzer statistics reset without changing tested circuit | NOT TESTED |

## 2. Signal Analyzer — INLINE mode

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Enter INLINE | Right-click UP face | Mode changes TAP → INLINE | NOT TESTED |
| Two-port connectivity | Inspect/connect TEST and opposite OUT | Only the two signal-path sides connect | NOT TESTED |
| Pass-through 0 | Apply TEST=0 | OUT=0 | NOT TESTED |
| Pass-through mid-scale | Apply TEST=7/8 | OUT equals measured value | NOT TESTED |
| Pass-through full-scale | Apply TEST=15 | OUT=15 | NOT TESTED |
| Dynamic pass-through | Vary TEST repeatedly | OUT follows sampled 0..15 input; statistics update | NOT TESTED |
| Return TAP | Right-click UP again | Output is removed/zeroed and analyzer becomes non-invasive | NOT TESTED |
| Vertical placement | Place analyzer with FACING UP/DOWN | TEST/OUT axis remains opposite and functional | NOT TESTED |

## 3. Signal Probe direction accuracy

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Probe source output face | Place probe on actual output side | Correct channel value is sampled | NOT TESTED |
| Probe non-output face | Move probe to non-output side | Does not report unrelated strongest source face | NOT TESTED |
| Dust node | Probe attenuated dust adjacent to stronger source | Reports dust POWER value | NOT TESTED |
| Channel cycle | Right-click probe | A→B→C→D cycle remains intact | NOT TESTED |

## 4. Instrument-network topology

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Direct probe | Probe adjacent to scope/logic analyzer | probeNodes and activeChannels reflect connection | NOT TESTED |
| Cable chain | Insert several Instrument Cable blocks | cableNodes reflects traversed network | NOT TESTED |
| Multiple channels | Add A/B/C probes | channels reports 3/4 and values stay independent | NOT TESTED |
| Duplicate channel | Add two A probes | A becomes AMBIGUOUS; duplicateChannels increases | NOT TESTED |
| Remove duplicate | Remove one A probe | A returns valid | NOT TESTED |
| Large bounded network | Build a large but reasonable instrument network | Scan stays bounded without runaway traversal | NOT TESTED |

## 5. Oscilloscope integration

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Direction-aware waveform | Feed a directional source through Probe A | Scope samples the physically probed face | NOT TESTED |
| Topology status | Read scope status | Displays cables/probes/channels/duplicates/bounded status | NOT TESTED |
| Existing triggers | Exercise trigger controls | Alpha 1.0.3 trigger behavior still works | NOT TESTED |
| Cursor behavior | Move cursors | Existing cursor delta behavior remains intact | NOT TESTED |

## 6. Logic Analyzer integration

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Threshold behavior | Probe channels around threshold | Digital mask still follows threshold | NOT TESTED |
| Topology status | Read logic analyzer status | Displays instrument-network topology summary | NOT TESTED |
| Duplicate probe | Duplicate one channel | Channel status becomes AMBIGUOUS rather than last-writer-wins | NOT TESTED |
| Existing trigger/edge | Exercise trigger source and edge controls | Existing behavior remains intact | NOT TESTED |

## 7. Pneumatic Cylinder feedback and terminal topology

Reference build:

```text
Compressor → Pipe → Cylinder(BACK)
Cylinder(FRONT) → redstone dust / analyzer
```

Isolation test build:

```text
Upstream pneumatic network → Cylinder(BACK) [ Cylinder ] FRONT → Pipe → downstream pneumatic receiver
```

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Pneumatic input | Apply pressure to BACK | Position approaches pressure-derived target | NOT TESTED |
| Front feedback | Read FACING/FRONT | Outputs position 0..15 | NOT TESTED |
| Redstone side isolation | Put dust on left/right/back sides | No cylinder feedback signal on those sides | NOT TESTED |
| Feedback analyzer | Place Analyzer on cylinder FRONT | Analyzer reads cylinder position feedback | NOT TESTED |
| Terminal actuator | Put Pneumatic Pipe/Receiver beyond FRONT | Upstream pressure does not pass through cylinder | NOT TESTED |
| Side pneumatic isolation | Put pneumatic pipe beside cylinder | Side branch is not joined through cylinder | NOT TESTED |
| Network separation | Build two pneumatic networks on BACK and FRONT | Cylinder does not merge the two networks during discovery | NOT TESTED |
| Pressure diagnostics | Vary source pressure | Current/peak pressure update | NOT TESTED |
| Reversal diagnostics | Raise then lower target | Motion reversal count updates | NOT TESTED |

## 8. Regression gates

| Test | Expected | Result |
| --- | --- | --- |
| PID Manual/Auto | Alpha 1.0.3 behavior remains functional | NOT TESTED |
| Servo Velocity/Brake | Alpha 1.0.3 behavior remains functional | NOT TESTED |
| Data Bus contention | Conflict/same-value multi-driver diagnostics remain functional | NOT TESTED |
| Radio diagnostics | Valid/undecodable/collision/dropout counters remain functional | NOT TESTED |
| Pneumatic proportional/relief | Existing pneumatic safety/control components remain functional | NOT TESTED |
| Operations monitor | IOE classification/metrics remain functional | NOT TESTED |
| Vanilla 0–15 | No RSE redstone output exceeds legal signal strength | NOT TESTED |
| Save/reload | World saves and reloads without RSE registry/state error | NOT TESTED |

## Release gate

Alpha 1.0.4 is ready for merge when branch CI passes:

1. repository verifier;
2. all previous Alpha verifiers;
3. Alpha 1.0.4 verifier;
4. Java 21 `compileJava`;
5. clean Gradle build;
6. SHA-256 artifact generation/upload.

It is ready for a public release candidate only after critical-path `runClient` tests above have no release-blocking `FAIL` result.
