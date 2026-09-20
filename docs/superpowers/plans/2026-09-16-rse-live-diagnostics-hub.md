# RSE Live Diagnostics Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the existing RSE diagnostics console into a real-time, structured, observer-only telemetry hub for active RSE devices, subsystem health, Mega Factory state, and copyable backend/file diagnostics.

**Architecture:** Keep `RseDiagnostics` for backward-compatible log capture while adding `RseLiveDiagnostics` as a bounded structured event and active-device-health registry. Existing RSE log capture is bridged into the live stream, Mega validation publishes structured summaries, and the current diagnostics screen becomes a multi-view live console without scanning or mutating the world.

**Tech Stack:** Java 21, NeoForge 1.21.1, Minecraft client/server runtime APIs, Log4j, existing RSE diagnostics/UI stack, Python unittest static contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-rse-live-diagnostics-hub-design.md`

## Global Constraints

- Observer-only: diagnostics never changes simulation, DUT, controller, topology, or process state.
- No global world/chunk scan and no forced chunk loading.
- Collection is event-driven plus bounded monitoring of registered active devices only.
- Event and active-device stores are bounded and stale records are pruned.
- Existing `RseDiagnostics` report/copy behavior remains usable.
- `run/rse-diagnostics/rse-live-latest.txt` is the canonical copyable whole-mod live report.
- Mega-specific `mega-latest.txt` and `mega-history.log` remain authoritative for Mega acceptance details.

---

### Task 1: Add structured live telemetry model and bounded registry

**Files:**
- Create: `src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnosticEvent.java`
- Create: `src/main/java/dev/redstoneengineering/diagnostics/RseLiveDeviceHealth.java`
- Create: `src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java`
- Create: `tools/test_rse_live_diagnostics.py`
- Modify: `.github/workflows/build.yml` only to include the new Python contract test in the existing validation step.

**Interfaces:**
- `RseLiveDiagnosticEvent(long sequence, long epochMillis, long gameTick, RseDiagnosticSeverity severity, Domain domain, String source, String eventType, String message, String dimension, String position, String oldState, String newState, String reasonCode, String upstream)`
- `RseLiveDeviceHealth(String source, String blockId, Domain domain, String dimension, String position, long lastSeenTick, String quality, RseDiagnosticSeverity severity, String detail)`
- `RseLiveDiagnostics.recordEvent(...)`
- `RseLiveDiagnostics.refreshDevice(...)`
- `RseLiveDiagnostics.snapshotEvents()`
- `RseLiveDiagnostics.snapshotDevices(long currentTick)`
- `RseLiveDiagnostics.summary(long currentTick)`

- [ ] **Step 1: Write RED contract tests**

Require the three classes, bounded constants, `recordEvent`, `refreshDevice`, stale pruning, duplicate coalescing, and explicit absence of world/chunk iteration tokens such as `getAllLevels`, `getChunkSource().chunkMap`, or `players().forEach` as a discovery mechanism.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
```

Expected: FAIL because live telemetry classes do not exist.

- [ ] **Step 3: Implement immutable data records and registry**

Use a synchronized bounded deque for events and a bounded `LinkedHashMap<String,RseLiveDeviceHealth>` keyed by a stable logical source key. Coalesce consecutive identical `(source,eventType,newState,reasonCode)` events within a small tick/time window. Prune device records older than `DEVICE_STALE_TICKS = 200` from live summaries.

- [ ] **Step 4: Add summary aggregation**

Produce counts by quality string, domain, severity, active devices, and latest abnormal/root event without querying the world.

- [ ] **Step 5: Run contracts**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnosticEvent.java \
  src/main/java/dev/redstoneengineering/diagnostics/RseLiveDeviceHealth.java \
  src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java \
  tools/test_rse_live_diagnostics.py .github/workflows/build.yml
git commit -m "feat: add bounded RSE live telemetry registry"
```

---

### Task 2: Bridge existing RSE log capture into live telemetry

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/client/diagnostics/RseLogCapture.java`
- Modify: `src/main/java/dev/redstoneengineering/diagnostics/RseDiagnostics.java`
- Modify: `tools/test_rse_live_diagnostics.py`

**Interfaces:**
- Consumes: existing `RseDiagnostics.record(...)` behavior unchanged.
- Produces: every accepted RSE-related Log4j event also emits a `SYSTEM/LOG_EVENT` live event.

- [ ] **Step 1: Add RED test**

Require `RseLogCapture` to call `RseLiveDiagnostics.recordLogEvent(...)` after the legacy `RseDiagnostics.record(...)` call and preserve the existing RSE filter rules.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
```

- [ ] **Step 3: Implement the bridge**

Map INFO/WARN/ERROR directly to live severity. Source is the logger name, domain is `SYSTEM`, event type is `LOG_EVENT`, and throwable presence is reflected in the reason code without copying unbounded stack traces into the live event.

- [ ] **Step 4: Verify contracts and compile**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
./gradlew compileJava
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redstoneengineering/client/diagnostics/RseLogCapture.java \
  src/main/java/dev/redstoneengineering/diagnostics/RseDiagnostics.java \
  tools/test_rse_live_diagnostics.py
git commit -m "feat: stream RSE logs into live diagnostics"
```

---

### Task 3: Add safe whole-mod live report export

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java`
- Modify: `tools/test_rse_live_diagnostics.py`

**Interfaces:**
- Produces: `String exportReport(String environmentHeader, long currentTick)`.
- Produces: `Path exportLatest(String environmentHeader, long currentTick)` writing `run/rse-diagnostics/rse-live-latest.txt`.

- [ ] **Step 1: Add RED export tests**

Require exact path `run/rse-diagnostics/rse-live-latest.txt`, UTF-8, `Files.createDirectories`, temporary-file replacement, and caught `IOException`/warning behavior.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
```

- [ ] **Step 3: Implement stable report format**

The report contains runtime header, active-device totals, quality counts, subsystem/domain summaries, latest Mega summary if present, and newest structured events. Prefix structured event lines with `[RSE-LIVE]`.

- [ ] **Step 4: Implement safe latest-file write**

Write to `rse-live-latest.tmp`, then atomically replace `rse-live-latest.txt` when supported, with a fallback replace move. Export errors are recorded as WARN and never thrown into gameplay.

- [ ] **Step 5: Verify and compile**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java tools/test_rse_live_diagnostics.py
git commit -m "feat: export RSE live diagnostics report"
```

---

### Task 4: Upgrade the red-cross diagnostics UI into a live multi-view console

**Files:**
- Modify: `src/main/java/dev/redstoneengineering/client/ui/RseDiagnosticsScreen.java`
- Modify: `src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java`
- Modify: `tools/test_rse_live_diagnostics.py`

**Interfaces:**
- Views: `OVERVIEW`, `LIVE_EVENTS`, `SYSTEMS`, `MEGA_FACTORY`, `EXPORT`.
- Reads snapshots from `RseLiveDiagnostics`; never writes simulation state.

- [ ] **Step 1: Add RED UI contract tests**

Require all five view labels, periodic refresh in `tick()`, active/warn/error/quality summary use, Mega summary rendering, `Copy Report`, and `Export Latest` controls.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
```

- [ ] **Step 3: Implement view navigation and live refresh**

Add one view enum and buttons/tabs. `tick()` refreshes only snapshot references/counters; it does not scan the world. LIVE_EVENTS uses the structured event buffer, SYSTEMS groups by domain, and OVERVIEW shows active devices plus quality counts.

- [ ] **Step 4: Add Mega and export views**

MEGA_FACTORY renders the latest published Mega snapshot. EXPORT copies `RseLiveDiagnostics.exportReport(...)` and invokes `exportLatest(...)`, displaying success/failure feedback.

- [ ] **Step 5: Preserve tablet entry point**

The Diagnostic Tablet's existing `Diagnostics` button continues opening the upgraded screen; retained block snapshots remain available on the tablet itself.

- [ ] **Step 6: Verify and compile**

```bash
python3 -m unittest tools.test_rse_live_diagnostics -v
./gradlew compileJava
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/redstoneengineering/client/ui/RseDiagnosticsScreen.java \
  src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java \
  tools/test_rse_live_diagnostics.py
git commit -m "feat: turn RSE diagnostics into live monitoring console"
```

---

### Task 5: Publish Mega Factory telemetry into the live hub

**Files:**
- Modify/Create according to Mega v2.1 plan: `src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java`
- Modify: `src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java`
- Modify: `src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java`
- Modify: `tools/test_rse_live_diagnostics.py`
- Modify: `tools/test_rse_mega_factory.py`

**Interfaces:**
- Produces: `RseLiveDiagnostics.publishMegaSnapshot(runNumber, phase, master, rootBlockers, cascades, cellSummary, abnormalStations)`.
- Publishes on explicit `/mega diagnose` and meaningful phase/master-verdict transition only, not every server tick.

- [ ] **Step 1: Add RED integration tests**

Require Mega reporter/service to publish to `RseLiveDiagnostics` and require the live report to contain run, phase, master, root blocker, cascade, and cell summary fields.

- [ ] **Step 2: Run RED**

```bash
python3 -m unittest tools.test_rse_live_diagnostics tools.test_rse_mega_factory -v
```

- [ ] **Step 3: Implement Mega snapshot publishing**

Build a compact immutable Mega summary from the already-computed live evaluation; do not trigger a second client-side/world scan. Coalesce unchanged Mega status and emit a structured `VALIDATION/MEGA_STATE_CHANGE` event on meaningful changes.

- [ ] **Step 4: Verify and compile**

```bash
python3 -m unittest tools.test_rse_live_diagnostics tools.test_rse_mega_factory -v
./gradlew compileJava
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redstoneengineering/diagnostics/RseLiveDiagnostics.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaDiagnosticReporter.java \
  src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java \
  tools/test_rse_live_diagnostics.py tools/test_rse_mega_factory.py
git commit -m "feat: publish Mega telemetry to RSE live diagnostics"
```

---

### Task 6: Full verification and user handoff

**Files:**
- Modify: `docs/VALIDATION_FACTORY.md`
- Modify/add diagnostics documentation if the repository has an existing diagnostics user guide.

- [ ] **Step 1: Document the field workflow**

Document that the user can inspect the red-cross live console or copy either `run/rse-diagnostics/rse-live-latest.txt` or `mega-latest.txt` instead of sending screenshots.

- [ ] **Step 2: Run all Python validation contracts**

```bash
python3 -m unittest \
  tools.test_rse_validation_autorun \
  tools.test_rse_validation_plant \
  tools.test_rse_mega_factory \
  tools.test_rse_live_diagnostics -v
```

- [ ] **Step 3: Run Java/build gates**

```bash
./gradlew clean compileJava
./gradlew test
./gradlew clean build
```

- [ ] **Step 4: Verify exact final HEAD workflows**

Required:

```text
RSE Build Verification: success
RSE Operations Material Flow Verification: success
```

- [ ] **Step 5: Field handoff remains explicit**

CI success proves contracts/build, not in-world behavior. User reruns:

```text
/rsevalidation mega retest
/rsevalidation mega diagnose
```

and shares `run/rse-diagnostics/mega-latest.txt` or `rse-live-latest.txt` for final field acceptance.