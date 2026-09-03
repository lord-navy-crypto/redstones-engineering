# RSE Redstone Processing Audit — Alpha 8.0.1 Hotfix

## Fixed functional bugs
1. Minecraft redstone `getSignal(..., side)` directions are query-oriented/backwards. Directional RSE processors were emitting from the physical input side instead of the output side.
2. Side-port redstone connectivity (Sample & Hold trigger/reset, PWM enable, Signal Tap branch) inherited the same direction mismatch.
3. Edge Detector and Pulse Shaper stopped scheduling one tick too early, allowing their output to remain latched HIGH after the requested pulse ended.
4. Engineering Range Sensor directional output used the same reversed-side bug.
5. Lapis -> Redstone Quantizer directional output used the same reversed-side bug.
6. Redstone Cable Terminal output used the same reversed-side bug.
7. Redstone -> Lapis Scaler advertised its physical input port on the wrong side.
8. Redstone Cable Network updated an output terminal's POWER state without notifying the vanilla-redstone neighbor, so cable->vanilla transitions could fail to propagate.

## Still technical debt (not changed in this hotfix)
Several legacy redstone processors keep dynamic runtime values in BlockState (PWM phase, Sample & Hold held/trigger state, pulse remaining counters). This is much smaller than the old alpha.6 state explosion but should move to runtime state in a dedicated cleanup pass.

## Regression tests
- Reference Source 4 -> Conditioner GAIN x2 -> Analyzer = 8.
- Conditioner front dust powers; rear dust never receives the processed output.
- Calibration FULL preserves 4; LOW 0..7 maps 4 near 9.
- Precision Filter rate=1 approaches 0->4 as 1,2,3,4.
- Sample & Hold rising edge captures the current input, keeps it after input changes, reset clears it.
- Edge Detector emits a finite pulse and autonomously returns to 0.
- Pulse Shaper width=N emits a finite pulse and autonomously returns to 0.
- PWM input 0 always off; 15 always on; intermediate input toggles according to duty cycle.
- Signal Tap outputs the same value on THROUGH and TAP physical ports.
- Range Sensor emits only from its rear output face.
- Lapis -> Redstone Quantizer emits only from its front redstone output face.
- Cable OUTPUT Terminal propagates cable power changes into vanilla dust without needing an unrelated neighbor update.
