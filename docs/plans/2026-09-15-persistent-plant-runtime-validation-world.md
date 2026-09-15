# Persistent Plant Runtime + Validation World

## Goal

Close the remaining plant-level gap without replacing the existing Operations and Robotics primitives. Keep physical/process logic in the existing runtimes, and add one durable server-owned evidence layer that survives world reloads.

## Architecture

- `OperationPlantSavedData`: existing authoritative plant topology, buffers, workcell bindings, and logical WIP.
- `OperationPlantRuntimeSavedData`: durable job lifecycle and plant-wide evidence ledger.
- Existing Operations runtimes remain responsible for dispatch, queue, quality, maintenance, completion, and transport-demand decisions.
- Existing Robotics runtimes remain responsible for AMR mission execution, route planning, pickup/unload, safety, and material-flow decisions.
- `OperationRobotLogisticsRuntime`: narrow handoff adapter from `OperationTransportDemand` to `RobotMission`; it does not select routes or bypass robot safety.

## Persistent lifecycle

`RELEASED -> QUEUED -> ASSIGNED -> RUNNING -> QUALITY_HOLD -> TRANSPORT -> COMPLETED`

Allowed recovery/alternate transitions are explicit. Every accepted transition emits a durable event with a world game tick and relevant identity.

## Durable evidence

The ledger records:

- queue: released, enqueued, dispatched/assigned, started, completed;
- quality: inspected, good, rejected, rework requested/released;
- maintenance: due, started, fault/downtime, completed/recovered;
- delivery: due tick, completion tick, on-time/late result;
- logistics: transport requested, AMR assigned, pickup, movement/arrival, unload, downstream receipt;
- identity: job id plus output/mission/resource identifiers where applicable.

History is append-only for normal runtime use. UI views may expose bounded windows, but persistence is not the same as the existing bounded dashboard event strip.

## Validation contract

Add focused GameTests for:

1. job lifecycle ordering and persistence codec;
2. invalid lifecycle transitions fail closed;
3. queue history preserves timestamps;
4. quality/rework preserves job/output identity;
5. maintenance fault/recovery history;
6. delivery on-time and late classification;
7. AMR handoff preserves demand identity;
8. full normal factory evidence chain.

GameTests remain local/world-level verification. GitHub CI must not run the Minecraft GameTest server.

## Validation World

Provide a rebuildable validation factory rather than relying only on a fragile binary save:

- inbound buffer -> workcell A -> intermediate buffer -> AMR lane -> workcell B -> quality -> rework/good -> finished buffer;
- control room with scenario controls and visible PASS/FAIL evidence;
- scenario buttons for Normal, Congestion, Quality/Rework, Machine Fault/Maintenance, Late Delivery, and AMR Transfer;
- a GameTest Hall that points the player at the precise automated contracts.

A pre-generated world zip is a convenience artifact, not the source of truth. The builder is the maintainable source of truth.

## Verification policy

- Compile/static verification may run in CI.
- Minecraft GameTests are run locally by the player/developer in the validation environment.
- Do not claim world-level passing status unless a local GameTest run actually produced it.
