# RSE 46 shared-implementation candidates — first lossless refactor (2026-10-28)

## Scope and precise completion claim

This is **phase 1** of the 46-candidate review, not a claim that all 46 have been converted or enhanced. The full inventory has **130 registered blocks**, including **46** potential code-sharing candidates in **15** overlapping engineering families.

**9/46 block implementations now reuse 3 focused support helpers; 37/46 remain on their previously reviewed, distinct implementations.** All 130 registry IDs and the 14-block commissioning enhancements are retained. The CI hard gate `tools/rse_46_shared_code_rollout_verify.py` writes a row-by-row report to `.rse-audit/rse-46-shared-code-rollout.csv` and fails if a checked source stops referring to its shared helper or loses distinguishing physics tokens.

## Concrete refactor with unchanged device identities

| Family | 9 changed block IDs | Shared code extracted | Physics that must NOT be merged |
| --- | --- | --- | --- |
| Optical series | `optical_attenuator`, `optical_channel_filter` | `OpticalSeriesSupport`: observed port snapshot, route/parameter change detection, former optical output invalidation, cleanup and recomputation | attenuation law (0..8) versus channel selection plus one-step insertion loss; input/output routing and optical source-quality |
| Pneumatic valves | `pneumatic_valve`, `pneumatic_check_valve`, `pneumatic_proportional_valve`, `pneumatic_relief_valve`, `pressure_regulator` | `PneumaticValveSupport`: observation-preserving pressure snapshot (0..100), network recompute and carrier cleanup | bidirectional isolation; one-way check; Redstone opening command and animated entity; overpressure release and diagnostic event history; regulated setpoint and distinct recompute cadence |
| Mechanical/hydro excitation | `mechanical_exciter`, `hydroacoustic_exciter` | `ExciterDriveSupport`: DOWN-side Redstone observation, source-valid zero and output-quality mapping | separate mechanical-vibration and hydroacoustic network propagation, runtime keys and frequency update |

**Source footprint:** comparison against the previous source snapshot for these 9 block classes: 60,409 bytes before; 54,583 bytes after the helper extraction (before import cleanup). Three helpers total 5,762 bytes. After unused import cleanup, **all 12 files total 60,345 bytes**: the measured aggregate is only **64 bytes smaller** than the original 9 files. This is a real deduplication / maintainability improvement, **not** a substantial archive-size or performance win. The source comparison does not show Java bytecode or Minecraft server tick acceleration.

## Remaining 37/46: review pathway

These groups have different sources, media or correctness constraints, so **do not** delete or merge visible blocks simply because names look alike.

- **3 Amethyst-frequency:** reuse frequency/quality math only after same-input response curves agree. A resonator source is not a filter.
- **3 filtering:** investigate filter coefficient bounds and first-sample handling; preserve discrete slew versus Lapis low-pass versus programmable conditioner.
- **2 range:** compare scan aperture/chunk behavior, but preserve meter quantization and media.
- **2 sample-and-hold:** compare edge/hold logic with no-signal and stale data; preserve Quartz-triggered Lapis versus Redstone signal contracts.
- **2 Quartz clocks:** share validated period calculations and snapshot formatting; lab clock must retain jitter and empirical timing history.
- **3 metering:** centralize quality presentation and immutable history, not calibrated numerical meaning.
- **4 optical transport:** wired fiber terminals and free-space LOS are not the same topology.
- **3 medium junctions:** common port rendering is fine; electrical/optical/other bus bridging must remain explicit.
- **5 failure-diagnostics devices:** reusable event metadata is possible, while watchdog, latch, alarm and permissive safety behavior stay distinct.
- **6 digital transport:** distinct differential driver/receiver/transmission-line/serial framing roles should use common bounded codecs only.
- **2 instrument-cable variants:** shielded version already inherits the base cable; preserve meaningful interference/shielding differences rather than adding a second abstraction.
- **2 optical-sensor conversions:** share read-only observation where identical; preserve calibration and domain conversion.

All 37 are **NOT_REFACTORED** in the CSV rather than falsely marked complete. They are not defective; for many, the correct decision is **no further merging**.

## Hard acceptance conditions

1. Retain registry identifiers, BlockState properties, codecs, registered items, creative catalog behavior, recipes/loot and existing save data.
2. Preserve each domain, port face, transmission direction, propagation path, information-quality semantics, tick timing and safe removal behavior.
3. Keep distinct physics in the device source; helpers must not quietly perform solver work when an observer inspects data.
4. Run ordinary CI, compile, unit tests and (separately) real-client GameTests, multiplayer network verification and old-world save loads. **Static checks are not in-game evidence.**
5. Before any public block merge, demonstrate full behavioral equivalence and a versioned world-save compatibility migration. No public block merge is authorized by this first phase.

## Next priority after phase 1

For each candidate, measure the **actual duplicate code**, identify a shared pure function if it exists, then extract only the minimum common computation. Prioritize the Quartz clock and sample/hold groups if real transfer semantics compare cleanly; defer device merges with dissimilar media or safety roles. Make old/new numerical examples, status and tick timing part of regression tests.
