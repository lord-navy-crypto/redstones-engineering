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
