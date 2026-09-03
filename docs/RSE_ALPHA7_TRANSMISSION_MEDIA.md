# RSE alpha.7 — Transmission Media Standard

## Core rule
A domain only gets a cable when its physics/gameplay needs a routed 3-D conduit.

| Domain | Medium | Geometry | Routing semantics |
|---|---|---|---|
| Redstone | Vanilla dust + insulated signal cable | dust surface + 3-D cable | dust is cheap/world-facing; cable is isolated dense routing |
| Lapis | precision trace | thin floor trace | horizontal only; no wall-climbing for free |
| Quartz | timing trace | thin floor trace | horizontal clock distribution |
| Amethyst | resonance dust | thin floor trace | horizontal frequency-event propagation |
| Optical | glowglass fiber | 3-D fiber | X/Y/Z straight segments + junction blocks for bends/branches; intensity/channel |
| Copper | copper electrical cable | 3-D cable | X/Y/Z cable segments + junction blocks |
| Iron | no signal cable | spatial field + iron core | B-field exists in space; iron shapes/carries flux conceptually |
| Thermal | no signal cable | thermal mass/contact | temperature is state; heat flows through physical contact |

## Redstone Cable balance
- Same signal quantity: 0..15.
- Explicit INPUT/OUTPUT terminal prevents hidden feedback and makes ports legible.
- Cable does not directly power arbitrary adjacent vanilla blocks.
- Cable-to-cable propagation loses one level per segment after the first segment, preserving a Vanilla-like attenuation cost.
- Primary advantage is geometry/isolation: vertical routing, corners, branches, dense parallel runs.

## Why this matters
Transmission medium is part of the model, not decoration.  Visual geometry must tell the player whether a network is a surface trace, routed cable/fiber, spatial field, or physical thermal state.
