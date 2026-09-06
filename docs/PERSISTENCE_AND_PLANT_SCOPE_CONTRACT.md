# RSE Plant Scope & Persistence Contract

## Why this exists

RSE now has system-wide event evidence, first-out analysis, operations assessment, alarms, interlocks, sequence control, topology diagnostics, commissioning evidence, and fault injection. Two boundaries must stay explicit:

1. **Plant scope:** a dashboard for one machine cell must not mix unrelated incidents from another plant in the same dimension.
2. **Persistence:** making every runtime value durable is not automatically safer or more truthful.

## Plant event scope

`SystemEventTimeline` remains bounded level-wide evidence storage (`256` records per loaded level). Ordinary operations views use `SystemEventScope` centered on an Operations Monitor with a default radius of `32` blocks and a hard maximum of `128` blocks.

This gives two valid views:

- **Global expert evidence:** `SystemEventTimeline.snapshot(level)` and `FirstOutAnalysis.latest(level)`.
- **Plant operations evidence:** `SystemEventTimeline.within(level, scope)` and `FirstOutAnalysis.latestWithin(level, scope)`.

The default `OperationsDashboardSnapshot.inspect(level, monitorPos)` is plant-scoped. This prevents a remote alarm in factory B from becoming the first-out incident shown on factory A's dashboard.

Spatial proximity is only a scope boundary. It is **not** a claim that nearby events are causally related.

## Current persistence matrix

| State | Current storage | Chunk unload, same server | Server restart | Block removal | Direction |
|---|---|---:|---:|---:|---|
| Sequence current step / edge state | `RuntimeIntStore` | retained | reset | cleared | Keep transient; fail-to-idle is safer than automatic motion resume |
| Alarm latch / acknowledgement runtime | `RuntimeIntStore` | retained | reset | cleared | Consider explicit durable incident recorder |
| Operations rolling metrics | `RuntimeIntStore` | retained | reset | cleared | Keep rolling metrics transient; later split lifetime metrics |
| Fault Injector mode | BlockState | retained | retained | removed with block | Keep durable bounded configuration |
| Fault Injector diagnostic runtime | `RuntimeIntStore` | retained | reset | cleared | Keep transient |
| System event timeline | level runtime | retained | reset | history remains if source block is removed | Candidate for bounded durable recorder / SavedData |
| PID tuning preset | BlockState | retained | retained | removed with block | Keep durable bounded configuration |
| PID integrator / derivative / transfer runtime | `RuntimeIntStore` | retained | reset | cleared | Keep transient to avoid stale hidden controller energy |
| Explicit commissioning evidence | `AcceptanceEvidenceStore` | retained | reset | cleared with PID | Candidate for explicit durable recorder |

`RuntimePersistenceContract` is the machine-readable source for these current semantics.

## Safety principle

Persistence is not a universal upgrade.

A server restart must not silently resume a partially completed actuator sequence or restore stale PID integral state without an explicit restart policy. Conversely, operator-created evidence such as acknowledged alarms and captured commissioning records has a stronger case for durable retention.

Future durable event storage should therefore be introduced as a **bounded, explicit recorder** with migration/version semantics rather than by placing high-cardinality event data in BlockState.

## Next lifecycle tests

The next reliability milestone should cover actual save/reload behavior where the NeoForge GameTest harness supports it, plus:

- source block removal while event history remains inspectable;
- chunk unload/reload during active monitoring;
- sequence restart policy after world reload;
- alarm/event recorder migration if durable SavedData is introduced;
- multiple independent plant scopes in one dimension;
- bounded retention under long-running event generation.
