# Alpha 2 Second-Pass Verification

Alpha 2 is under **FEATURE FREEZE**. This stage does not add normal blocks or new gameplay systems. It verifies the frozen 127-block architecture more aggressively and allows only narrowly scoped fixes for demonstrated defects.

## AUTOMATED SECOND-PASS GATE

The automated layer intentionally combines exhaustive static/per-block evidence with horizontal destructive runtime checks.

### Frozen architecture prerequisites

- Historical core registry: **122/122**
- Direct per-block GameTest evidence: **122/122**
- Direct per-block verifier evidence: **122/122**
- Engineering port contracts: **122/122**
- Engineering domain contracts: **122/122**
- Snapshot/metrology surfaces: **122/122**
- Stateful cleanup evidence: **62/62**
- Systems extension: **5**
- Aggregate architecture: **127 blocks**

The per-block audit is still the exhaustive matrix. The second pass does not blindly place every default block state because some devices require specific orientation, support, medium, or topology context; doing that would create false failures rather than meaningful verification.

### Destructive runtime additions

Five cross-system GameTests are added on top of the existing 187 required tests, raising the required automated floor to **192**:

1. `rapidBoundaryToggleConvergesWithoutStaleState`
   - repeatedly drives a real source -> conditioner -> indicator path through 0 <-> 15
   - verifies saturation and LOW/HIGH convergence without retained stale output

2. `instrumentCableLinkLifecycleRebuildsSymmetrically`
   - places a two-ended instrument link
   - verifies symmetric physical connection and `VALID` read-only snapshots
   - removes one endpoint and proves the surviving cable drops the ghost connection/snapshot
   - replaces the endpoint and proves the link rebuilds symmetrically

3. `faultInjectorRuntimeCleanupSurvivesRemovalAndReinsert`
   - creates live transient runtime state
   - removes the device and proves its runtime store is gone
   - reinserts the device unarmed and proves no ghost activation history is inherited

4. `faultInjectorToAlarmChainClearsAfterHealthyReset`
   - exercises a real Fault Injector -> Alarm Processor chain
   - proves injected fault latching, persistence until explicit healthy reset, recovery, and non-relatch after replacement

5. `vanillaOverlayBurstHistoryIsBoundedAndObserverOnly`
   - generates 64 real server NeighborNotify observations against vanilla redstone wire
   - requires the exact-target presentation history to remain bounded at 24 samples
   - preserves literal dust value 0
   - proves the observer layer does not mutate the vanilla wire

The normal CI chained-neighbor-update cutoff remains authoritative. A stress test that triggers `Too many chained neighbor updates` fails the build.

## Authority boundaries retained

- VEO remains observation/diagnostics only: **VANILLA BEHAVIOR IMMUTABILITY** is unchanged.
- Observer telemetry does not call `setBlock`, `scheduleTick`, `updateNeighborsAt`, or `neighborChanged` to alter vanilla behavior.
- Shared chart clients remain synchronized presentation only and do not scan client world state to infer physics.
- The destructive GameTests are allowed to mutate their isolated test worlds because they are tests, not runtime observer code.

## MANUAL / PROCESS-BOUND CHECKS

The automated second-pass gate does **not** claim to prove checks that inherently require a real client, multiple clients, a process restart, or deliberate chunk lifecycle control. These remain explicitly pending after the automated gate is green:

- real-client visual/UI inspection at representative GUI scales
- multiplayer viewing and simultaneous interaction
- full **world restart** / process restart persistence
- true **chunk unload/reload** lifecycle checks
- extended long-running control-loop soak
- larger dense-network performance observation beyond the GameTest safety cutoff

These checks must be recorded as separate evidence. Their pending status is not hidden or converted into a synthetic PASS.

## Exit condition

The automated portion of SECOND-PASS VERIFICATION is complete only when:

- the 122-block audit still reports full closure,
- the Alpha 2 finishing gate remains green,
- the second-pass verifier is green,
- Java 21 compile succeeds,
- Gradle verification succeeds,
- at least **192 required Minecraft GameTests** pass,
- no chained-neighbor-update cutoff occurs,
- clean build and SHA-256 generation succeed.

After that, work moves to the manual/process-bound matrix above rather than feature expansion.
