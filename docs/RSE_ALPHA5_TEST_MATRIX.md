# RSE alpha.5 manual test matrix

## Baseline / regression
- Confirm old RSE tab still contains all alpha.4 Redstone instruments.
- Verify Signal Analyzer / Probe / Scope / Conditioner still behave only as Vanilla Redstone devices.
- Place every new-domain wire beside redstone dust and confirm it does not visually/electrically connect as redstone.

## Lapis
1. Place one Lapis Precision Source and 10+ Lapis Signal Line blocks.
2. Change source from 0.50 to 0.55/0.60.
3. Right-click a distant line; value should match source.
4. Break source; network should return to zero.

## Quartz
1. Place Quartz Oscillator + Quartz Timing Trace.
2. Cycle 2/4/8/16/32 tick periods.
3. Inspect trace repeatedly and confirm HIGH/LOW timing follows the selected period.

## Amethyst
1. Place Resonator + Resonance Dust.
2. Set frequency 7.
3. Shift-right-click to pulse; line should briefly report event/frequency 7.
4. Connect two active different-frequency sources and verify conflict does not silently select one.

## Optical
1. Place emitter → fiber chain → receiver.
2. Change intensity and channel.
3. Receiver should display matching values.
4. Confirm optical fiber does not power redstone devices.

## Copper
1. Place voltage source + long copper wire + resistive load.
2. Verify V-level falls by roughly one level every eight wire steps.
3. Change load resistance and read I=V/R and P=VI diagnostics.
4. Confirm copper wire does not behave as redstone dust.

## Iron / magnetic
1. Put Electromagnet adjacent to powered Copper network.
2. Verify B-level follows adjacent Copper voltage.
3. Put Iron Core next to strong electromagnet; it should become magnetized.
4. Put Magnetic Field Sensor within 6 blocks and verify nonzero B-level.
5. Shift-right-click magnetized core to demagnetize.

## Thermal
1. Place Thermal Mass in ordinary environment; expect trend near 20.
2. Put lava/magma/fire nearby; temperature should rise over time.
3. Put ice/blue ice nearby; temperature should fall.
4. Place Temperature Sensor beside Thermal Mass and compare readings.

## Report failures with
- block name
- exact placement layout
- expected behavior
- actual behavior
- terminal stack trace if Minecraft crashes
