# RSE alpha.7.1 Stability + Transmission Test Matrix

Run these in a new test world before opening a large existing engineering world.

## Startup safety
- Main menu opens normally.
- Java memory does not continuously climb while idle at main menu.
- New world loads without long stalls.

## Redstone cable
- Vanilla dust -> INPUT terminal -> cable -> OUTPUT terminal -> lamp.
- Cable preserves 0, 1, 7, 15 semantics subject to configured cable attenuation.
- Straight cable does not power unrelated adjacent blocks along its route.
- X/Y/Z segments obey their axis.
- Junction turns and branches intentionally.
- Parallel cables do not cross-talk.

## Redstone instruments
- Analyzer reads vanilla dust.
- Analyzer reads insulated cable.
- Probe reads insulated cable.
- Reference Source steps 0..15.

## Sensors
- Local Light Sensor responds to actual local brightness changes.
- Tank Level Sensor responds to column height.
- Entity Density Sensor responds to living-entity count in its region.
- Engineering Range Sensor remains functional.

## Actuation/display
- Analog Indicator brightness/state tracks 0..15 input.
- Existing Electromagnet and Thermal Heater still work after the stability changes.

## Regression / performance
- Build a 50-block redstone cable run: no runaway updates.
- Build a branched cable network: no infinite recursion.
- Place multiple Quartz/Lapis/Optical/Copper devices: main-thread responsiveness remains normal.
- Move far enough that neighboring chunks unload: domain scans must not force-load them.

## Required commands
./gradlew clean build --no-daemon --console=plain
./gradlew runClient --no-daemon --stacktrace
