# Redstone Systems Engineering v0.1 Full Alpha

This overlay turns the initial five-block prototype into the complete v0.1 engineering layer.

## Topology

- Analyzer / Oscilloscope = parallel, non-invasive probes.
- Conditioner / Calibration / Filter / Sample & Hold / Edge / Pulse / PWM = inline processors.
- Signal Tap = junction/routing device.
- Range Sensor = world-to-redstone transducer.

## Blocks

1. Engineering Signal Analyzer
2. Redstone Oscilloscope
3. Signal Conditioner
4. Calibration Module
5. Precision Filter
6. Sample & Hold
7. Edge Detector
8. Pulse Shaper
9. PWM Controller
10. Engineering Signal Tap
11. Engineering Range Sensor

## Core v0.1 flow

World
→ Range Sensor
→ Calibration / Conditioner / Filter
→ Sample & Hold / Edge / Pulse
→ PWM
→ Vanilla redstone / actuator

Analyzer and Oscilloscope probe nodes in parallel.

## Visual identity

All blocks use dedicated 16×16 pixel textures with:
- dark technical casing
- redstone traces
- function-specific front panel
- directional front/back distinction

## Controls

Most blocks:
- Right click: cycle primary mode/parameter.
- Shift-right click: secondary/reset action where implemented.

## Performance rules

- Signal processors use scheduled block ticks.
- Analyzer is passive.
- Oscilloscope samples every 2 ticks and stores only 32 samples.
- No world-wide scanning.
- Range Sensor scans only its configured line.

## v0.2 is intentionally not included

Digital buses, registers, FPGA/LUT, PLC, motor/encoder/PID and IOE factory optimization remain later versions.
