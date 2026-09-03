# RSE Alpha 1.0.5 — Interactive Quality Validation Matrix

Use this matrix after CI passes. It validates quality/calibration behavior that cannot be proven by headless compilation alone.

## Result codes

- `PASS` — behavior matches contract.
- `FAIL` — reproducible contract violation or crash.
- `OBSERVE` — functional but UX/balance/visual presentation should improve.
- `NOT TESTED` — not yet tested.

## Environment

```text
RSE: 1.0.5-alpha
Minecraft: 1.21.1
NeoForge: 21.1.249
Java: 21
Test world:
Test date:
```

## 1. Signal Analyzer rolling measurement quality

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Warmup | Place analyzer on constant node and inspect immediately | Window starts below 4 samples and reports WARMUP | NOT TESTED |
| Steady window | Hold raw signal constant for >32 ticks | p2p=0, meanStep=0, state=STEADY | NOT TESTED |
| Small variation | Alternate nearby values such as 7/8 | Rolling metrics update without affecting circuit | NOT TESTED |
| Dynamic ramp | Sweep 5→6→7→8→9 | average/p2p/meanStep reflect recent window | NOT TESTED |
| Lifetime vs window | Produce an old 0 and 15, then hold 7 | lifetime min/max remain 0/15 while recent p2p eventually falls | NOT TESTED |
| Sample age | Inspect continuously sampled analyzer | sampleAge stays near sampling interval | NOT TESTED |
| Reset | Shift-use UP | lifetime/window statistics reset; mode/calibration configuration remains usable | NOT TESTED |

## 2. Analyzer display calibration

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Zero offset | Set calibration 0 on raw 7 | display=7 | NOT TESTED |
| +2 offset | Cycle DOWN until +2 on raw 7 | display=9 | NOT TESTED |
| -2 offset | Cycle DOWN until -2 on raw 7 | display=5 | NOT TESTED |
| Lower clamp | raw=0, calibration=-2 | display remains 0 | NOT TESTED |
| Upper clamp | raw=15, calibration=+2 | display remains 15 | NOT TESTED |
| Config persistence | Set nonzero calibration, save/reload world | configured calibration remains in BlockState | NOT TESTED |

## 3. Calibration isolation from INLINE circuit

Build:

```text
source → TEST [Analyzer INLINE] OUT → downstream dust/analyzer
```

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Raw pass-through | raw TEST=7, calibration=0 | OUT=7 | NOT TESTED |
| Positive calibration isolation | raw TEST=7, calibration=+2 | display=9 but OUT stays 7 | NOT TESTED |
| Negative calibration isolation | raw TEST=7, calibration=-2 | display=5 but OUT stays 7 | NOT TESTED |
| Full-scale clamp | raw TEST=15, calibration=+2 | display=15 and OUT=15 | NOT TESTED |
| Mode return | INLINE→TAP | OUT goes to zero/non-connectable; calibration remains display configuration | NOT TESTED |

## 4. Instrument-network integrity diagnostics

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Healthy network | One or more unique-channel probes | integrity=OK | NOT TESTED |
| No probes | Instrument cable network with no probe | integrity=NO_PROBES | NOT TESTED |
| Duplicate channel | Two A probes | integrity=AMBIGUOUS, duplicateChannels/probes increase | NOT TESTED |
| Resolve duplicate | Remove one duplicate | integrity returns OK | NOT TESTED |
| Depth | Extend cable chain before probe | depth/cableDepth increase plausibly | NOT TESTED |
| Bounded scan | Very large test network near scan limit | No runaway traversal; truncated state is reported when limit is exceeded | NOT TESTED |

## 5. Oscilloscope capture quality

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Sample timebase | Observe status | samplePeriod=2t | NOT TESTED |
| Full coverage | A/B each have one valid probe | coverage approaches 100%, COMPLETE after warmup | NOT TESTED |
| Missing B | Remove B probe | B coverage falls/NO or partial capture is visible | NOT TESTED |
| Average | Feed constant 8 | avg≈8.00, meanStep≈0.00 | NOT TESTED |
| Activity | Alternate 0/15 | p2p=15 and meanStep becomes large | NOT TESTED |
| Period | Feed periodic pulse | period shown in samples and ticks | NOT TESTED |
| Cursor timebase | Move cursors | tick delta = sample delta × 2 | NOT TESTED |

## 6. Oscilloscope trigger save/reload

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Arm and trigger | Generate configured edge | Trigger enters TRIGGERED/HOLD sequence | NOT TESTED |
| Save during post-trigger | Save/reload while capture is triggered | Post-trigger progress resumes instead of restarting from zero | NOT TESTED |
| History persistence | Save/reload with capture history | Existing history/cursors/trigger config remain valid | NOT TESTED |

## 7. Logic Analyzer capture quality

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Sample timebase | Observe status | samplePeriod=1t | NOT TESTED |
| Full coverage | Unique A/B/C/D probes | valid channels report complete coverage | NOT TESTED |
| Ambiguous channel | Duplicate one channel | invalid/ambiguous samples visibly reduce coverage | NOT TESTED |
| Transition rate | Toggle channel every sample interval | edge and transition-rate diagnostics increase | NOT TESTED |
| Duty | Use periodic high/low signal | duty percentage remains consistent with valid samples | NOT TESTED |
| Cursor timebase | Move cursors | tick delta equals sample delta | NOT TESTED |

## 8. Logic Analyzer trigger save/reload

| Test | Procedure | Expected | Result |
| --- | --- | --- | --- |
| Trigger | Generate configured edge | Trigger fires normally | NOT TESTED |
| Save mid post-trigger | Save/reload before post-trigger capture finishes | Capture progress resumes instead of restarting | NOT TESTED |
| Counters | Reload after edge history | edge counters/history remain valid | NOT TESTED |

## 9. Alpha 1.0.4 topology regression

| Test | Expected | Result |
| --- | --- | --- |
| Analyzer TAP non-invasive | Still no redstone connection/output | NOT TESTED |
| Analyzer INLINE explicit ports | Only TEST and opposite OUT are signal-path ports | NOT TESTED |
| Direction-aware probe | Non-output face of directional source does not read unrelated strongest face | NOT TESTED |
| Dust-node accuracy | Attenuated dust reports its own POWER | NOT TESTED |
| Cylinder front feedback | Redstone position feedback only from FRONT | NOT TESTED |
| Cylinder pneumatic terminal | Pressure enters BACK but cannot pass through FRONT/sides | NOT TESTED |

## 10. Earlier system regression

| Test | Expected | Result |
| --- | --- | --- |
| PID Manual/Auto | Existing Alpha 1.0.3 behavior works | NOT TESTED |
| Servo Position/Velocity | Existing behavior works | NOT TESTED |
| Data Bus contention | Existing conflict diagnostics work | NOT TESTED |
| Radio diagnostics | Existing valid/collision/dropout diagnostics work | NOT TESTED |
| Pneumatic proportional valve | Existing proportional behavior works | NOT TESTED |
| Relief valve | Safety limit behavior works | NOT TESTED |
| Operations monitor | IOE metrics/classification work | NOT TESTED |
| Vanilla compatibility | No RSE redstone output exceeds 15 | NOT TESTED |
| World save/reload | No registry/state crash | NOT TESTED |

## Release gate

Alpha 1.0.5 can merge to `main` after branch CI passes all of:

1. Python verifier syntax;
2. repository verifier;
3. source/resource quality audit;
4. deterministic reference-model tests;
5. all historical Alpha regression verifiers;
6. Alpha 1.0.5 verifier;
7. Java 21 `compileJava`;
8. Gradle `test`;
9. clean Gradle build;
10. SHA-256 generation and verified artifact upload.

A public release candidate additionally requires no release-blocking `FAIL` in the critical `runClient` tests above.
