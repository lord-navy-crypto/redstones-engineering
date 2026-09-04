# RSE Functional Correctness Matrix

This document separates **build success** from **gameplay correctness**. A registered block, valid JSON model, or successful Java compile does not by itself prove that a device behaves correctly in a Minecraft world.

## Evidence levels

| Level | Meaning |
| --- | --- |
| **A — In-world end-to-end** | Real blocks are placed in a GameTest world and the full input → processing → output path is asserted after Minecraft ticks/neighbor updates. |
| **B — In-world contract** | Real blocks are placed or inspected in a GameTest world and topology, direction, domain, safety, or state-transition behavior is asserted. |
| **C — Deterministic model** | The engineering algorithm is executable and regression-tested, but the complete placed-block path is not yet exercised. |
| **D — Static only** | Registration/source/resource invariants are checked, but runtime behavior still needs executable coverage. |

A simple passive component may be adequately covered by a shared Level A/B contract when it delegates all behavior to a proven common implementation. A stateful controller, converter, network device, sensor, actuator, or safety component should normally earn dedicated runtime evidence.

## Current high-value evidence

| System / component | Current evidence | What is actually proven |
| --- | --- | --- |
| Redstone Reference Source | **A** | FRONT-only source participates in a live world processing chain. |
| Signal Conditioner | **A** | GAIN, OFFSET, CLAMP, THRESHOLD, DEADBAND, directionality, 0..15 saturation, scheduled ticks, downstream propagation, and source-removal clearing. |
| Analog Process Indicator | **A** | BACK-only world input, live downstream observation, side-input rejection through existing topology tests, and stale-value clearing. |
| Insulated redstone cable / junction | **B** | Placement-order connectivity and REDSTONE-vs-COPPER domain rejection. Full long-run signal propagation remains a separate target. |
| Redstone cable terminal | **B** | Input/output mode changes the physical Engineering Port direction correctly. |
| Redstone ↔ Lapis converters | **B/C** | Explicit domain/port direction is proven; conversion math/runtime implementation is inspected and statically guarded. A complete placed-world round trip remains a target. |
| Copper voltage source / wire / series resistor / resistive load | **A** | Real source → wire → resistor → wire → load propagation and attenuation. |
| Copper fuse | **A** | Real overload trip and protected-output cutoff. |
| Copper axial processors | **B** | BACK input / FRONT output / no SIDE port contract. |
| Metrology tracker / measurement quality | **C** | Repeatability, bias, drift, saturation, staleness, uncertainty proxy, and quality projection algorithms. More real sensor-chain GameTests are still needed. |
| PID commissioning projection | **C** | Runtime metrics → commissioning status/score and disturbed-run comparison. A complete placed-world controlled process remains a future Level A target. |
| Fault injection model | **C** | Deterministic bounded noise/bias/dropout/saturation/latency primitives. |
| Engineering acceptance / evidence history | **B/C** | Acceptance/evidence ownership, bounded history, comparison, and observer boundaries are executable; durable persistence is intentionally not part of Alpha 1.0.20. |
| Mechatronics visual projection | **C** | Rendering projection is normalized/read-only; visual animation does not own physics. |

## Release rule

RSE does **not** use block count as a correctness metric. Before a subsystem is treated as release-critical gameplay, its highest-risk behavior should have executable evidence appropriate to that subsystem:

1. **Transmission:** connectivity, propagation, removal/de-energization, unloaded-chunk behavior.
2. **Measurement:** real sampling cadence, range, quality states, stale/saturation behavior.
3. **Conditioning/conversion:** exact transfer function, direction, domain boundary, clamping.
4. **Control:** controller mode, bounded output, reset/save behavior, commissioning response.
5. **Actuation:** command → authoritative physical state → feedback path.
6. **Safety/reliability:** fault trigger, latch/trip behavior, reset path, protected-output behavior.
7. **Operations/diagnostics:** read-only observation must never become simulation authority.

## Next correctness targets

The next runtime-hardening passes should prioritize:

- Redstone → Lapis → Redstone placed-world round trip;
- representative real sensor → conditioner → controller/indicator chains;
- PID Controller placed-world closed-loop behavior rather than runtime-array-only evidence;
- servo and pneumatic command → motion/pressure → sensor feedback loops;
- serial/data-bus/radio end-to-end transmission, contention, disconnect and recovery;
- save/reload and block break/replacement for stateful devices;
- unloaded-chunk/no-force-load behavior for every network family;
- 100/500/1000-node performance and dirty-topology rebuild budgets.

The goal is not to write 122 shallow tests. The goal is to prove the shared foundations once, then give dedicated Level A/B tests to every component whose behavior is stateful, safety-critical, cross-domain, or materially different from its shared base implementation.
