# LDLib2 HMI Migration Plan

## Ownership boundary

LDLib2 is the engineering UI infrastructure layer, not the simulation owner.

```text
RSE server BlockState / BlockEntity / runtime stores
        ↓ validated state + intent
LDLib2 ModularUI / layout / binding / RPC
        ↓
client engineering HMI
```

LDLib2 may own layout, styling, reusable widgets, data binding, UI RPC,
UI debugging and editor tooling.

LDLib2 must not own physics calculations, sampling cadence, topology solving,
control-loop execution, authoritative BlockState, measurement generation, or
commissioning verdicts.

## First migration prototypes

1. **Signal Conditioner** — formula card, signed direct entry, mode cycle,
   synchronized derived output/evidence.
2. **Oscilloscope** — complex responsive layout, acquisition state,
   trigger/cursors, live capture visualization.
3. **Universal Field Device** — conditional controls across many block
   families, ADJUSTABLE / MEASURED / DERIVED / FIXED roles, large workspace.

## Reusable RSE LDLib2 components

- FormulaCard
- EngineeringParameterRow
- DirectNumericEntry
- DiscreteCycleControl
- FixedParameterRow
- MeasurementRow
- EvidenceBadge
- TopologySummary
- RouteControl
- ExplicitActionButton
- InstrumentChart
- CommissioningHistoryTable

## Migration rule

Do not rewrite all 122 block UIs at once. A legacy Screen/Menu remains valid
until its LDLib2 replacement preserves every existing control and server
authority contract, passes verifier/tests, and receives a runClient visual
review.


## PR #229: exact formula entry and evidence truth

A numeric field represents the visible engineering value, not an internal
BlockState index. Server-side menu validation must use the live block, its
supported range or discrete set, and the active mode. Preset-owned coefficients
remain read-only; toggles and maintenance resets remain separate actions.

A synchronized numeric field can submit the currently displayed value again.
The repeated value is an acknowledged no-op: it must not increment a
calibration/reference switch counter, allocate evidence history, notify redstone
neighbors, restart an acquisition, or enqueue an extra transfer tick solely
because the HMI repeated the existing setting. Invalid values are rejected.

Two representative executable checks now cover this rule:

- Signal Analyzer: exact offset and reference edits increment distinct switch
  counters only for real changes. Repeated/out-of-range entries preserve evidence.
  RseSignalCalibrationTrialGameTests is explicitly registered with NeoForge.
- Signal Conditioner: gain g=3 with Redstone input 6 yields bounded output 15
  after scheduled server ticks. Repeated entry and g=0/5 must not replace g=3.

Static CI guards: rse_signal_calibration_trial_verify.py and
rse_engineering_ui_verify.py. In-world contracts: RseSignalCalibrationTrialGameTests
and RseEngineeringUiGameTests. Normal green PR builds verify sources, compiler
and Gradle tests; runGameTestServer and runClient remain separate runtime/visual
gates and must not be reported as passing until actually executed.
