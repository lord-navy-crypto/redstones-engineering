# RSE Engineering Interface Foundation

The RSE Engineering Interface System makes existing engineering behavior easier to inspect and configure without moving simulation authority into the client.

## Interaction hierarchy

RSE uses three UI levels:

1. **No dedicated screen** — passive wires, cables, junctions, conduits, dampers, and other devices that are adequately described by their model, Jade, and port diagnostics.
2. **Compact Engineering Panel** — configurable processors such as the Signal Conditioner. These screens expose a small bounded set of controls and live readback.
3. **Engineering Workbench** — complex controllers and instruments such as the PID Controller. These screens organize live engineering evidence into Overview, Ports, Configure, Diagnostics, and History sections.

This avoids creating one unrelated GUI implementation per block while preserving distinct device semantics.

## Shared visual language

The first framework establishes five shared sections:

- **Overview** — the small set of values needed to understand current operation;
- **Ports** — physical face, engineering role, direction, and boundary;
- **Configure** — bounded user-owned configuration only;
- **Diagnostics** — measured or derived engineering evidence;
- **History** — captured evidence where the device owns a meaningful history concept.

The dark instrument-panel visual language, status colors, signal bars, tab layout, and server-authority footer live in `EngineeringScreen` rather than being copied across devices.

## Authority boundary

The UI is downstream of authoritative RSE behavior:

```text
Server physics / network / controller / sampling
                    |
                    v
       authoritative state + snapshots
                    |
                    v
              Menu / DataSlot
                    |
                    v
                Client Screen
```

A configuration interaction travels in the opposite direction only as bounded intent:

```text
Client button
    |
    v
vanilla menu-button request
    |
    v
server validation
    |
    v
existing authoritative BlockState/controller configuration
```

Client screens must not:

- import RSE physics implementations;
- read or mutate `RuntimeIntStore`;
- schedule simulation ticks;
- write world BlockState;
- update neighbors;
- evaluate acceptance independently;
- define sensor sampling cadence.

`tools/rse_engineering_ui_verify.py` makes this boundary a CI contract.

## Signal Conditioner pilot

Normal right-click opens the compact Engineering Panel. The screen exposes:

- live input and output;
- GAIN / OFFSET / CLAMP / THRESHOLD / DEADBAND mode;
- current bounded parameter;
- BACK input and FRONT output port semantics;
- explicit 0..15 world boundary.

Mode and parameter buttons call `SignalConditionerBlock.applyConfigurationAction` on the logical server. The block's established signal-processing calculation remains unchanged. Shift-right-click remains a quick parameter-adjust shortcut.

## PID Controller pilot

Normal right-click opens the Engineering Workbench. It presents the existing `ClosedLoopCommissioning` snapshot rather than reimplementing controller calculations:

- setpoint (SP), process value (PV), error, and output;
- commissioning score/status;
- rise-to-90%, settling, overshoot, and saturation diagnostics;
- AUTO/MANUAL and inhibit state;
- existing four bounded tuning presets;
- bounded captured acceptance-history count.

The established engineering gestures remain intact:

- **Shift + FRONT** — capture acceptance evidence;
- **Shift + another face** — reset PID runtime;
- **normal right-click** — open the Engineering Workbench.

The workbench does not expose fictitious continuous Kp/Ki/Kd controls because Alpha 1.0.20 currently owns four explicit tuning presets.

## Verification

`RseEngineeringUiGameTests` exercises the server actions behind the screens in a real GameTest level. These tests prove that UI configuration reaches authoritative world state while preserving bounded engineering semantics. Visual layout itself remains an interactive `runClient` acceptance gate because a headless GameTest cannot prove readability or interaction ergonomics.
