# RSE Live Diagnostics Hub Design

**Date:** 2026-09-16  
**Branch:** `codex/persistent-plant-runtime-validation-world`

## Goal

Upgrade the existing RSE Diagnostics / Diagnostic Tablet stack from a passive bounded log viewer into a real-time, observer-only engineering telemetry hub for the whole mod. The hub must surface RSE runtime health, state changes, faults, loaded active devices, subsystem summaries, Mega Factory telemetry, and copyable exports without requiring visual inspection or screenshots.

## Existing system

The current stack already provides three useful foundations:

- `RseLogCapture` taps Log4j and forwards RSE-related log events.
- `RseDiagnostics` stores a thread-safe session-local bounded buffer of diagnostic entries.
- `RseDiagnosticsScreen` and `DiagnosticTabletScreen` expose logs and retained block snapshots.

The weakness is that this stack is reactive text capture only. It does not expose structured engineering telemetry, device-health summaries, or a live subsystem view.

## Architecture

The upgraded system keeps `RseDiagnostics` observer-only and adds a structured telemetry layer.

```text
RSE runtime producers
  ├─ engineering port quality/state transitions
  ├─ subsystem state changes
  ├─ validation / Mega Factory reports
  ├─ operations / robotics / process monitor events
  └─ exceptions and existing RSE logs
          ↓
RseLiveDiagnostics
  ├─ bounded event stream
  ├─ active-device health registry
  ├─ subsystem health summary
  ├─ latest Mega telemetry snapshot
  └─ export snapshot
          ↓
RseDiagnosticsScreen / Diagnostic Tablet
          ↓
clipboard + run/rse-diagnostics/rse-live-latest.txt
```

## Collection model

Use **event-driven updates plus bounded periodic sampling**, not global world scanning.

1. Structured events are recorded immediately when an RSE subsystem reports a meaningful state transition.
2. Active RSE devices that opt into monitoring are sampled on a bounded cadence of 10–20 ticks while loaded.
3. Sampling only covers registered/active monitored devices; no chunk loading and no scan of the entire world.
4. Repeated identical state is coalesced so the event stream does not spam every tick.
5. The diagnostics system never owns or mutates simulation, controller, topology, or device state.

## Structured telemetry

Add a structured event model with fields sufficient for machine-readable debugging:

- sequence
- game tick when available
- wall-clock timestamp
- severity (`INFO`, `WARN`, `ERROR`)
- domain (`ANALOG`, `DIGITAL`, `OPTICAL`, `PNEUMATIC`, `CONTROL`, `ROBOTICS`, `OPERATIONS`, `VALIDATION`, `SYSTEM`)
- source id / block id / logical device id
- event type
- message
- dimension and world position when relevant
- old/new quality or state when relevant
- reason code
- upstream/root-source identifier when known

A typical event is rendered as:

```text
[RSE-LIVE] tick=183420 domain=CONTROL source=D38/POS_SENSOR event=QUALITY_CHANGE old=VALID new=NO_SIGNAL severity=WARN reason=SERVO_FEEDBACK_LOST upstream=D37
```

## Active-device health registry

Track only monitored loaded devices. Each record contains:

- logical source id
- block id
- dimension / position
- last seen tick
- current quality/state
- current severity
- subsystem/domain
- concise detail

A device becomes inactive when it has not been refreshed within a bounded timeout. Inactive records are removed from the live count but may remain in history events.

## UI

Keep the existing red-cross / Diagnostics entry point. Upgrade `RseDiagnosticsScreen` into a multi-view live console with at least these views:

### OVERVIEW

- active monitored RSE devices
- counts by `VALID`, `NO_SIGNAL`, `STALE`, `SATURATED`, `FAULT`, `DOMAIN_MISMATCH`, `TOPOLOGY_ERROR`
- current WARN / ERROR counts
- latest root fault / blocker
- latest Mega Factory phase/master status when available

### LIVE EVENTS

- continuously refresh from the structured event stream
- filter by severity and domain
- newest events visible without closing/reopening the screen
- preserve existing log events by adapting them into the same stream

### SYSTEMS

Aggregate live health by domain/subsystem. A subsystem summary contains active count, unhealthy count, worst severity, and most recent abnormal event.

### MEGA FACTORY

Render the latest structured Mega Factory diagnosis: run number, phase, master verdict, root blockers, cascades, cell summary, and station abnormal count. This view consumes `RseMegaDiagnosticReporter` output/telemetry rather than re-evaluating the factory on the client.

### EXPORT

- Copy current report to clipboard.
- Write `run/rse-diagnostics/rse-live-latest.txt`.
- Keep the existing Mega-specific `mega-latest.txt` and `mega-history.log` outputs.

## Logging and file export

The live diagnostics exporter writes UTF-8 to:

```text
run/rse-diagnostics/rse-live-latest.txt
```

The report includes runtime versions, overview counts, subsystem summaries, latest Mega status, and recent structured events. File I/O errors are logged but never crash gameplay or suppress UI access.

## Integration with Mega Factory v2.1

`RseMegaDiagnosticReporter` emits a structured summary into `RseLiveDiagnostics` whenever `/rsevalidation mega diagnose` runs and whenever the runtime reaches a meaningful phase/verdict transition. Mega remains authoritative for its own evaluation; Live Diagnostics only observes and republishes telemetry.

The live view must therefore make the field workflow possible without screenshots:

```text
/rsevalidation mega retest
/rsevalidation mega diagnose
→ open Red Cross Diagnostics OR copy rse-live-latest.txt / mega-latest.txt
```

## Performance and safety constraints

- No global chunk/world scan.
- No forced chunk loading.
- No per-tick full registry traversal.
- Default event buffer remains bounded.
- Active-device registry is bounded and prunes stale records.
- State-change coalescing prevents duplicate event spam.
- Diagnostics code is observer-only and cannot change DUT/controller state.
- Client UI reads snapshots; it does not own server simulation state.

## Code organization

- `diagnostics/RseDiagnostics.java` — preserve legacy log buffer/export compatibility.
- `diagnostics/RseLiveDiagnostics.java` — structured live telemetry/event and active-device registry.
- `diagnostics/RseLiveDiagnosticEvent.java` — immutable event data.
- `diagnostics/RseLiveDeviceHealth.java` — immutable current health snapshot.
- `client/diagnostics/RseLogCapture.java` — adapt captured RSE logs into live events as well as legacy records.
- `client/ui/RseDiagnosticsScreen.java` — multi-view live console.
- `validation/RseMegaDiagnosticReporter.java` — publishes Mega summary/transition events into live telemetry.

## Tests

Add contract/unit coverage for:

- structured event record and bounded buffer
- duplicate event coalescing
- active-device refresh and stale pruning
- quality counts and subsystem aggregation
- no world/chunk scanning API usage
- `RseLogCapture` forwarding into live telemetry
- UI source containing OVERVIEW / LIVE EVENTS / SYSTEMS / MEGA FACTORY / EXPORT views
- export path `run/rse-diagnostics/rse-live-latest.txt`
- Mega reporter publishing live diagnostics events

Final delivery requires the exact final HEAD to pass existing Python contracts, static/reference verification, `compileJava`, Gradle tests, clean build, and both GitHub Actions workflows. In-world behavior remains field-acceptance pending until the user reruns and shares exported diagnostics.