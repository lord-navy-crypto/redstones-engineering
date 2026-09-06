# RSE Alpha 2 Finishing Gate

This milestone is the end of feature expansion for the second Alpha build. It combines the final Phase 5 Vanilla Engineering Overlay fixes, core observability closure, presentation consistency, and lifecycle/release hardening into one release gate.

## FEATURE FREEZE

Alpha 2 is ready to freeze only when the repository proves all of the following in CI:

- 122/122 historical core blocks remain registered and reconciled to the deep-audit ledger.
- 122/122 core blocks have required resources, an engineering port contract, an explicit engineering domain, direct GameTest evidence, direct verifier evidence, and an engineering snapshot/metrology surface.
- Every stateful core block has an explicit lifecycle cleanup path detected by the deep audit.
- The five systems-extension blocks remain exactly: Sequence Controller, Safety Interlock, Fault Injector, Alarm Processor, and Topology Debugger.
- The architecture therefore remains 122 core + 5 systems-extension = 127 blocks; finishing work does not add content.
- Instrument Cable and Shielded Instrument Cable expose read-only local physical-link quality snapshots without scanning the entire instrument network from the HUD.
- Oscilloscope, Logic Analyzer, Signal Analyzer, PID Controller, and Topology Debugger use the shared synchronized chart presentation layer; client screens do not scan or mutate the world.
- Vanilla Engineering Overlay remains diagnostics-only under **VANILLA BEHAVIOR IMMUTABILITY**.
- Java 21 compilation, Gradle tests, the complete required Minecraft GameTest suite, clean build, SHA-256 generation, and artifact upload all pass.

## Authority and presentation contract

The finishing gate preserves the established ownership chain:

`server simulation -> bounded server observation/history -> synchronized menu snapshot -> client presentation`

Client UI is presentation-only. It must not become a second solver, infer hidden physics from the client world, or mutate device/vanilla behavior.

For vanilla redstone, the overlay may measure, observe, explain, visualize, diagnose, profile, compare, and commission. It does not change repeater timing, comparator behavior, observer pulses, dust propagation, quasi-connectivity, or update order.

## Observability closure

Instrument cable snapshots deliberately report physical link health rather than inventing one scalar value for a four-channel measurement bus. A selected connected face is represented as a local link observation:

- `VALID`: the loaded neighboring face still exposes a compatible instrument-bus endpoint.
- `STALE`: the neighboring chunk is not currently available to the observation.
- `TOPOLOGY_ERROR`: retained cable connection state no longer agrees with the loaded neighboring endpoint contract.

Logical probe-channel values remain owned by instruments and `InstrumentNetwork`; the cable HUD does not run a full graph scan per displayed face.

## Release gate

The CI finishing verifier consumes the existing 122-block audit JSON and promotes the release-critical closure metrics to hard failures. The existing 127-block systems verifier remains authoritative for aggregate architecture closure. The normal pipeline then continues through Java compilation, Gradle tests, Minecraft GameTests, clean build, checksum generation, and verified artifact upload.

A green finishing gate means the feature set is frozen. It does **not** mean verification is finished.

## SECOND-PASS VERIFICATION

Immediately after feature freeze, work switches to runtime second-pass verification rather than adding features. The next stage audits all 127 blocks for registration/resources, placement, physical I/O, port contracts, lifecycle cleanup, observability, save/load behavior, interoperability, and applicable UI behavior, then exercises representative end-to-end chains and destructive/stress scenarios.

Any issue found in the second pass is treated as a bug or hardening task. New blocks or new gameplay systems remain out of scope unless a blocker cannot be resolved without a narrowly scoped compatibility fix.
