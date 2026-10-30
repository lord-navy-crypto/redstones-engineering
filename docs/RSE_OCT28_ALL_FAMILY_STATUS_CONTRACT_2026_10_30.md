# RSE first-round all-family UI upgrade — Status / Model / Configure

All UI changes in this branch are **content-only**. Keep the accepted October 28 `RseLdUiComponents.tabbedWorkspace`, standalone navigation, X/Y scrollbars, dragging, original root geometry, and `EngineeringScreen` unchanged.

The canonical example is **Lapis Low-Pass / FieldDevice**: show the actual implemented equation, distinguish a configured coefficient from real measured values, provide exact legal input and server readback, then show a compact live Status with quality/health/route and actionable next check.

Rollout:
- Existing FieldDevice + Universal families already have exact legal-step buttons, full formula readback, and short Status pages from PR243.
- All 21 dedicated families receive the same concise Status contract based on `EngineeringDeviceMenu`. Evidence tab labels become Status; detailed legacy diagnostics are available under a local `Show technical details` toggle so nothing is deleted.
- Instrument families without an Evidence page get their own additive Status tab; their original capture/configuration pages and graphs remain available.
- No new fake controls on passive meters. Only genuine menu methods should drive any additional buttons.
- Existing detailed equations and configured ranges remain in their original Model/Overview/Details/Configure pages. Fixed constants must never be mislabeled as adjustable.
- In-game manual tests remain required for real scroll dragging, small-screen geometry, multiplayer readback, and all 129 interactive block entries.

Source verification: `tools/rse_all_family_status_verify.py`, `tools/rse_oct28_visual_baseline_verify.py`, and `tools/rse_fusion_nonvisual_parity_verify.py`.

## Real editable controls added in this rollout

| Existing specialist HMI family | Server-validated user-adjustable parameters | Model/effect |
|---|---|---|
| Amethyst | carrier/target frequency index 1..15; source amplitude 1..15 or tuned Q index 1..4 | resonance band matching and source strength |
| Magnetic | permanent field S 1..15; induction turns N 1..4 | magnetic strength and induction |
| Optical | emitter/channel/attenuation 0..15 or 0..8; free-space channel 0..3 | optical intensity, filtering and loss |
| Pneumatic | regulator/relief pressure {25,50,75,100} | pressure setpoint and relief threshold |
| Radio link | channel 0..3 | pairing, collisions, decoding |
| Range sensor | range {4,8,15} blocks | scan aperture and distance-to-output mapping |
| Logic analyzer | digital threshold T 1..15 | HIGH/LOW event classification |
| Oscilloscope | sampling period {1,2,4,8} ticks | Δt=N/20, f_s=20/N, Nyquist=f_s/2 |
| Signal processor | response r 1..4 or pulse width W 1..8 ticks | filter speed / pulse duration |
| Signal conditioner | gain 1..4; offset -5..5; clamp/threshold 1..15; deadband 1..4 | active transfer function by mode |

The common legal-step component adds previous/next/min/max actions and shows exact synchronized server values and accepted domains. It does **not** invent control values for passive devices. The server menu's existing `set...FromUi` / `apply...FromUi` method remains the only mutation path; the helper never updates a block directly. All original special-purpose controls, traces and measurement panels remain in their old positions.

### Verification and caveats

- All 21 specialist families plus both shared families have consistent Status navigation or an additive Status page, backed by synchronized `EngineeringDeviceMenu` health/evidence/route snapshots.
- The accepted first-round `RseLdUiComponents` scroll, dragging, navigation and host screen source is unchanged. More content lives *inside* the scroll viewport.
- The underlying 756 reviewed engineering source/resource files are unchanged relative to the existing fusion baseline.
- Static source, Java compile and unit tests do **not** establish that every in-game tab, scrollbar or multiplayer menu interaction works. Keep PR draft until that manual acceptance work is done.
