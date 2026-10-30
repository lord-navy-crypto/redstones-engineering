# RSE Phase 4 — trustworthy measurements across missing-chunk boundaries

2026-10-29 | NeoForge 1.21.1 | no registry, world-save, model, recipe, port-direction or numerical transfer formula changes

## The defects and concrete repairs

**Signal Analyzer (`signal_analyzer`)**

Previously, `uiSnapshot` used `RuntimeIntStore.get`: opening the HMI could create an empty sampling runtime record even before the server sampler ran. Also, an uncovered target could be interpreted as a zero measurement, resulting in fabricated changes to the rolling window and quality `NO_SIGNAL` instead of `STALE`.

- `uiSnapshot` now uses `RuntimeIntStore.peek` and a local non-persisted zero-filled array for an uninitialized history. Reading it never starts a sample or creates server runtime data.
- `sampleTarget` and `measurementPresent` check `hasChunkAt` before reading the target.
- A missing target chunk produces `PortQuality.STALE` on the input/output measurement; it never enters the 16-point rolling history as a synthetic zero.
- INLINE mode still outputs a safe zero when its input is uncovered. This intentionally changes only the missing-coverage behavior, not the loaded-domain redstone transfer or display-only calibration.

**Copper Circuit Meter (`copper_circuit_meter`)**

The observation, `sampledVoltage` and circuit diagnostics previously accessed the adjacent target even if its chunk was not present.

- They now fail closed with `PortQuality.STALE` and no physical voltage/power claim when the target chunk is missing.
- A retained historical reading is not represented as a live voltage on an uncovered port.
- Sampling, commissioning incident transitions, six-face routing and normal loaded measurements continue to use the original electrical solver and metrology data.

**Optical Power Meter (`optical_power_meter`)**

The meter now guards its sampling aperture with `level.hasChunkAt(target)` before querying `DomainNetwork.sampleOptical`. Missing coverage is STALE, not dark/NO_SIGNAL. Loaded targets retain the authoritative optical intensity/channel plus the independent quality classification.

## Acceptance and limitations

Two newly registered NeoForge GameTests exercise: (1) analyzer UI readout does not allocate sampling history; (2) empty optical/copper measurement targets remain NO_SIGNAL, not false valid zero. The static gate `tools/rse_meter_observer_neutrality_verify.py` also checks explicit unloaded-source fail-closed paths, with no extra chunk reads.

**Important:** successful compilation/static checking does not mean these Minecraft runtime tests have been executed. Actual client/server, chunk-unload boundary cases, multiplayer synchronization and existing save-world acceptance are separate outstanding work.

## Candidate audit status

All 130 registered blocks remain intact. Of 46 shared-code review candidates, 21 already use shared helper code and 3 have separately recorded functional evidence upgrades. These phase-4 changes additionally harden three previously untouched meters (Signal Analyzer, Copper Circuit Meter, Optical Power Meter), so **19 candidates have not yet received an extraction or specific evidence/safety enhancement**. Existing range sensor and precision filter safety work remains in the per-block audit. This is a progress metric, not proof of behavioral equivalence or full runtime acceptance.
