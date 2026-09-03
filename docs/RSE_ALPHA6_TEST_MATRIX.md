# RSE alpha.6 Engineering Test Matrix

## Global invariants
- Existing 14 Redstone instruments still interact only with Vanilla Redstone / RSE Redstone instrumentation.
- Non-redstone domain blocks do not connect as Vanilla Redstone conductors.
- No domain silently resolves an explicit conflict when the rule says INVALID/conflict.
- 50–100 block test networks should not create unbounded graph traversal; DomainNetwork remains bounded.

## Lapis
1. Precision Source 0.50 -> line reports 0.50 valid.
2. Two unequal sources on same line -> INVALID.
3. Noise Source baseline 0.50, noise ±0.10 -> observed values remain bounded 0.40–0.60.
4. Low-pass filter after Noise Source -> output variance visibly decreases.
5. Smaller alpha -> more smoothing and more lag after a step.
6. Precision Meter attached to intended test point -> reports only that point.

## Quartz
1. Clean oscillator period 8t -> Timing Trace toggles accordingly.
2. Lab oscillator nominal 8t, jitter 0 -> Stability Monitor error approaches 0.
3. Lab oscillator jitter ±2t -> Stability Monitor shows nonzero period error over time.
4. Divider ÷2 -> output period doubles.
5. Phase Delay 4t -> delayed pulse appears after four ticks.
6. Two incompatible oscillator periods on one raw timing segment -> invalid clock domain.

## Amethyst
1. Resonator f=6, A=12 -> dust carries f=6 and attenuated amplitude with distance.
2. Frequency Filter target 6 passes f=6 and rejects f=7.
3. Tuned Resonator f0=6, high Q -> strong response at f=6, narrower response off resonance.
4. Lower Q -> broader response region.
5. Spectrum Analyzer near mixed traffic -> reports dominant band and active-band count.

## Optical
1. Emitter intensity 15 -> Fiber/Receiver show distance-dependent loss.
2. Splitter with input 12 -> both branches near 6 before downstream line loss.
3. Channel Filter target 4 passes channel 4; rejects other channel.
4. Attenuator loss 3 reduces output intensity by roughly 3 indices.
5. Equal strongest conflicting optical channels -> invalid where conflict is resolved.
6. Power Meter on explicit fiber point reports local intensity/channel.

## Copper
1. Voltage Source 12 -> nearby wire reads near 12.
2. Series resistor + load -> output follows divider direction; larger series R lowers Vout.
3. Circuit Meter -> V/R/I/P values obey I≈V/R and P≈VI.
4. Capacitor step from 0 to source -> output rises over time, not instantaneously.
5. Remove source -> capacitor output decays over time.
6. Fuse rating below estimated load current -> TRIPPED and output segment loses power.
7. Shift-click Fuse reset -> returns armed if overload removed.

## Iron / Magnetic
1. Electromagnet without Copper input -> weak/zero field.
2. Increase Copper voltage -> measured field increases.
3. Permanent Magnet strength control -> sensor field changes.
4. Static field next to Induction Coil -> after settling, emf returns to zero/low.
5. Change field strength or move source -> Induction Coil produces transient Copper emf.
6. More turns-index -> larger emf for the same flux change, subject to 0–15 saturation.
7. Gradient Meter closer to source -> gradient magnitude becomes larger in at least one axis.

## Thermal
1. Two Thermal Mass blocks, C=1 vs C=4, same heater -> C=1 changes temperature faster.
2. Heater with no Copper -> approaches ambient.
3. Increase Copper voltage -> heater temperature/power rises.
4. Increase heater resistance at same voltage -> electrical power and heating decrease.
5. Radiator next to hot Thermal Mass -> pulls toward ambient.
6. Radiator never cools an ambient mass below ambient by itself.
7. Calorimeter across heating interval -> positive ΔT; cooling interval -> negative ΔT.
8. Lava/fire/magma/campfire increase environmental target; ice variants decrease it.

## Build validation
- `./gradlew build --console=plain` -> PASS
- `./gradlew runClient` -> PASS
- Creative tab contains all legacy Redstone tools + alpha.5 foundations + alpha.6 depth tools.
- No missing-texture purple/black checkerboards.
