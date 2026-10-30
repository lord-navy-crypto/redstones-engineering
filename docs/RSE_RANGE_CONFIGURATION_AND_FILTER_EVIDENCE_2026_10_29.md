# RSE functional robustness: range configuration and source-aware filtering (2026-10-29)

## Actual scope

This patch upgrades `RangeSensorBlock` and `PrecisionFilterBlock`, plus every `RangeSensorMenu` configuration button. No block, registry ID, loot entry, model, recipe, save field, or BlockState property is added or removed. Range and slew-filter transfer mathematics remain unchanged.

### 1. Range Sensor: configuration transaction with safe reset

**Previous risk:** scan status, distance, and output were retained in live runtime data after changing range, detection mode, response curve, or facing. The HMI could temporarily present a result obtained under a different configuration as though it were a valid measurement.

**New:** all in-world Shift-click changes and Range Sensor HMI buttons route through `RangeSensorBlock.applyConfiguration`. Upon a real configuration change, server code:

1. validates the requested state belongs to the existing range sensor;
2. clears the old `range_sensor_scan` runtime entry;
3. writes the new state with `OUTPUT=0` to de-energize the old signal;
4. notifies the old and new sensing/output neighborhoods;
5. schedules an authoritative new scan at the next tick.

Until that new scan completes, the output snapshot reports `PortQuality.STALE` and `ScanStatus.UNINITIALIZED`. An unchanged parameter request does not mutate state or schedule unnecessary work. **This changes fail-safe behavior intentionally; old scan results are never valid measurements for a new configuration.**

The existing 3-integer runtime layout is preserved. `lastScan` additionally rejects implausible retained data (e.g. CLEAR with fewer scanned cells than configured range, TARGET with a mismatched distance vs coverage); it doesn't force-load chunks or perform active scans.

### 2. Precision Filter: truth-preserving source diagnostics

The input engineering port now uses read-only `RedstoneObservationSupport.observe` rather than marking every numerical 0 as a valid source. It distinguishes **NO_SIGNAL**, deliberately driven **VALID zero**, and **STALE** when chunk coverage is missing. Its existing 0..15 slew response, rate configuration, output scheduling, and physical redstone behavior remain unchanged. This is a measurement-quality correction, not a new filter equation.

### Regression coverage

`RseRangeReconfigurationGameTests` includes three in-game tests:
- range-mode change revokes a previously valid scan and output;
- sensing-axis rotation cannot reuse the former scan or energized output;
- slew-filter input readback distinguishes unconnected from valid zero without allocating runtime data.

All three are registered in `RseGameTestRegistration`; the source contract gate `tools/rse_range_config_safety_verify.py` is included in the standard CI.

**Important:** Gradle compilation and scripted source checks are not evidence the Minecraft GameTest runtime suite has executed. Also, direct external modification of BlockState outside the supported configuration entry points is not in this change's guarantee. No multiplayer/save-migration acceptance claim is made without running those scenarios.

### Current 46-candidate audit

The current audit script tracks **21 shared-helper users, 3 separate functional evidence upgrades**, and **22 candidates not yet refactored/enhanced**. Two of the 21 shared users (`range_sensor` and `precision_filter`) receive this additional robustness hardening; these two are tracked in their own CSV column **without double-counting them** as new completed candidates.

### Manual acceptance

1. Create a target in front of a Range Sensor and verify its output rises and `lastScan` reports TARGET.
2. Change range, response, detection mode or orientation from the HMI. Immediately observe output 0 with STALE/UNINITIALIZED, before the scheduled new reading.
3. Re-run after a server tick: output and scan status must describe **the new configuration**.
4. With the filter input disconnected, read its input port quality. Then connect a 0-strength Redstone reference source: input quality must become VALID even though its numerical value stays 0.
5. Verify actual vanilla/engineering redstone response and full original 0..15 slew behavior, inspect loaded/unloaded chunk boundaries, test multiplayer UI synchronization and open an old saved world.
