# RSE 0.1.0-alpha.8.0.2 — Full Audit / Reliability Baseline

This pass is a reliability release, not a feature-count release.

## Correctness fixes
- Directional Redstone output/query convention fixed across inline processors.
- Analyzer reads explicit test-node values without contamination from stronger adjacent sources.
- PWM left port is INHIBIT; a powered inhibit always forces output OFF.
- Edge Detector / Pulse Shaper final-clear scheduling corrected.
- Sample & Hold, PWM, Edge Detector and Pulse Shaper transient state moved to RuntimeIntStore.
- Range Sensor new placement faces its sensing aperture toward the player-facing front convention.

## Domain/network fixes
- Shared graph budget remains 128 nodes and unloaded chunks are not traversed.
- Isolated Lapis, Quartz, Optical and Copper segments detect multiple active drivers instead of last-writer-wins.
- Copper Junction and Optical Junction carry runtime payloads.
- Copper loads and Optical receivers are terminal graph nodes, not transparent conductors.
- Copper equivalent-load search follows actual cable/junction topology and stops at loads.
- Electromagnet no longer creates a self-waking Copper recompute loop.
- Quartz timing payload stores actual processed period ticks instead of clamping divider results to 32 ticks.
- Instrument probes are deduplicated by physical probe position.

## Measurement fixes
- Copper processor output nodes can be sampled on their real physical output faces.
- Lapis Voltage Transducer rejects invalid processor side-face probing.
- Copper Circuit Meter uses observer-aware node sampling.

## Resource fixes
- Legacy Alpha 4 shaped-recipe key ingredients were normalized to the NeoForge 1.21-1.21.1 ingredient object format.

## Verification gates
Run, in order:

```text
python3 tools/rse_verify.py
python3 tools/rse_redstone_verify.py
python3 tools/rse_full_audit.py
./gradlew compileJava --no-daemon --console=plain
./gradlew clean build --no-daemon --console=plain
```

Static verification is not a replacement for the real NeoForge build and in-game regression tests.
