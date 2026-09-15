# Operations World Integration Design

## Purpose

RSE now has substantial Industrial Operations runtime semantics—jobs, bounded queues, dispatch, routing/precedence, material allocation, workcell capacity, setup/changeover, quality, rework, maintenance, reliability, robotics handoff, and plant-level decision support. Most of those mechanisms are currently runtime contracts rather than first-class world interactions.

This design makes those mechanisms useful to players by integrating them into existing blocks whenever the block already owns the correct responsibility, and introducing a new block only when no existing block has the right semantic role.

## Design Rule: Reuse Before New Blocks

A mechanism MUST be integrated into an existing block when that block already represents the same engineering responsibility. A new block is justified only when adding the mechanism to an existing block would create conflicting authority or misleading player semantics.

Examples:

- `OperationsMonitorBlock` remains the plant observer and receives the new plant-level Operations view.
- `SequenceControllerBlock` remains a local four-step signal sequencer. It may contribute cycle/step evidence but MUST NOT become the production scheduler.
- `AlarmProcessorBlock`, `FaultLatchBlock`, `SafetyInterlockBlock`, and `WatchdogBlock` remain their existing safety/reliability devices and become evidence sources for Operations resource health. RSE MUST NOT create duplicate IE-specific alarm/watchdog blocks.
- Existing mechatronic, pneumatic, control, and other machine blocks are adapted as Operations resources when they can expose useful run/fault/completion/process evidence. They are not replaced by parallel “IE machines.”

## Existing Block Integration

### Operations Monitor

`OperationsMonitorBlock` already owns observer-only Industrial Operations instrumentation and opens `OperationsMonitorUi`. It remains observer-only.

Its server menu/screen will be extended to expose the plant-level view already available in the Operations diagnostics layer:

- throughput / current WIP and queue pressure
- dominant finite-capacity constraint and constrained workcell count
- quality coverage, FPY, reject rate, rework rate
- reliability coverage, observed availability, failure/repair observations
- due-date exposure and overdue unfinished work
- evidence coverage / incomplete evidence warnings

The Monitor MUST NOT create jobs, change dispatch policy, initiate changeover, admit work, start maintenance, move material, or command robots.

### Existing Safety and Reliability Blocks

Existing engineering devices become explicit evidence contributors:

- `AlarmProcessorBlock`: latched alarm / severity / acknowledge state can contribute resource fault evidence.
- `WatchdogBlock`: heartbeat timeout can contribute resource-health evidence.
- `SafetyInterlockBlock` and `FaultLatchBlock`: safety/fault state can make a resource unavailable or faulted through an adapter.
- `SequenceControllerBlock`: local sequence step and completed-cycle evidence can contribute processing/cycle evidence, but never global job dispatch authority.

Adapters MUST read the authoritative existing block state/runtime. They MUST NOT duplicate or overwrite the source device logic.

### Existing Machines

Operations resource integration uses an adapter/provider contract rather than hard-coding each block into the scheduler. A machine integration describes, where supported:

- stable resource identity
- process capability identifiers
- current availability / running state
- completion evidence source
- fault/safety evidence
- setup/changeover eligibility when applicable
- maintenance eligibility when applicable

Missing evidence remains missing; an adapter may not fabricate a healthy, idle, or completed state.

## New Block: Workcell Controller

No existing block has the semantic responsibility of grouping production resources into one workcell and coordinating Operations-level setup/capacity/admission. A dedicated `WorkcellControllerBlock` is therefore justified.

The controller is a world-facing bridge to existing authoritative Operations runtimes, not a new scheduler.

Player-visible responsibilities:

- bind/unbind nearby or explicitly selected Operations-capable resources
- assign a stable workcell identity
- show resource membership and process capabilities
- show active assignments and local queued work
- request setup/changeover through `OperationChangeoverRuntime`
- show maintenance holds and resource faults
- expose finite-capacity status and admission result
- show why a job is WAIT / SAFE_STOP / FAULT / eligible

Authority boundaries:

- job/resource ranking remains `OperationDispatchRuntime`
- precedence remains `OperationRoutedDispatchRuntime`
- setup remains `OperationChangeoverRuntime` / setup assessment
- maintenance remains `OperationMaintenanceRuntime`
- finite-capacity admission remains the existing capacity admission runtime
- bottleneck metrics remain observer-only

The controller never treats GUI state as simulation truth.

## New Block: Industrial Buffer

No existing generic world block currently owns lot identity, finite WIP capacity, quality-cleared material receipt, atomic allocation, and downstream release. A dedicated `IndustrialBufferBlock` is justified.

Player-visible responsibilities:

- explicit finite capacity
- list material lots by output/lot identity and unit count
- distinguish accepted quality-cleared lots from unresolved/rework/reject material
- accept valid downstream receipts
- provide the authoritative world snapshot for `OperationBufferSnapshot`
- perform allocation only through `OperationBufferRuntime` / `OperationMaterialReleaseRuntime`
- expose FULL / AVAILABLE / EVIDENCE_INVALID diagnostics

Atomicity requirement:

Material must never be removed from the buffer unless downstream admission succeeds in the same authoritative decision. Queue-full or invalid downstream admission keeps the buffer unchanged.

The first implementation should prefer RSE’s existing Operations lot semantics over inventing an item-only model. NeoForge item capability integration can be layered on when physical item interoperability is required, but item stacks must not erase lot/output/job identity.

## Deferred New Blocks

The following concepts are useful but are not created in the first integration wave. Each is reconsidered only after attempting reuse of existing blocks/UI:

- Production Controller
- Quality Inspection Station
- AMR Dock / Transfer Station
- Maintenance Terminal

Likely direction:

- Quality may initially be exposed through an existing compatible instrumentation/HMI path before a dedicated station is justified.
- Maintenance actions may initially live in the Workcell Controller for bound resources; a separate Maintenance Terminal is warranted only if plant-wide maintenance workflows outgrow it.
- AMR Dock likely remains a strong candidate for a new block because explicit physical docking/reservation/transfer has no current equivalent.
- Production job creation may initially be integrated into a Workcell/Operations control UI if it can be done without turning the observer-only Operations Monitor into a controller.

## World State and Persistence

High-cardinality Operations data MUST NOT be encoded into Minecraft block-state properties.

Persistent Operations world state should use a server-authoritative store keyed by stable identity and world location, with immutable runtime snapshots passed into existing Operations logic. The implementation should prefer an RSE/NeoForge persistence mechanism appropriate for server-owned plant state (for example SavedData or an existing RSE persistent store) rather than client-local state.

The UI is a projection of server truth. Any operator action is validated server-side before changing authoritative Operations state.

## Engineering Ports and Redstone

The vanilla 0–15 boundary remains intact.

Engineering Ports may expose simple world-facing signals such as:

- RUNNING / ACTIVE
- COMPLETE pulse
- QUEUE / WIP pressure
- PERMIT / HOLD
- FAULT / MAINTENANCE HOLD
- BUFFER pressure/fullness

High-cardinality data such as job IDs, output IDs, resource IDs, routes, or lot identities MUST NOT be encoded into analog redstone values.

## Player Interaction Model

The intended world interaction becomes:

1. Existing machines remain the physical process equipment.
2. A Workcell Controller binds those machines and exposes their Operations eligibility/setup/capacity.
3. An Industrial Buffer holds explicit material lots/WIP before or after a workcell.
4. The existing Operations Monitor observes the whole plant and presents the plant-level decision-support view.
5. Existing Alarm/Watchdog/Safety blocks feed health/fault evidence into the same resource model.
6. Robotics consumes explicit transport demand and later world-visible dock evidence without Operations owning route/motion authority.

This makes the recently added mechanisms observable and actionable in Minecraft instead of leaving them as isolated Java contracts.

## Failure Semantics

- Missing binding/evidence: WAIT or SAFE_STOP according to the existing runtime contract.
- Contradictory identity: SAFE_STOP.
- Explicit source fault: FAULT.
- Capacity full: WAIT, not fault.
- Quality unresolved: material cannot enter accepted-output flow.
- Maintenance due/in progress: resource unavailable to new work.
- Client/UI disagreement with server: server state wins.

No integration layer may convert unknown evidence into an assumed healthy state.

## First Implementation Wave

### Slice A — World Operations Adapter Contract

Create the common adapter/provider contract and server-side world binding/persistence needed to represent existing blocks as Operations resources without changing their original physics/control logic.

Representative integrations in the first slice:

- `SequenceControllerBlock` for cycle/sequence evidence
- `AlarmProcessorBlock` for alarm/fault evidence
- `WatchdogBlock` for heartbeat/timeout evidence
- at least one existing process/mechatronic block as a real Operations resource proof

### Slice B — Deepen Existing Operations Monitor

Extend the existing `OperationsMonitorMenu` and `OperationsMonitorScreen` to consume `OperationPlantViewAssessment` and show plant-level flow, bottleneck, quality, reliability, and due-date exposure. Monitor remains read-only.

### Slice C — Workcell Controller

Add the new controller block, registration/assets, server-owned workcell binding state, menu/screen, Engineering Ports, and delegation into the existing setup/maintenance/capacity/dispatch contracts.

### Slice D — Industrial Buffer

Add the new buffer block, registration/assets, persistent lot/capacity state, menu/screen, Engineering Ports, and delegation to existing buffer/material-release runtimes.

## Verification

Every implementation slice must pass:

- targeted Operations authority verifier
- Java compile
- Gradle tests
- clean build
- artifact/checksum gates used by the repository

Add a dedicated world-integration verifier enforcing:

- Operations Monitor remains observer-only
- adapters read existing device authority rather than duplicate it
- Workcell Controller delegates scheduling/setup/maintenance/capacity decisions
- Industrial Buffer preserves atomic material release and lot identity
- no job/lot/resource identities are encoded into redstone 0–15
- no UI/client code owns simulation truth

Minecraft GameTests may remain a manual diagnostic in ordinary PRs unless an automated world integration test is explicitly added. Skipped GameTests are never reported as passed.
