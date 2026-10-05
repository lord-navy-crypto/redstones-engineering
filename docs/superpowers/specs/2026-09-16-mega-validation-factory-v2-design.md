# RSE Mega Validation Factory v2 — Design Specification

## Purpose

Build a second, much larger acceptance facility alongside the existing `/rsevalidation plant` fixture. The v2 facility is a 40-DUT, eight-cell industrial commissioning factory that validates components individually, validates each five-device subsystem, validates cross-cell integration, exercises degraded/faulted operation, and produces a final plant-wide verdict with persistent evidence.

The existing v1.1 plant remains unchanged as a fast commissioning fixture. The new facility uses `/rsevalidation mega ...` commands and separate saved data.

## Core acceptance model

The factory contains exactly 40 primary DUT stations, grouped as eight cells of five stations each:

| Cell | Stations | Function |
| --- | --- | --- |
| A | D01–D05 | Analog metrology |
| B | D06–D10 | Timing and waveform |
| C | D11–D15 | Precision signal |
| D | D16–D20 | Digital data |
| E | D21–D25 | Robust communications |
| F | D26–D30 | Optical plant link |
| G | D31–D35 | Pneumatic process |
| H | D36–D40 | Closed-loop control and safety |

Primary DUT list:

1. D01 Redstone Reference Source
2. D02 Signal Probe
3. D03 Signal Conditioner
4. D04 Precision Filter
5. D05 Signal Analyzer
6. D06 Quartz Lab Oscillator
7. D07 Quartz Clock Divider
8. D08 Edge Detector
9. D09 Pulse Shaper
10. D10 PWM Controller
11. D11 Lapis Noise Source
12. D12 Lapis Low-Pass Filter
13. D13 Lapis Precision Meter
14. D14 Quartz Triggered Lapis Sampler
15. D15 Lapis-to-Redstone Quantizer
16. D16 Redstone Byte Encoder
17. D17 Eight-Bit Data Bus
18. D18 Serializer
19. D19 Serial Data Line
20. D20 Deserializer
21. D21 Differential Driver
22. D22 Differential Data Pair
23. D23 Digital Regenerator
24. D24 Differential Receiver
25. D25 Watchdog
26. D26 Optical Emitter
27. D27 Optical Fiber
28. D28 Optical Splitter
29. D29 Optical Channel Filter
30. D30 Optical Receiver
31. D31 Air Compressor
32. D32 Air Reservoir
33. D33 Pressure Regulator
34. D34 Pneumatic Proportional Valve
35. D35 Pneumatic Cylinder
36. D36 PID Controller
37. D37 Servo Actuator
38. D38 Servo Position Sensor
39. D39 Safety Interlock
40. D40 Alarm Processor

Auxiliary instrumentation such as analog indicators, oscilloscopes, logic analyzers, flow meters, fault injectors, redundant voters, fault latches, operations monitors, reference sources, status lamps, converters, and cables do not count toward the 40-DUT total.

## Physical architecture

The facility is a large industrial campus generated from modular structures. It consists of:

- a north control hall with a 40-station status wall;
- a central three-wide service spine;
- eight 31x7x25 commissioning cells arranged four across by two deep;
- east/west utility cross-corridors between rows;
- overhead frames, lighting, maintenance lanes, safety rails, cell signs, station signs, and colored status plinths;
- a plant-wide analog acceptance backbone that physically traverses A→B→C→D→E→F→G→H through buffered redstone/engineering signal segments;
- cell-local domain rigs for timing, precision signal, data, communications, optical, pneumatic, and control devices.

Each station has a visible identifier sign (`D01` … `D40`), DUT name, test role, and three-state WAIT/PASS/FAIL indicator with yellow/lime/red bases. Each cell also has a local three-state verdict panel. The control hall contains eight cell verdicts, a 40-station matrix, a master verdict panel, a master RETEST button, and diagnostic usage signs.

## Diagnostic hierarchy

Verdicts form a strict hierarchy:

1. **Station verdict** — each of D01–D40 reports WAIT/PASS/FAIL and detail text.
2. **Cell verdict** — each A–H passes only when all five required stations pass and the local integrated condition passes.
3. **Process-train verdict** — validates continuity and end-to-end plant token propagation across all eight cells.
4. **Reliability/safety verdict** — validates injected degradation, fault observation, safety trip, safe state, acknowledgement/reset, and recovery.
5. **Master acceptance** — passes only after all required phases have completed without unresolved failure.

A station may recover to PASS while retaining historical evidence that it previously failed during a required fault scenario.

## Runtime phases

The automatic runtime progresses through:

1. STRUCTURE_PRECHECK
2. STATION_BIST
3. CELL_ACCEPTANCE
4. CHAIN_CONTINUITY
5. NOMINAL_STARTUP
6. TIMING_TEST
7. NOISE_TEST
8. SATURATION_TEST
9. DATA_INTEGRITY_TEST
10. COMM_DEGRADATION
11. PROCESS_LOAD
12. SENSOR_FAULT
13. ACTUATOR_FAULT
14. REDUNDANCY_TEST
15. INTERLOCK_TRIP
16. SAFE_STATE
17. ACK_RESET
18. RECOVERY
19. ENDURANCE_RUN
20. FINAL_ACCEPTANCE

The runtime may drive validation-owned stimuli and fixture sources in v2, but it must never directly write a DUT output to force a pass. DUT evidence is read from actual world block state, engineering snapshots, block runtime APIs, sampling/history APIs, and physical status observations.

## Persistent evidence

`RseMegaValidationSavedData` stores:

- plant origin;
- current phase and phase start tick;
- retest button edge state;
- per-station current verdict/detail;
- per-station sticky `everFailed` and `everPassed` flags;
- per-cell current verdict/detail;
- completed phase bitset;
- first failure phase/detail for each station;
- endurance start tick and accumulated healthy endurance ticks.

Retest rebuilds the v2 structures and resets acceptance history for the new run.

## Command surface

Add developer/debug commands while keeping physical controls primary:

- `/rsevalidation mega place`
- `/rsevalidation mega status`
- `/rsevalidation mega station <1..40>`
- `/rsevalidation mega cell <A..H>`
- `/rsevalidation mega report`
- `/rsevalidation mega retest`

`status` shows phase plus A–H summaries. `station` shows current and historical evidence for one DUT. `cell` shows all five station verdicts plus integrated-cell detail. `report` prints plant-level completion and unresolved failures.

## Generator architecture

Create `tools/rse_mega_factory.py` as the single source of truth for physical layout and station metadata. It exposes:

- `MEGA_STRUCTURE_ORDER`
- `MEGA_MODULE_OFFSETS`
- `MEGA_MODULE_SIZES`
- `MEGA_STATIONS`
- `MEGA_CELL_STATIONS`
- `build_all()`

The standard `python3 tools/rse_validation_factory.py generate` flow generates v1 structures and v2 structures. Mega resources are written under `data/redstoneengineering/structure/validation/mega/`.

## Station metadata contract

Each station record contains:

- integer station number 1..40;
- cell A..H;
- DUT block id;
- human-readable name;
- module id;
- local DUT coordinate;
- local WAIT/PASS/FAIL panel power coordinates;
- station sign coordinate;
- role string.

Tests enforce unique station numbers, exactly five stations per cell, unique coordinates within a module, and all referenced block ids being present in `RedstoneEngineering.java`.

## Evaluation strategy

Not every domain exposes the same runtime API. v2 therefore uses layered evaluators:

- **Presence/identity evidence** for every station during STRUCTURE_PRECHECK.
- **Live-state evidence** for every station during STATION_BIST using blockstate/property observations plus engineering snapshot/runtime APIs where available.
- **Domain-specific integrated evidence** for each cell during CELL_ACCEPTANCE and later phases.
- **End-to-end backbone evidence** independent of local cell tests so a cell cannot pass only by self-contained fixtures.

Where a station lacks a stable public runtime observation API, v2 must still require the exact expected block identity and at least one observable state/neighbor/topology condition; it may not be auto-passed solely because its metadata exists.

## Completion criteria

FINAL_ACCEPTANCE requires all of the following:

- 40/40 station BISTs have passed at least once in the current run;
- 8/8 cells have passed integrated acceptance;
- plant backbone continuity has passed;
- timing/noise/saturation/data/communications/process-load phases completed;
- required fault phases observed their intended failure/degraded condition;
- interlock/safe-state/ack-reset/recovery phases completed;
- no current unresolved station or cell failure;
- ENDURANCE_RUN accumulated the required healthy window;
- generated-structure, registry, command, persistence, and CI contract tests pass.

## Non-goals for v2

- Do not delete or repurpose the existing 19 selftests or the v1.1 A–F plant.
- Do not make the v2 runtime depend on manual commands for phase progression.
- Do not claim Minecraft in-world acceptance is proven by CI alone; PR CI validates code/contracts/build, while final world evidence comes from the generated factory run.
- Do not require all cross-domain conversion to be physically vanilla-redstone-only in v2; fixture-driven stimuli remain allowed, with a future v3 reserved for fully physical sequencing.