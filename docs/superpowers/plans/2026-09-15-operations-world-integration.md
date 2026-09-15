# Operations World Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the recently added Industrial Operations mechanisms directly useful in Minecraft by integrating them into existing RSE blocks first and adding only the two first-wave blocks whose responsibilities do not currently exist: Workcell Controller and Industrial Buffer.

**Architecture:** Existing field devices and machines remain authoritative for their native engineering behavior and expose Operations evidence through adapters. The existing Operations Monitor is deepened as a read-only plant console. Workcell Controller owns only world-facing workcell configuration/binding and delegates all scheduling/setup/maintenance/capacity decisions to existing Operations runtimes. Industrial Buffer owns persistent world-visible lot/WIP state and delegates allocation/release decisions to existing buffer/material runtimes.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, RSE Engineering Ports, existing RSE menus/screens and Operations runtimes, repository Python authority verifiers, Gradle CI.

**Spec:** `docs/superpowers/specs/2026-09-15-operations-world-integration-design.md`

## Global Constraints

- [ ] Reuse existing blocks whenever their current semantic responsibility matches the new mechanism.
- [ ] Do not turn `OperationsMonitorBlock` into a production controller; it remains observer-only.
- [ ] Do not turn `SequenceControllerBlock` into a scheduler; it remains a local sequence controller.
- [ ] Do not duplicate `AlarmProcessorBlock`, `WatchdogBlock`, `SafetyInterlockBlock`, or `FaultLatchBlock` with IE-specific copies.
- [ ] Existing Operations runtimes remain the single authority for dispatch, precedence, setup/changeover, maintenance, capacity, buffer allocation, quality, and material release.
- [ ] Preserve vanilla redstone 0–15 as a simple world-facing boundary; never encode job/output/resource/lot identity into analog redstone.
- [ ] UI is server-authoritative projection only.
- [ ] Missing evidence never becomes fabricated healthy/idle/completed state.
- [ ] Keep ordinary PR GameTests labeled skipped unless actually run.

---

## Task 1: Add Operations World Resource Adapter Contract

**Files:**
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceProvider.java`
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceSnapshot.java`
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceResolver.java`
- Create: `tools/rse_operations_world_integration_verify.py`
- Modify: `.github/workflows/operations-material-flow.yml`

- [ ] Define a minimal provider API that exposes stable resource identity, process capabilities, availability/running evidence, completion evidence availability, fault/health evidence, and evidence quality without owning machine physics.
- [ ] Make the immutable snapshot fail closed when identity/evidence is missing or contradictory.
- [ ] Implement resolver logic that reads a block implementing the provider contract at an explicit `BlockPos`; no proximity auto-discovery.
- [ ] Add verifier rules prohibiting imports/calls from the adapter layer into `OperationDispatchRuntime` mutation paths, world movement, robot motion, or client UI.
- [ ] Add the verifier to the existing `RSE Operations Material Flow Verification` workflow.
- [ ] Run verifier syntax + targeted verifier before continuing.

**Acceptance:** Existing blocks can expose Operations evidence without any new IE machine block and without changing native block authority.

---

## Task 2: Integrate Existing Safety/Sequence Blocks as Evidence Sources

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java`
- Modify: `src/main/java/dev/redstoneengineering/block/AlarmProcessorBlock.java`
- Modify: `src/main/java/dev/redstoneengineering/block/WatchdogBlock.java`
- Inspect/modify if appropriate: `src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java`
- Inspect/modify if appropriate: `src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java`
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] `SequenceControllerBlock`: expose step/running/completed-cycle evidence through the provider contract while preserving its current RUN/ADVANCE/RESET/HOLD behavior.
- [ ] `AlarmProcessorBlock`: expose latched/severity/acknowledged fault evidence without changing alarm latching/reset semantics.
- [ ] `WatchdogBlock`: expose heartbeat timeout/health evidence without changing its transition-based timeout logic.
- [ ] Integrate SafetyInterlock/FaultLatch only if their current public runtime evidence can be mapped without new semantics; otherwise leave them unchanged and document deferral.
- [ ] Ensure none of these blocks can enqueue/dispatch jobs or mutate Operations queues.
- [ ] Extend verifier with explicit anti-duplication/authority checks.

**Acceptance:** Existing devices become useful Operations inputs rather than being replaced by parallel IE blocks.

---

## Task 3: Integrate One Real Existing Machine as an Operations Resource Proof

**Files:**
- Inspect candidates under `src/main/java/dev/redstoneengineering/block/` and select one existing process/mechatronic machine with authoritative run/fault/completion evidence.
- Preferred initial candidates: `ServoActuatorBlock.java`, `PneumaticCylinderBlock.java`, or another machine whose current runtime can expose non-fabricated evidence.
- Modify selected block/provider support.
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Choose the machine based on actual existing evidence, not aesthetics.
- [ ] Map its process capability to one stable process ID.
- [ ] Expose its real availability/running/fault state through the adapter.
- [ ] If no trustworthy completion event exists, explicitly expose completion as unavailable rather than inventing one.
- [ ] Verify that its original redstone/physics behavior is unchanged.

**Acceptance:** At least one existing RSE machine participates in Operations as a real resource with zero parallel simulation logic.

---

## Task 4: Deepen the Existing Operations Monitor Instead of Adding a New Dashboard Block

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java`
- Modify: `src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java`
- Modify: `src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java`
- Modify only if registration changes are required: `src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java`
- Modify: `tools/rse_operations_plant_view_verify.py`
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Keep the block’s existing Engineering Ports (machine running, cycle pulse, queue/WIP proxy) backward-compatible.
- [ ] Sync `OperationPlantViewAssessment` data into the existing menu: bottleneck, constrained workcells, quality coverage/FPY/reject/rework, reliability coverage/availability/failures, due-date exposure/overdue jobs.
- [ ] Add a Plant View section/tab/page in `OperationsMonitorScreen` using the existing monitor screen rather than registering another monitor block/menu.
- [ ] Surface incomplete/invalid coverage visibly; do not silently render invalid data as zero/healthy.
- [ ] Do not add control buttons for enqueue, dispatch, changeover, maintenance, buffer mutation, or robot movement.
- [ ] Verifier must continue to prove Monitor is observer-only.

**Acceptance:** The player can see the mechanisms recently added to Operations from an existing world block, with no second dashboard block.

---

## Task 5: Add Server-Owned Workcell Binding State

**Files:**
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationWorkcellBinding.java`
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationWorkcellStore.java`
- Create or adapt persistence: `src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java` (name may be adjusted only if an existing RSE SavedData store is found and reused)
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Persist stable workcell ID plus explicitly bound resource positions/identities server-side.
- [ ] Require explicit binding/unbinding; no proximity magic.
- [ ] Resolve bound resources through `OperationWorldResourceResolver` and preserve missing/invalid evidence.
- [ ] Keep high-cardinality identities out of `BlockState`.
- [ ] Add duplicate resource/workcell identity checks and fail-closed behavior.

**Acceptance:** World-visible workcells can persist resource membership across save/load without encoding plant state into client UI or block properties.

---

## Task 6: Add the New Workcell Controller Block

**Files:**
- Create: `src/main/java/dev/redstoneengineering/block/WorkcellControllerBlock.java`
- Create: `src/main/java/dev/redstoneengineering/ui/WorkcellControllerUi.java`
- Create: `src/main/java/dev/redstoneengineering/ui/menu/WorkcellControllerMenu.java`
- Create: `src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java`
- Modify registration in the existing block registry owner (`RedstoneEngineering.java` or the module currently owning Operations blocks after inspection).
- Modify: `src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java`
- Modify: `src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java`
- Add standard block/item/model/loot/lang assets under `src/main/resources/assets/redstoneengineering/` and `src/main/resources/data/redstoneengineering/` following existing RSE conventions.
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Register one new block because no existing block owns workcell grouping/configuration semantics.
- [ ] Engineering Ports expose only low-cardinality signals such as ACTIVE/PERMIT/HOLD/FAULT/QUEUE pressure.
- [ ] UI shows bound resources, process capability, setup state, maintenance hold, active assignments, queue/capacity evidence, and current admission reason.
- [ ] Operator resource binding and changeover request are validated server-side.
- [ ] Delegate final job/resource selection to `OperationDispatchRuntime` / routed dispatch.
- [ ] Delegate changeover to `OperationChangeoverRuntime`.
- [ ] Delegate maintenance lifecycle to `OperationMaintenanceRuntime`.
- [ ] Delegate capacity admission to the existing workcell admission runtime.
- [ ] No bottleneck score may directly alter dispatch order.

**Acceptance:** The player can interact with the real Operations mechanisms through one justified controller block without creating a second scheduler.

---

## Task 7: Add Server-Owned Industrial Buffer State

**Files:**
- Create: `src/main/java/dev/redstoneengineering/operations/world/OperationIndustrialBufferState.java`
- Extend/reuse: `src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java`
- Reuse: existing `OperationBufferLot`, `OperationBufferSnapshot`, `OperationBufferRuntime`, `OperationMaterialReleaseRuntime`
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Persist capacity and explicit lots by existing lot/output/job identities.
- [ ] Preserve quality-cleared accepted-output identity.
- [ ] Snapshot world state into existing `OperationBufferSnapshot`; do not duplicate buffer decision logic.
- [ ] Apply receipt/allocation decisions only through existing buffer/material runtimes.
- [ ] Guarantee queue-full or downstream WAIT does not consume a lot.
- [ ] Do not collapse lot identity into raw item count.

**Acceptance:** Existing buffer/material-flow mechanisms receive a real world authority source without losing traceability.

---

## Task 8: Add the New Industrial Buffer Block

**Files:**
- Create: `src/main/java/dev/redstoneengineering/block/IndustrialBufferBlock.java`
- Create: `src/main/java/dev/redstoneengineering/ui/IndustrialBufferUi.java`
- Create: `src/main/java/dev/redstoneengineering/ui/menu/IndustrialBufferMenu.java`
- Create: `src/main/java/dev/redstoneengineering/client/ui/IndustrialBufferScreen.java`
- Modify block/menu/client registrations as in Task 6.
- Add standard block/item/model/loot/lang assets following existing RSE conventions.
- Modify: `tools/rse_operations_world_integration_verify.py`

- [ ] Register one new block because no existing generic block owns finite lot/WIP storage semantics.
- [ ] UI lists capacity, used/free units, lot/output identity, source job, quality-cleared status, and evidence state.
- [ ] Engineering Ports expose simple fullness/WIP/permit signals only.
- [ ] Player interaction must not manually forge accepted-quality status or completion evidence.
- [ ] Optional NeoForge item capability interoperability is deferred unless an existing RSE inventory pattern can preserve lot identity safely in this slice.

**Acceptance:** A player can place and inspect a finite Operations buffer whose contents are the same authoritative lots used by downstream release logic.

---

## Task 9: Cross-Block World Interaction Verification

**Files:**
- Modify: `tools/rse_operations_world_integration_verify.py`
- Modify: `.github/workflows/operations-material-flow.yml`
- Optionally add manual GameTest diagnostics under the repository’s existing GameTest structure after inspecting current conventions.

- [ ] Static guard: Monitor cannot import/call dispatch/queue/mutation runtimes.
- [ ] Static guard: Workcell Controller cannot reimplement comparator/routing algorithms.
- [ ] Static guard: Industrial Buffer must call existing buffer/material-release runtimes.
- [ ] Static guard: adapter sources cannot mutate native block physics/control state.
- [ ] Static guard: no job/output/resource/lot identity is encoded in redstone properties/values.
- [ ] Compile Java.
- [ ] Run Gradle tests.
- [ ] Run clean build, checksums, and artifact upload gates.
- [ ] Report GameTests accurately as passed only if they actually execute.

**Acceptance:** World integration is useful and interactive without violating the single-authority architecture.

---

## Task 10: Review Deferred Blocks After First-Wave Playability

No implementation in this task unless evidence shows a real interaction gap.

- [ ] Assess whether job creation can live in Workcell/Operations control UI without corrupting Monitor observer semantics; otherwise propose a Production Controller.
- [ ] Assess whether existing instrumentation/HMI can host quality inspection interaction; otherwise propose a Quality Inspection Station.
- [ ] Assess whether maintenance actions fit Workcell Controller; create Maintenance Terminal only if plant-wide maintenance workflows justify it.
- [ ] AMR Dock / Transfer Station remains likely new-block candidate because explicit physical docking/reservation/transfer has no existing semantic owner; design separately before implementation.

**Acceptance:** New blocks remain a consequence of missing responsibilities, not a default way to expose every runtime mechanism.
