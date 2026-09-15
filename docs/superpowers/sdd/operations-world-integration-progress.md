# SDD ledger — plan: docs/superpowers/plans/2026-09-15-operations-world-integration.md

## Pre-flight rulings

| Scope | Producer / consumer | Finding |
| --- | --- | --- |
| Task 1 → Task 2 | world resource provider → existing blocks | Compatible; provider must remain evidence-only. |
| Task 2 → Task 4 | device evidence → Operations Monitor | Compatible; monitor remains observer-only. |
| Task 3 → Task 5/6 | machine resource evidence → workcell binding/controller | Compatible; explicit binding only. |
| Task 5 → Task 6 | persisted workcell binding → controller UI | Compatible; identities stay server-side. |
| Task 7 → Task 8 | persisted lot/WIP state → buffer block | Compatible; buffer runtime remains decision authority. |
| Task 9 | all prior tasks → cross-block verification | Compatible; verifier must execute in workflow rather than syntax-check only. |

Ruling: use repository CI as the executable test environment because the local container cannot resolve GitHub.

## Task 1 — COMPLETE

- RED commit `bd83c0c44dd1d8296f19e20e8052dc631b02ff6b`: executable verifier failed because provider/snapshot/resolver did not yet exist.
- Added `OperationWorldResourceProvider`, fail-closed `OperationWorldResourceSnapshot`, and explicit-position `OperationWorldResourceResolver`.
- Added numeric evidence map without adding scheduling/control authority.
- Added `rse_operations_world_integration_verify.py` to the dedicated Operations workflow so it is executed, not merely syntax-checked.

## Task 2 — COMPLETE

- RED verifier required existing controls to expose Operations evidence.
- Integrated `SequenceControllerBlock`, `AlarmProcessorBlock`, `WatchdogBlock`, `SafetyInterlockBlock`, and `FaultLatchBlock` through the provider contract.
- Preserved native sequence/alarm/watchdog/interlock/latch behavior and legacy verifier contracts.
- No IE-specific duplicate control/safety blocks created.

## Task 3 — COMPLETE

- RED verifier required one real existing machine adapter.
- Selected `ServoActuatorBlock` because it already owns authoritative position/command/velocity/error/brake evidence.
- Added stable `servo_positioning` capability evidence.
- Completion evidence is explicitly unavailable rather than inferred or fabricated.

## Verification after Tasks 1–3

Head `ffcc42e47c2741b8ecf312e61575b379c711fff8`:

- `RSE Operations Material Flow Verification` run #46: PASS.
- `RSE Build Verification` run #1339: PASS.
- verifier syntax: PASS.
- full static/reference verification: PASS.
- Java 21 compile: PASS.
- Gradle tests: PASS.
- ordinary-PR Minecraft GameTests: SKIPPED by repository policy.
- clean build: PASS.
- SHA-256 checksums: PASS.
- verified artifact upload: PASS.

## Task 4 — NEXT

Deepen the existing Operations Monitor with a truthful plant-level projection. Before implementation, identify which plant-level collections currently have authoritative runtime ownership; missing collections must surface as incomplete coverage rather than being synthesized as empty/healthy data.
