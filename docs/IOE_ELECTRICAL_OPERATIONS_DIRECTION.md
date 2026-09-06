# RSE Industrial & Operations Engineering + Electrical Direction

## Purpose

RSE has reached a point where additional value should come primarily from deeper system behavior rather than more low-level components or new engineering domains. The next development phase therefore treats existing RSE devices as a small industrial cyber-physical system and emphasizes operations, reliability, process control, diagnostics, maintenance, optimization, safety, and electrical/control infrastructure.

This document is a design constraint for future work. It does **not** turn RSE into a factory-management mod and it does **not** replace the vanilla-first 0..15 redstone boundary. The goal is to make existing engineering devices form complete, inspectable, testable systems.

## Authoritative engineering basis

### Industrial & Systems / Operations Engineering

The Institute of Industrial and Systems Engineers (IISE) Body of Knowledge identifies major areas including:

- Work Design & Measurement
- Operations Research & Analysis
- Engineering Economic Analysis
- Facilities Engineering & Energy Management
- Quality & Reliability Engineering
- Ergonomics & Human Factors
- Operations Engineering & Management
- Supply Chain Management
- Safety
- Information Engineering
- Design & Manufacturing Engineering
- System Design & Engineering

Source: IISE, *Industrial and Systems Engineering Body of Knowledge*.
https://www.iise.org/Details.aspx?id=43631

ABET's Industrial Engineering program criteria require design, analysis, operation, and improvement of integrated systems, together with productivity analysis, operations research, probability/statistics, engineering economy, and human factors.

Source: ABET, *Criteria for Accrediting Engineering Programs, 2026-2027*.
https://www.abet.org/accreditation/accreditation-criteria/criteria-for-accrediting-engineering-programs-2026-2027/

University of Michigan Industrial & Operations Engineering highlights operations research and analytics, quality control and reliability engineering, occupational safety/ergonomics, and human-systems integration as core program areas. Its quality/reliability area explicitly emphasizes data-driven modeling, simulation, quality control, reliability, and maintenance decisions under uncertainty.

Source: University of Michigan IOE, *Program areas and curriculum*.
https://ioe.engin.umich.edu/graduate/masters-programs/industrial-and-operations-engineering-masters/masters-program-curriculum/

### Electrical / Controls contribution

The electrical contribution to RSE should remain focused and system-relevant rather than expanding into a second independent electrical-component catalog.

ABET electrical/electronic engineering-technology criteria identify control systems, instrumentation systems, communications systems, power systems, and energy systems as appropriate areas of analysis, design, implementation, operation, and maintenance.

Source: ABET, *Criteria for Accrediting Engineering Technology Programs, 2026-2027*.
https://www.abet.org/accreditation/accreditation-criteria/criteria-for-accrediting-engineering-technology-programs-2026-2027/

The University of Texas at Austin ECE curriculum illustrates useful industrial intersections: power-system instrumentation, motors and machines, protective relaying, fault analysis, controls, and economic operation of power systems.

Source: UT Austin ECE course catalog.
https://catalog.utexas.edu/general-information/coursesatoz/ece/

### Smart manufacturing / operations basis

NIST smart-manufacturing work emphasizes performance metrics, process control, prognostics and health management, diagnostics, maintenance, operational optimization, interoperability, and trustworthy digital representations of manufacturing systems.

Sources:

- NIST, *Smart Manufacturing Operations Planning and Control Program*
  https://www.nist.gov/programs-projects/smart-manufacturing-operations-planning-and-control-program
- NIST, *Prognostics and Health Management for Reliable Operations in Smart Manufacturing (PHM4SM)*
  https://www.nist.gov/programs-projects/prognostics-and-health-management-reliable-operations-smart-manufacturing-phm4sm
- NIST, *Digital Twins for Advanced Manufacturing*
  https://www.nist.gov/programs-projects/digital-twins-advanced-manufacturing

## RSE design interpretation

The research above maps naturally onto the systems RSE already has:

| Engineering concept | RSE implementation direction |
| --- | --- |
| Productivity analysis | throughput, cycle-time evidence, run/stop state, queue/WIP |
| Operations management | sequence state, bottleneck/constraint identification, recovery workflow |
| Operations research | bounded scheduling/priority decisions and comparative scenarios, not a universal optimizer block |
| Quality & reliability | fault injection, failure evidence, downtime, recovery, maintenance indicators |
| Facilities & energy | electrical load/protection/energy-operation scenarios using existing Copper/electrical devices |
| Safety | interlocks, alarm severity, trip/reset workflow, first-out cause |
| Information engineering | event history, topology diagnostics, traceable system state |
| Human factors | clear operator acknowledgement/reset semantics and legible diagnostics |
| Control/instrumentation | sensors -> conditioning -> sampling -> PID/control -> actuator |
| Electrical protection | source -> protection -> switching -> load -> current/voltage monitoring -> trip/alarm |
| Communications | serializer/link/receiver plus latency/dropout/channel/topology faults |

## Content budget

For the next consolidation phase:

- New engineering domains: **0**
- New blocks: target **0-3 maximum**
- Default solution: extend observers, diagnostics, UI, configuration, reference systems, tests, and shared infrastructure first.
- A new block must represent a physical or operator role that cannot be expressed cleanly through an existing block plus configuration/UI.

## First implementation: IOE Operations Assessment

The existing `OperationsMonitorBlock` already observes:

- machine running state;
- completed-cycle pulses;
- queue/WIP proxy on horizontal inputs;
- throughput over its rolling window;
- downtime;
- starvation evidence;
- blocked/fault evidence;
- high-queue running evidence;
- coarse system state.

`IndustrialOperationsAssessment` now provides a read-only system-level projection over those authoritative values. It adds:

- queue-pressure percentage derived from the existing 0..15 queue input;
- a dominant operational constraint classification:
  - `NONE`
  - `STARVED`
  - `BLOCKED`
  - `HIGH_WIP`
  - `UNSTABLE`
  - `SAFETY_LIMITED`
  - `FAILED`
- a compact IOE summary suitable for later dashboard/Jade/diagnostics use.

It deliberately does **not** claim to calculate OEE. True OEE requires at least defensible availability, performance, and quality terms, including planned-production-time and good/reject-count semantics that RSE does not yet model authoritatively.

## Five reference systems to build before adding broad new content

### 1. Tank Process Control

```text
Tank Level Sensor
        -> Calibration / Conditioning
        -> PID Controller
        -> Pneumatic Proportional Valve
        -> Tank Process
```

System layer:

```text
High Level Alarm
High-High Safety Interlock
Fault Injector
Topology Debugger
Operations Monitor
Commissioning Evidence
```

Engineering focus: measurement, closed-loop control, process safety, alarm handling, commissioning and disturbance recovery.

### 2. Pneumatic Motion Cell

```text
Compressor
 -> Reservoir
 -> Regulator
 -> Solenoid / Proportional Valve
 -> Cylinder
 -> Position Sensor
 -> Controller
```

System layer:

- travel limit;
- pressure fault;
- timeout;
- safety interlock;
- watchdog;
- alarm;
- sequence control;
- operations monitoring.

Engineering focus: automation sequencing, actuator availability, failure propagation and recovery.

### 3. Thermal Process

```text
Heater
 -> Thermal Mass
 -> Temperature Sensor
 -> PID
 -> Heater command
```

Safety path:

```text
Overtemperature
 -> Safety Interlock
 -> Emergency Trip
```

Engineering focus: dynamic response, tuning, overshoot, thermal safety and controlled recovery.

### 4. Motor / Electrical Protection Cell

Use existing Copper/electrical infrastructure instead of adding a large new electrical catalog.

```text
Voltage Source
 -> Fuse / Breaker
 -> Relay / switching element
 -> Motor / electrical load
```

Observation/protection path:

```text
Current / voltage abnormality
 -> Trip logic
 -> Alarm Processor
 -> Event / fault evidence
 -> Operations impact
```

Engineering focus: instrumentation, protective coordination as a game abstraction, load availability, downtime, maintenance and facilities/energy operations.

### 5. Communication Fault Lab

```text
Data Source
 -> Serializer
 -> Differential / Radio / Optical Link
 -> Receiver
```

Inject controlled faults such as:

- latency;
- dropout;
- attenuation;
- wrong channel;
- wrong direction;
- broken link.

Diagnose with existing Logic Analyzer, Topology Debugger, Alarm Processor, and Operations Monitor.

Engineering focus: communications reliability and the operational consequences of degraded information flow.

## Operations Dashboard direction

Do not create multiple dashboard blocks. Prefer a single `Engineering Operations Console` only if a dedicated world-space operator station becomes necessary; otherwise use an existing diagnostics/UI entry point first.

The dashboard should be a downstream observer of existing authoritative systems:

```text
SYSTEM STATUS

PROCESS       RUNNING
SEQUENCE      STEP 4/8
CONTROL       HEALTHY
INTERLOCK     CLEAR
ALARMS        1 ACTIVE / 1 ACK
TOPOLOGY      0 FAULTS
THROUGHPUT    82 cycles/min
CONSTRAINT    HIGH_WIP
DOWNTIME      3.2 s
```

The dashboard must not become a second simulation engine.

## Event history and first-out analysis

PID commissioning history already exists and is intentionally transient diagnostic evidence. System event history is a different concern: it should explain the causal sequence of plant-level events.

Example:

```text
T=18420  SENSOR      Tank Level       HIGH
T=18422  ALARM       Level High       RAISED S2
T=18423  INTERLOCK   Fill Permit      TRIPPED
T=18424  SEQUENCE    Filling          PAUSED
T=18431  OPERATOR    Level High       ACKNOWLEDGED
T=18455  SENSOR      Tank Level       NORMAL
T=18456  INTERLOCK   Fill Permit      READY
T=18460  ALARM       Level High       CLEARED
```

First-out analysis should identify the earliest meaningful cause and distinguish it from downstream consequences.

```text
FIRST OUT: PNEUMATIC PRESSURE LOW

Consequences:
-> actuator unavailable
-> sequence timeout
-> position mismatch
-> watchdog trip
```

## Root-cause topology direction

The Topology Debugger should evolve from local fault classification into bounded causal tracing, not into another generic analyzer block.

Example:

```text
Cylinder unavailable
-> Valve output = 0
-> Safety Interlock inhibited
-> Pressure Switch = LOW
-> Pneumatic branch pressure invalid
-> Pipe disconnected at bounded traced location
```

Traversal must remain bounded and observer-only.

## Persistence contract to audit

The next lifecycle audit must explicitly classify each state as transient or durable:

| State | Chunk unload | World save/load | Server restart | Block removal |
| --- | --- | --- | --- | --- |
| Sequence current step | TBD | TBD | TBD | clear |
| Alarm latched state | TBD | TBD | TBD | clear |
| Alarm acknowledgement | TBD | TBD | TBD | clear |
| First-out cause | TBD | TBD | TBD | clear |
| Operations counters | TBD | TBD | TBD | clear |
| Fault configuration | TBD | TBD | TBD | clear |
| System event history | TBD | TBD | TBD | clear/archive policy |
| PID commissioning evidence | currently transient | currently transient | currently transient | clear |

No persistence behavior should be implied until code and save semantics actually support it.

## Testing direction

Do not optimize for GameTest count alone. Add cross-system and lifecycle tests such as:

```text
Fault
 -> Alarm
 -> Interlock
 -> Sequence stop
 -> Operator acknowledgement
 -> Fault clear
 -> Reset
 -> Restart
```

Also prioritize:

- long-run stability;
- network cut/rejoin;
- chunk unload/reload;
- save/reload once persistence is implemented;
- two-player configuration conflicts;
- bounded topology/root-cause traversal;
- no neighbor-update storms;
- observer layers never writing simulation state.

## Milestone direction

### Alpha 1.0.21 — Consolidation & Industrial Operations Foundation

- no new domain;
- IOE operations assessment over existing monitor data;
- reference-system specifications;
- Creative/JEI/progression organization;
- persistence audit;
- verifier-core consolidation;
- begin electrical-protection reference cell using existing components.

### Alpha 1.0.22 — Operations & Event Intelligence

- system event timeline;
- first-out analysis;
- unified operations dashboard;
- Alarm/Sequence/Interlock/Topology integration;
- root-cause trace prototype.

### Alpha 1.0.23 — System Acceptance & Reliability

- Tank, Pneumatic, Thermal, Motor/Electrical and Communications reference plants;
- long-run tests;
- fault-recovery tests;
- lifecycle tests;
- system-level acceptance criteria.

## Rule for future additions

Before registering another block, ask:

> Does this represent a genuinely new physical, control, measurement, safety, maintenance, or operator role that cannot be expressed by an existing RSE device plus configuration, UI, or system logic?

If the answer is no, deepen the existing system instead.
