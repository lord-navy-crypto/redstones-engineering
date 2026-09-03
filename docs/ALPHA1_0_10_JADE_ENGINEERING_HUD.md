# RSE Alpha 1.0.10 — Jade Engineering HUD

The first ecosystem adapter built on the Alpha 1.0.10 Engineering Port Contract is a **read-only, server-backed Jade view**.

## Data ownership

```text
RSE physics / topology / control
        ↓
EngineeringPortProvider
        ↓
Jade server data provider
        ↓
Jade client tooltip
```

Jade does not calculate cable power, decide whether two ports connect, change a control output, or mutate the world. It presents the engineering state already owned by RSE.

## Why server-backed

Several RSE observations are runtime values rather than BlockState properties. For example, insulated cable signal is held in the network runtime model. The server therefore resolves the current targeted face through `EngineeringPortProvider` and sends only presentation data to Jade.

This avoids client-side guessing and keeps the no-BlockState-explosion invariant intact.

## Current targeted face

For an RSE block that implements `EngineeringPortProvider`, Jade reports the current targeted face:

- physical side;
- port label;
- engineering domain;
- semantic port kind;
- input/output/bidirectional direction;
- current value, range and normalized percentage when a snapshot exists;
- `PortQuality`;
- whether direct vanilla redstone attachment is legal.

If the targeted face is not a port, Jade explicitly reports it as isolated and still shows the block's total engineering-port count.

Instrument Bus currently exposes a structural/multi-channel port without inventing a single scalar value. Future instrumentation work can add a richer channel summary without changing the core port descriptor.

## Boundary rule

All `snownee.jade` imports live under `dev.redstoneengineering.integration.jade`. Core, physics, signal and block simulation logic remain authoritative and Jade-independent.
