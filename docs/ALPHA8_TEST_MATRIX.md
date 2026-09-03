# Alpha 8 test matrix

## Build gate
- `python3 tools/rse_verify.py` → PASS
- `./gradlew compileJava --no-daemon --console=plain` → PASS
- `./gradlew clean build --no-daemon --console=plain` → PASS
- `./gradlew runClient --no-daemon` → main menu

## Sensor model
- FAST changes faster than PRECISION.
- PRECISION is less noisy than FAST.
- BALANCED/PRECISION show one-sample latency.
- No alpha.8 live measurement is stored as a high-cardinality BlockState.

## Transducers
- Thermal Mass → Temperature Transducer → Lapis Trace.
- Electromagnet/Permanent Magnet → Magnetic Transducer → Lapis Trace.
- Optical Fiber/Receiver → Optical Transducer → Lapis Trace.
- Copper Wire → Voltage Transducer → Lapis Trace.
- Range target inside selected range → valid Lapis; no target → INVALID.

## Conversion
- Lapis 0.00 → Redstone 0.
- Lapis 0.50 → approximately Redstone 8.
- Lapis 1.00 → Redstone 15.
- Redstone 0 → Lapis 0.00.
- Redstone 15 → Lapis 1.00.

## Sampling
- Quartz LOW→HIGH edge captures current Lapis input.
- Lapis changes between clock edges do not change held output.
- Invalid Lapis sampled on an edge yields invalid held output.

## Regression
- Alpha.7.3 surface traces still reject Y-axis edges only.
- `NetworkKernel.MAX_NODES == 128`.
- 3-way passive cable still requires Junction/Splitter according to domain rules.
