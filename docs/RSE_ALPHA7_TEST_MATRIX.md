# RSE alpha.7 test matrix

## Media
- Lapis / Quartz / Amethyst render as thin floor traces and connect only horizontally.
- Optical / Copper / insulated Redstone render as X/Y/Z 3-D cable segments; junction blocks make bends/tees.
- Vertical stacked Lapis/Quartz/Amethyst traces must NOT silently transmit.
- Crouch-place Optical/Copper/Redstone cable to create Y-axis vertical runs; junctions must connect bends/branches.

## Redstone cable
1. Reference Source 15 -> INPUT terminal -> cable: first cable should read 15.
2. Each subsequent cable segment should eventually attenuate toward 0.
3. Cable beside a lamp must not power the lamp directly.
4. OUTPUT terminal facing lamp/dust should export its cable value.
5. Analyzer and Probe must read cable POWER directly.
6. Two parallel cables separated by one block should not cross-talk.

## Sensors
- Local Light Sensor reacts to torch/glowstone as well as sky light.
- Tank Level Sensor counts contiguous fluid above from 0..15.
- Entity Density Sensor counts nearby living entities, capped at 15.

## Actuator
- Analog Indicator brightness level follows neighboring Redstone 0..15.

## Regression
- Existing 14 Redstone instruments remain Redstone-only.
- Existing Lapis/Quartz/Amethyst/Optical/Copper/Iron/Thermal devices retain alpha.6 behavior.
- ./gradlew build
- ./gradlew runClient
