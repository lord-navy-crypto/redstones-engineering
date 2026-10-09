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
