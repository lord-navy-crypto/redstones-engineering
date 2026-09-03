# RSE Alpha 1.0.10 — Engineering Port Architecture

Alpha 1.0.10 begins the large post-dependency renovation. The goal is not to add more blocks; it is to give existing and future blocks one shared engineering contract.

## Architectural rule

The authoritative direction is:

```text
RSE physics / control / topology
        ↓
EngineeringPort + EngineeringPortSnapshot
        ↓
Jade / JEI / GeckoLib / Cloth Config / Fusion adapters
```

Never reverse that dependency. A Fusion visual connection must not decide whether a cable is physically connected. A Jade tooltip must not become the source of a process value. Client-only libraries must not leak into server/common engineering packages.

## Static descriptor vs runtime value

`EngineeringPort` stores low-cardinality, structural information:

- physical side;
- engineering domain;
- semantic port kind;
- input/output/bidirectional direction;
- whether direct vanilla redstone attachment is legal;
- engineering unit label.

`EngineeringPortSnapshot` stores dynamic information:

- current value;
- expected minimum and maximum;
- normalized 0..1 representation;
- `PortQuality`.

This keeps measurements out of BlockState and preserves RSE's no-state-explosion invariant.

## Port quality

The first shared quality vocabulary is:

- `VALID`
- `NO_SIGNAL`
- `SATURATED`
- `STALE`
- `FAULT`
- `DOMAIN_MISMATCH`
- `TOPOLOGY_ERROR`

Later metrology work can extend snapshots with uncertainty, repeatability, drift and sample age without redesigning every machine.

## Domain and direction compatibility

`PortCompatibility` evaluates direct RSE-to-RSE contact. Direct connection requires:

1. the same engineering domain;
2. at least one transmitting side and one receiving side;
3. neither side marked isolated.

Cross-domain conversion remains explicit through terminals/transducers/converters.

## First legacy migration wave

### Directional signal processors

Every `DirectionalSignalBlock` now automatically reports:

- BACK = `INPUT`, redstone analog, 0..15;
- FRONT = `OUTPUT`, redstone analog, 0..15.

This immediately upgrades the whole subclass family without copy-pasting port logic into each processor.

### Early sensors

Engineering Light Sensor, Entity Density Sensor and Tank Level Sensor keep their existing all-side vanilla output behavior in this compatibility milestone, but now expose that behavior explicitly through `EngineeringPortProvider`. A later physical-facing migration can therefore be deliberate rather than inferred from legacy code.

### Analog Process Indicator

The early indicator remains omnidirectional input for save/resource compatibility in 1.0.10, but the new contract labels those sides `LEGACY_INPUT`. A later model/state migration can safely reduce this to one back input plus one display face.

### Wiring

Insulated Redstone Cable exposes only currently connected faces as bidirectional `REDSTONE` ports. Instrument Cable exposes connected faces as bidirectional `INSTRUMENT_BUS` ports. Runtime signal values remain outside topology BlockState.

## Required dependency responsibilities

- **Jade**: read port descriptors/snapshots for engineering HUD and server-backed diagnostics.
- **JEI**: show engineering progression, recipe/use relationships and later machine information.
- **GeckoLib**: animate servo/cylinder/valve state derived from RSE runtime models.
- **Cloth Config**: configure user-facing diagnostics and tuning options without hard-coding custom screens everywhere.
- **Fusion**: render topology-aware connected visuals from RSE-owned connection state.

The next integration wave should add adapters against this contract instead of placing third-party API calls inside physics/control code.
