# RSE alpha.6.1 Stability Hotfix

This hotfix addresses startup and runtime pressure discovered in alpha.6.

## Root cause
Several high-cardinality runtime measurements were incorrectly encoded as BlockState properties. That caused a combinatorial state explosion during block registration/model preparation.

Approximate enumerated RSE states before hotfix: ~775,600.
Approximate enumerated RSE states after hotfix: ~5,100.

## Changes
- Runtime measurements moved out of BlockState for:
  - Lapis noise current value
  - Lapis low-pass output/validity
  - Quartz stability history
  - Quartz divider runtime counters
  - Quartz phase-delay runtime counters
  - Copper capacitor charge
  - Copper series-resistor output
  - Induction coil previous flux / EMF
  - Thermal calorimeter history
  - Optical fiber/receiver live intensity/channel
  - Amethyst resonator pulse activity
- Non-redstone domain graph budget reduced from 512 to 128 nodes per recomputation.
- Network scans avoid stepping into unloaded chunks.
- Basic Quartz oscillator recomputation now happens on half-period transitions instead of every game tick.
- Several graph-driving processors poll every 2 ticks instead of every tick.

## Safety rule going forward
BlockState is reserved for small, model/topology-relevant state. High-resolution measurements, histories, counters and transient values must use BlockEntity/runtime data instead.
