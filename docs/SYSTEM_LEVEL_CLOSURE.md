# System-Level Closure

RSE has completed its sequential **122 / 122 registered-block engineering audit**. The project therefore moves from **Block-Level Closure** to **System-Level Closure**: proving that individually correct components remain correct when composed into real engineering chains.

The current baseline also includes the **Vanilla Redstone Immutability Regression Gate**. Vanilla dust, repeater, comparator, observer and piston behavior remain protected while RSE's Vanilla Redstone Engineering layer stays observer-only.

## Closure model

System-Level Closure is intentionally staged so a failure remains attributable instead of being hidden inside one oversized test plant.

### Phase 1 — Deterministic chains and recovery

Phase 1 adds no gameplay blocks. It exercises five representative multi-block chains using the existing architecture:

1. **Reference -> Signal Conditioner -> Analog Indicator**
   - repeated 0 <-> 15 transitions must converge through the full chain;
   - no downstream stale output may survive a boundary change.

2. **Reference -> Servo Actuator -> Servo Position Sensor -> Analog Indicator**
   - redstone command crosses into mechatronic position;
   - the position sensor crosses the plant state back into redstone measurement;
   - the display must faithfully present the measured feedback;
   - deterministic metrology imperfection is allowed, but the bounded error contract is enforced.

3. **Servo command-loss and recovery chain**
   - loss of command evidence must brake and hold the plant rather than fabricate command zero;
   - restored command evidence must release the fail-safe and reconverge;
   - the measurement/display chain must recover without ghost state.

4. **Fault Injector -> Alarm Processor**
   - an injected fault must propagate and latch;
   - clearing the physical fault alone must not erase the alarm;
   - explicit healthy reset must clear the incident;
   - device replacement must not inherit transient runtime and re-latch a ghost fault.

5. **Instrument Cable remove / replace lifecycle**
   - a live link must be physically symmetric and expose symmetric VALID snapshots;
   - endpoint removal must remove both ghost connectivity and ghost evidence;
   - replacement must rebuild the link and read-only evidence symmetrically.

Phase 1 raises the required Minecraft GameTest floor from **317 to 322**.

## Phase 2 — Closed-loop control

Phase 2 will test true feedback control rather than isolated controller behavior. The primary candidate is:

`Setpoint -> PID Controller -> Servo Actuator -> Position Sensor -> Process Value feedback -> PID Controller`

The acceptance target is not perfect tick-by-tick equality. It is bounded convergence, correct response to setpoint changes, fail-safe behavior on missing process evidence, bumpless/recoverable operation where applicable, and truthful commissioning/telemetry evidence.

## Phase 3 — Safety, protection and incident closure

Phase 3 will compose control with Safety Interlock, Fault Injector, Alarm Processor, first-out analysis, event timeline and recovery sequencing. It will verify that a plant transitions to a safe state under faults and that diagnostics preserve the earliest trustworthy abnormal evidence without inventing causality.

## Phase 4 — Lifecycle, soak and process-bound closure

Phase 4 covers failure modes that are not honestly proven by short isolated GameTests alone:

- extended control-loop soak;
- repeated start / stop and reconnect cycles;
- true chunk unload / reload;
- full world/process restart persistence;
- multiplayer simultaneous observation and interaction;
- representative real-client UI behavior;
- dense-network performance and bounded telemetry under sustained activity.

These checks remain explicit evidence boundaries until they are actually executed. They must never be converted into synthetic PASS results.

## Engineering invariants

System-Level Closure preserves the existing product rules:

- Vanilla-first 0..15 redstone boundary;
- missing evidence is not a zero measurement;
- STALE is not VALID;
- observer and UI reads must not create runtime state;
- fault recovery must not inherit ghost runtime;
- diagnostics must not mutate the plant they observe;
- no new gameplay blocks are added merely to make a closure test easier;
- a demonstrated cross-system defect may receive a narrowly scoped production fix, followed by a regression test.

## Phase 1 exit condition

Phase 1 is complete only when:

- the 122-block audit remains green;
- Vanilla Redstone Immutability remains green;
- all five Phase 1 system-chain GameTests pass;
- the required GameTest count is at least **322**;
- Java compile and Gradle tests pass;
- the chained-neighbor-update safety cutoff is not triggered;
- clean build, SHA-256 generation and verified artifact upload succeed;
- the same checks pass again on `main` after merge.
