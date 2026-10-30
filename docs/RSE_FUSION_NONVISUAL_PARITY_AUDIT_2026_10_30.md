# RSE First-Round Fusion — Nonvisual Engineering Code Audit

**Review date:** 2026-10-30

## Scope and source authority

- **Frozen UI:** user-confirmed October 28 first-round visual architecture and the already-built PR #242 fusion UI, SHA `a9ba1da067e629d51440a6f03e22b5aaec744618`.
- **Latest source candidate:** PR #241, SHA `b321325d792fb446295e8e095e15195d1e4b81ba`.
- **Additional independent candidates checked:** PR #230 `1ba69f8`, PR #239 `a815e25` and superseded PR #240 `13caec2`.
- **Frozen areas:** all client source, GUI components outside server-owned menus, rendering code and `src/main/resources/assets/`. Do not alter them during engineering-only work.

## File-by-file Git blob comparison

All four reference branches and the PR #242 fusion branch have the same **756 nonvisual engineering files** under `src/`, excluding the frozen client presentation code and visual assets.

- **Missing technical source files:** 0
- **Different technical source blobs:** 0
- **Unported nonvisual differences from PR #230, #239 or #240:** 0
- **Intentional visual differences:** retained in the accepted PR #242 presentation; not to be touched here.

The nonvisual scope includes block and block-entity behavior, engineering data models, network/media physics, server-owned menu/synchronization code, machine safety and commissioning logic, GameTests and nonvisual resources. Hash equality is stronger than a filename check, but is not proof that all simulated behavior is correct in a running world.

## Latest integrated engineering work

- Complete 130-block registry and 23-family UI *coverage auditing*; 129 GUI candidates plus non-GUI compass, without claiming they were all opened in-game.
- 14 niche-device commissioning improvements and 46 shared-code reuse/optimization audits.
- Quartz sampling/timebase, Lapis low-pass/filter response and parameter validity.
- Optical freshness, frequency/domain integrity, communication fail-closed handling, differential receiver safety.
- Meter read-only evidence, watchdog/fault interlocks, topology and six-face I/O contract.
- Pneumatic, mechanical, Amethyst, Copper and industrial operations code as represented in the latest audited source tree.

## Regression gates

`tools/rse_fusion_nonvisual_parity_verify.py` is run in the normal CI static verification stage and writes inspectable JSON and Markdown reports into `.rse-audit/`. It checks that every reviewed engineering blob remains present and unchanged unless the reference is intentionally re-reviewed, flags extra backend files, cross-checks the earlier PR candidates, and **fails on any change to the frozen visual sources**.

This gate is intentionally separate from `rse_oct28_visual_baseline_verify.py`, which also validates the historically accepted dual-axis scroll/workspace layout. Normal builds run Java compile, unit tests, clean build and artifact checksum.

## Open acceptance work

- Minecraft GameTests are dispatched separately and were skipped in the passing PR #242 build.
- Real runClient GUI-scale/window-size checks, all 129 GUI blocks, real drag/scroll and multiplayer server synchronization are not verified by Git blob equality.
- The user-confirmed first-round appearance must be tested in the actual Minecraft client before releasing.

This is a source and regression audit, not an assertion that live gameplay has been fully certified.
