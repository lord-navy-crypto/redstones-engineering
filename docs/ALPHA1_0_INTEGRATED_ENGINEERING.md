# Redstone Systems Engineering — Alpha 1.0 Integrated Engineering

This overlay advances the reconstructed Alpha 8.0.2 baseline toward a coherent cyber-physical engineering sandbox while preserving vanilla redstone as the foundation.

## Design rule

RSE does not replace redstone. Vanilla 0–15 strength, ticks, pulses, spatial wiring, repeaters, comparators, and ordinary automation remain the cheapest and simplest control layer. Advanced systems exist to solve problems that redstone alone does not model explicitly: precision measurement, communication, feedback, physical-domain coupling, reliability, and operations monitoring.

## Engineering references

- University of Michigan EECS 461 — Embedded Control Systems: digital I/O, ADC, PWM, timing/frequency measurement, CAN, sensors, actuators, feedback and motor control.
  https://web.eecs.umich.edu/~jfr/embeddedctrls/
- Carnegie Mellon 18-474 — Embedded Control Systems: event/clock sampling, PWM, PID, state feedback, state estimation, setpoint and trajectory tracking, motors and encoders.
  https://courses.ece.cmu.edu/18474
- UIUC ECE 110 / ECE 385 — measurement/modeling, sensors, circuits, digital logic, sequential systems, timing, datapath/controller design and debugging.
  https://courses.grainger.illinois.edu/ECE110/fa2026/
  https://courses.grainger.illinois.edu/ECE385/fa2026/
- UW–Madison Mechanical Engineering — Robotics, Controls and Sensing: physical systems evolving over time, sensing, actuation, control and cyber-physical integration.
  https://engineering.wisc.edu/departments/mechanical-engineering/research/robotics-controls-and-sensing/
- NIST Cyber-Physical Systems Framework — integration of computational, physical, analog/digital and human components.
  https://www.nist.gov/publications/framework-cyber-physical-systems-volume-1-overview
- ISA-95 — separation of physical process, sensing/manipulation, supervisory control and manufacturing operations layers.
  https://www.isa.org/standards-and-publications/isa-standards/isa-95-standard

## Implemented in this Alpha 1.0 overlay

### Digital communications
- 8-bit Data Bus (runtime byte payload; no 256-state BlockState)
- Redstone → Byte Encoder
- Byte → Redstone Decoder
- Serial Data Line
- Serializer / Deserializer
- Differential Pair + Driver + Receiver
- Digital Regenerator
- Shielded Instrumentation Cable

### Control and reliability
- PID Controller
- Watchdog
- Redundant Voter
- Fault Latch
- Servo Actuator
- Servo Position Sensor

### Pneumatics
- Air Compressor
- Pneumatic Pipe
- Air Reservoir
- Pressure Regulator
- Pneumatic Receiver

### Minecraft-native vibration engineering
- Sculk Vibration Interface
- Slime Vibration Conduit
- Honey Vibration Damper
- Mechanical Exciter
- Mechanical Vibration Receiver

### Fluid acoustics
- Hydroacoustic Tube
- Hydroacoustic Exciter
- Hydroacoustic Receiver
- Discrete water / milk-model / lava medium behavior

### Wireless and optics
- Radio Transmitter / Receiver
- Four-channel radio model with collision detection and bounded range
- Registry-based radio lookup rather than cubic receiver scans
- Free-space Optical Transmitter / Receiver with line-of-sight obstruction

### Minecraft-fictional Soul Flux
- Soul Soil Flux Conduit
- Soul Sand Flux Reservoir
- Soul Flux Injector
- Soul Flux Meter

Soul Flux is explicitly fictional Minecraft physics, not a real-world claim. It is modeled as a slow, persistent, decaying stored state.

### Molecular communication
- Molecular Cloud Receiver sampling vanilla AreaEffectCloud entities
- Concentration proxy from cloud geometry/distance

This release intentionally does **not** simulate individual molecules. Full diffusion/advection/reaction chemistry remains future work.

### End-game thermal signalling
- Diamond Shard: 1 diamond → 16 shards
- Ender-Diamond Phonon Conduit
- Thermal Pulse Encoder / Receiver
- End Stone + Obsidian + Diamond Shards provide progression/economic gating

The Phonon Conduit is a Minecraft-fictional high-effective-thermal-conductivity composite. It is not presented as a real superconductor.

### Operations engineering
- Operations Monitor
- Rising-edge cycle count
- rolling 60-second cycle count
- utilization proxy

## Architecture and stability rules

- High-cardinality dynamic values stay out of BlockState.
- Runtime values use RuntimeIntStore / InformationRuntime.
- Bounded networks use NetworkKernel.MAX_NODES.
- No unloaded-chunk traversal.
- Multi-driver ambiguity must fail/diagnose rather than silently become last-writer-wins.
- Processors/end devices are not automatically transparent network conductors.
- Cross-domain conversions should be explicit through transducers/interfaces.
- Event/scheduled updates are preferred over unbounded continuous scans.

## Static validation status

At packaging time, the Alpha 8.0.2 reconstructed baseline plus this overlay reported:
- 116 registered blocks
- 159 Java source files
- 655 JSON resources
- Redstone regression verification: PASS
- Full static audit: PASS
- Alpha 1.0 verification: PASS
- 43 Alpha 1.0 blocks checked
- JSON parsing: PASS
- High-cardinality BlockState guard: PASS

These are static checks only. A real NeoForge Java compile, game launch, new-world placement test, and multiplayer/performance test must still be run on the target Minecraft/NeoForge development environment.

## Intentionally deferred beyond this overlay

- Full molecular diffusion/advection/reaction-diffusion simulation
- Full fluid dynamics / CFD
- Full pneumatic thermodynamics
- Full electromagnetic field solver
- SPICE/MNA electrical solver
- Damage-capable ballistic communication; physical courier concepts, if ever added, should remain non-damaging engineering entities
- Full CPU/large memory architecture
- State estimation / trajectory planning beyond the compact servo primitive
- Full SCADA/MES interface and advanced IOE optimization

The goal is an engineering abstraction that is measurable, debuggable and composable—not an attempt to simulate every microscopic physical law.
