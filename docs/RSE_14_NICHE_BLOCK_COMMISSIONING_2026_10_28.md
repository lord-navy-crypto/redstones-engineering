# RSE 14 formerly niche blocks — unique commissioning functions (2026-10-28)

## Status

Implemented as real right-clickable **server-side inspection workflows**, not screenshots or static lore. All 14 block classes now call `NicheDeviceCommissioning.send` once. The shared inspection library reads existing simulation and evidence snapshots; it does **not** change any physics, block ID, saved world state, recipes, existing controls or output signals. Reports go to the full chat rather than a truncated actionbar. The strict source gate `tools/rse_14_block_commissioning_verify.py` runs in CI.

**This is a functional diagnostic/commissioning enhancement, not a claim of new energy transmission or stronger production mechanics.** The existing distinct physics of these blocks remain intact. Behavioral Minecraft client testing and GameTests are still required.

## Fourteen independent field workflows

| Block ID | New practical check | Player action |
| --- | --- | --- |
| `engineering_compass` | Six-direction survey of adjacent engineering-device port declarations, domains and unloaded-neighbor count. Explicitly not a connectivity test. | Shift + right-click; ordinary right-click still reports world datum |
| `analog_indicator` | Live vs displayed redstone level, input quality and retained-when-STALE distinction. | Shift + right-click; existing analog readout retained |
| `honey_vibration_damper` | Current mechanical wave, local packet quality and mathematically predicted next local amplitude after damping. | Shift + right-click TOP; ordinary click opens HMI |
| `iron_core` | Applied-field coverage, remanence, magnetization threshold margin. | Shift + right-click BOTTOM; other Shift faces still demagnetize |
| `lapis_noise_source` | Deterministic test envelope, sample initialization, actual deviation vs configured baseline. | Shift + right-click TOP (also opens original HMI) |
| `molecular_cloud_receiver` | Loaded aperture, raw/filtered/peak detection and sensitivity gain; missing chunks not treated as zero. | Shift + right-click BOTTOM; other Shift faces still reset history |
| `phonon_conduit` | Thermal packet age, quality and expected in-node decay. | Shift + right-click TOP; ordinary click opens HMI |
| `sculk_vibration_interface` | Event count, last code, transition count, age since latest event and output. | Shift + right-click BOTTOM; other Shift faces still reset event history |
| `soul_flux_injector` | Control quality, adjacent recipients and offered packet strength; no false delivery claim. | Shift + right-click, alongside original short readout |
| `soul_flux_meter` | 100-to-15 quantization and readback comparison with validity state. | Shift + right-click |
| `soul_sand_reservoir` | Stored-charge estimate, validity/age, 40-tick decay period and no-recharge empty-time projection. | Shift + right-click |
| `soul_soil_conduit` | Measured transient charge/age and 20-tick loss rate, contrasted with long-hold reservoir. | Shift + right-click |
| `thermal_pulse_encoder` | Downside Redstone input, short local encoded packet and five possible output directions; send != arrival. | Shift + right-click TOP; ordinary click opens HMI |
| `thermal_pulse_receiver` | Pulse age, quality and front redstone readback; checks low-bandwidth delivery. | Shift + right-click |

## Acceptance and protection requirements

- `NicheDeviceCommissioning.inspect` is read-only and uses no world mutation, scheduled tick, power injection or administrative action.
- All reports derive from existing authoritative server observations. A declaration is not a live connection. A zero with valid evidence differs from a stale/unloaded input.
- Existing standard GUI openers and other Shift controls remain reachable. Three previously destructive Shift interactions (iron-core demagnetize, molecular history reset, sculk event reset) now gain a separate Shift+BOTTOM **non-mutating** inspection route.
- The hard-gate verifier requires all 14 registered block Java classes to contain exactly one inspector call and a corresponding distinct purpose branch.
- The in-game test plan is **not completed**: verify all 14 actions in creative and survival, old-world loading, target-face interactions, client/server synchronization, low-window UI usability and exact integrity of scripted inter-block measurements before calling runtime acceptance.

## Future deeper strengthening (after user playtest)

If players still do not need a device in real builds, prioritize **measured and optional** upgrades such as a calibration target for analog indicators, explicit hold/release controls for reservoir charging, amplitude/quality attenuation experiments, and pulse test patterns. These require separate game-design and compatibility tests; do not silently introduce new block states, physical carrier rules or data formats. Prefer genuinely unique experiments over new content counts.
