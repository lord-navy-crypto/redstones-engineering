# Alpha 1.0.13 — Copper Circuit Topology & Control Renovation III

Alpha 1.0.13 continues the legacy renovation by bringing the early copper electrical system onto the same Engineering Port Contract used by modern RSE instrumentation.

## Engineering topology

Copper remains a simplified macroscopic electrical domain with bounded voltage-equivalent values from 0 through 15. The renovation does not replace DomainNetwork or CircuitPhysics. It makes their existing physical roles explicit and observable.

### Axial two-port processors

Copper Series Resistor, Copper Capacitor, and Copper Fuse use one shared contract:

```text
BACK  = COPPER / ELECTRICAL / INPUT
FRONT = COPPER / ELECTRICAL / OUTPUT
SIDES = isolated from the processor transfer path
```

Their runtime output remains owned by the component simulation. EngineeringPort snapshots only observe it.

### Source node

Copper Voltage Source remains a multi-face source node because the existing network model intentionally lets a source energize any adjacent compatible copper conductor. Every exposed face is therefore an OUTPUT port rather than inventing a directional restriction that the simulation does not have.

### Terminal load

Copper Resistive Load remains a terminal sink. Compatible copper can feed it from any face, but DomainNetwork does not propagate through the load. Its six physical ports are INPUT ports.

### Circuit meter

Copper Circuit Meter is non-invasive. Only its FACING side is a MEASUREMENT input. It samples the target through DomainNetwork without becoming a conductor.

## Dependency ownership

Jade consumes EngineeringPortProvider and can now display the copper topology without copper-specific HUD code. Fusion may later render connected copper geometry, but it must follow RSE topology rather than define connectivity.

## Runtime verification

Minecraft GameTests cover:

1. BACK/FRONT EngineeringPort direction for resistor, capacitor, and fuse.
2. Source -> wire -> resistor -> wire -> load voltage propagation.
3. Attenuation through a series resistor under a real load.
4. Fuse trip and protected output cutoff.
5. Rejection of a SIDE feed on an axial processor.
6. All earlier redstone cable, converter, and directional-I/O GameTests.

This keeps the project rule: if a topology behavior can be proven in Minecraft runtime, `runGameTestServer` is preferred over source-token verification alone.
