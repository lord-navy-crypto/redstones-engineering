# RSE Alpha 1.0.21 RC1 — One-Pass Integrated Testing Guide

This is the integrated stabilization candidate for **Redstone Systems Engineering 1.0.21-alpha-rc1**. Feature expansion is intentionally frozen for this pass. The goal is to expose remaining runtime, topology, save/reload, HMI, and cross-system integration defects in one Minecraft session.

Use a fresh test world first. Back up any world you care about before loading an alpha build.

## Supported baseline

| Component | RC1 baseline |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.249` |
| Java | `21` |
| RSE | `1.0.21-alpha-rc1` |
| JEI | `19.27.0.336` |
| Jade | `15.10.6` |
| GeckoLib | `4.9.2` |
| Cloth Config | `15.0.140` |
| Fusion | `1.3.14` / `1.3.14-neoforge-mc1.21.1` |

## Before entering a world

1. Confirm Minecraft reaches the title screen without an RSE loading error.
2. Confirm all required dependency mods load.
3. Confirm the RSE jar identifies itself as `1.0.21-alpha-rc1`.
4. Create a fresh creative test world.
5. Confirm the RSE creative content, JEI integration and Jade integration are present.

If startup fails, keep the full `latest.log` and crash report. The last launcher line alone is usually insufficient.

---

# One-pass test route

Run the following in order. The sequence intentionally crosses domains so state leaks, stale evidence, bad route mutation, and observer/authority mistakes are easier to expose.

## 1. Vanilla Redstone → Lapis conversion boundary

Build a vanilla 0..15 source into **Redstone → Lapis**, then route Lapis into **Lapis → Redstone**.

Test:
- input and output faces independently;
- rotate RX without moving TX;
- rotate TX without moving RX;
- verify RX and TX never overlap;
- reconnect after moving each endpoint;
- try source values `0`, `1`, `7`, `15`;
- inspect the Media Conversion HMI.

Expected:
- Redstone remains bounded to `0..15`;
- Redstone→Lapis reports scaled representation/source spacing but does **not** claim new source precision;
- Lapis→Redstone reports reconstructed value and quantization loss;
- HMI controls change the backend face actually read/written;
- old TX faces stop driving after rerouting.

## 2. Lapis precision loss test

Feed values that are representable and non-representable on the 0..15 Redstone grid into the Lapis precision path.

Expected:
- the precision meter identifies sub-Redstone detail when present;
- quantization diagnostics are deterministic;
- no random noise appears merely because conversion occurred.

## 3. Instrument Cable vs Shielded Instrument Cable

Build two otherwise-equivalent measurement routes: one normal Instrument Cable, one Shielded Instrument Cable. Put an energized Redstone or Copper source next to part of both routes, then repeat with the interference source removed.

Check Oscilloscope and Logic Analyzer.

Expected:
- exposure evidence appears only when a real local interference source is present;
- unshielded exposure reduces interference confidence more than shielded exposure;
- waveform/sample values are not randomly modified;
- topology `PortQuality` is not falsely converted into an interference fault;
- NEXT guidance recommends shielding/separation when appropriate.

## 4. 8-bit Bus / Serial / Differential tradeoff

Exercise the encoder/decoder, serializer/deserializer/regenerator, and differential driver/receiver paths.

Expected:
- 8-bit Bus exposes parallel payload plus loading/contention/driver evidence;
- Serial exposes ordered timing/period/utilization evidence;
- Differential exposes one-bit payload and link-integrity identity;
- HMI values come from actual connected media, not hard-coded marketing text;
- disconnecting/reconnecting the relevant medium changes synchronized evidence correctly.

## 5. Copper power/load domain and Operations

Build a Copper source/conductor/load/meter path. Change the load and source state, then create and clear a real protection event if practical.

Expected:
- Copper Circuit Meter shows server-authoritative `V / Req / I / P`;
- opening the HMI does not change the circuit;
- measurement-face routing samples the selected physical side;
- commissioning distinguishes `PASS / MARGINAL / NOT_READY / FAIL`;
- de-energized valid evidence does not become a false fuse trip;
- Operations distinguishes protection reliability from Copper evidence readiness;
- Copper evidence transitions do not contaminate fuse downtime/repeat-trip metrics.

## 6. Guided optical segment budget

Build an emitter → passive fiber/junction path → receiver. Then add one explicit processor boundary such as splitter, attenuator or filter and retest the downstream segment.

Expected:
- receiver diagnostics show source intensity/channel, path evidence, observed segment loss, receiver intensity and headroom;
- the UI stays in the existing discrete `0..15` optical model and does not claim dB;
- splitter/attenuator/filter behavior is not double-counted in the downstream passive segment budget;
- channel mismatch is diagnosed explicitly;
- bounded/truncated or ambiguous evidence fails closed instead of inventing an ordered path.

## 7. Pneumatic pressure-response test

Build source/reservoir/regulator/valve/flow-meter/cylinder combinations. Compare:
- high supply pressure;
- reduced regulator pressure;
- partially restricted proportional valve;
- longer path;
- low downstream pressure/starvation.

Expected:
- cylinder response time changes deterministically with authoritative pressure evidence;
- diagnostics distinguish low source pressure, distribution path loss, regulation/restriction loss and downstream starvation where the retained model can prove them;
- flow meter localizes dominant pressure loss using synchronized witnesses;
- no random leak or fake CFD history is introduced;
- changing route/configuration clears or updates stale evidence correctly.

## 8. Radio link margin and interference

Test one transmitter/receiver pair under:
- short unobstructed range;
- long range near decode margin;
- obstacle(s) in the path;
- adjacent-channel transmitter exposure;
- two same-channel transmitters creating collision;
- a valid payload of `0`.

Expected:
- diagnosis distinguishes healthy, marginal, below-margin, obstruction, adjacent-channel interference and same-channel collision;
- distance, obstacles, fading, adjacent-channel penalty, collision, threshold and latency remain server/RadioKernel evidence;
- payload `0` remains valid when quality evidence is valid;
- no client-side random radio history appears.

## 9. Instrument / Quartz / Amethyst / PID spot check

Do one short pass through:
- Signal Analyzer;
- Oscilloscope;
- Logic Analyzer;
- Quartz timing/divider diagnostics;
- Amethyst resonance/filter/spectrum diagnostics;
- PID commissioning and explicit acceptance capture.

Expected:
- stale/invalid evidence is clearly distinguished from a legitimate zero value;
- instrument history is retained evidence, not render-generated history;
- Quartz timing initializes from real edges;
- Amethyst diagnostics stay in the implemented discrete resonance model;
- PID normal click, Shift+FRONT capture, and Shift+other-face reset keep their established meanings.

## 10. Route mutation stress pass

For representative devices with physical RX/TX controls:
- rotate input repeatedly;
- rotate output repeatedly;
- attempt configurations near other ports;
- break and replace adjacent media;
- move from valid → invalid → valid topology.

Expected:
- endpoint collisions are rejected/skipped;
- UI labels follow the real backend port orientation;
- old outputs stop driving;
- no `Too many chained neighbor updates` safety cutoff occurs.

## 11. Save / reload / shutdown

Save and exit the world. Restart Minecraft and reopen it.

Verify:
- block orientation and configured properties survive;
- no registered content disappears;
- network behavior recomputes coherently;
- no stale drive remains on a former output face;
- expected transient diagnostics may reset, but persistent block configuration must not silently change;
- clean shutdown produces no RSE exception in `latest.log`.

## 12. Final mixed-system smoke test

Put at least three domains in the same area—for example Redstone/Lapis conversion, Copper power measurement, and an Instrument/Radio/Optical path—and operate them together for several minutes.

Look specifically for:
- one domain accidentally influencing another without an explicit converter/interface;
- stale HMIs after unrelated nearby updates;
- repeated neighbor-update warnings;
- excessive log spam;
- severe FPS/tick degradation caused by standing diagnostic UIs;
- evidence counters growing without bound.

---

# Stop-the-test conditions

Stop and save logs immediately if any of these occur:

- startup/world-load crash;
- world corruption or block-state loss;
- `Too many chained neighbor updates`;
- reproducible authoritative value outside its documented range;
- client HMI visibly changing network physics merely by opening/closing it;
- persistent ghost output after changing a TX face or breaking a source;
- cross-domain conduction without an explicit documented converter;
- unbounded event/history growth;
- repeatable severe tick freeze.

# Bug report minimum

For each defect, record:
- RSE `1.0.21-alpha-rc1`;
- jar SHA-256;
- Minecraft / NeoForge / Java versions;
- dependency versions;
- single-player or client/server;
- smallest physical topology that reproduces it;
- block orientations / RX / TX faces;
- exact steps;
- expected vs actual result;
- whether restart reproduces it;
- `latest.log` and crash report when relevant;
- screenshot/video when visual or route state matters.

# RC1 acceptance rule

This build is a valid test candidate only if the **exact RC1 PR HEAD** passes:

- verifier syntax;
- complete static/reference gate;
- 122-block audit;
- Java 21 compile;
- Gradle tests;
- **blocking NeoForge GameTests**;
- chained-neighbor-update safety check;
- clean build;
- SHA-256 generation;
- artifact upload.

Do not substitute an older green commit for a newer RC1 HEAD.