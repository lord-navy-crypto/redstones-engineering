# RSE Crafting Progression

RSE recipes are organized by engineering difficulty rather than by raw rarity alone. Higher-tier devices should reuse lower-tier engineering components so survival progression mirrors how real systems are assembled from instruments, conditioning, control and actuators.

## Tier 1 — Basic Measurement

Goal: make measurements before building automation.

Representative devices:
- Measurement Probe
- Instrumentation Bus Cable
- Instrumentation Signal Analyzer
- Analog Redstone Indicator
- basic sensors/transducers

Typical materials: copper, iron, redstone, glass, quartz.

## Tier 2 — Signal Processing & Data Acquisition

Goal: condition and capture signals.

Representative devices:
- Analog Signal Conditioner
- Instrument Calibration Reference
- Precision Low-Pass Filter
- Sample-and-Hold Module
- 4-Channel Engineering Oscilloscope
- 4-Channel Logic Analyzer

Typical dependencies: Tier-1 analyzer/probes plus quartz timing, comparators/observers and glass.

## Tier 3 — Control & Mechatronics

Goal: close the loop.

Representative devices:
- Discrete PID Controller
- PWM Signal Modulator
- Position/Velocity Servo Actuator
- Position Feedback Transducer
- Pneumatic Proportional Control Valve

Typical dependencies: signal-conditioning components, comparators, pistons, copper and redstone.

## Tier 4 — System Safety & Reliability

Goal: make failures visible and survivable.

Representative devices:
- Fault Watchdog Timer
- Redundant Signal Voter
- Latched Fault Memory
- Pneumatic Safety Relief Valve
- shielded instrumentation

Typical dependencies: Tier-2/3 devices plus robust materials and redundancy-oriented components.

## Tier 5 — Operations & Integrated Systems

Goal: reason about the entire production/control system.

Representative devices:
- Production Operations Monitor
- communication buses and radio links
- multi-sensor networks
- integrated pneumatic/mechatronic cells

Typical dependencies: clock/timing components, instrumentation and control blocks rather than only raw ingots.

## Representative recipe ladder

| Device | Tier | Engineering dependency |
| --- | ---: | --- |
| Instrumentation Signal Analyzer | 1 | copper + glass + quartz + redstone |
| 4-Channel Engineering Oscilloscope | 2 | Signal Analyzer + glass + quartz |
| 4-Channel Logic Analyzer | 2 | Signal Analyzer + Observer + Comparator |
| Discrete PID Controller | 3 | Signal Conditioner + Comparator + Quartz |
| Position/Velocity Servo Actuator | 3 | Piston + copper + comparator/redstone |
| Pneumatic Proportional Control Valve | 3 | Pneumatic Isolation Valve + Comparator + copper |
| Production Operations Monitor | 5 | Clock + Comparator + Observer + instrumentation materials |

The key balancing rule is **dependency before rarity**: a sophisticated controller should normally require a prior measurement/conditioning component even when all of its raw materials are easy to obtain.
