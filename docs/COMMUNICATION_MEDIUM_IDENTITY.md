# RSE Communication Medium Identity Contract

RSE borrows engineering questions from ECE, controls, communications, and operations engineering, but it does not attempt to reproduce real-world communication standards or exact physical units. Each medium must earn its place by creating a distinct design decision inside the RSE physics system.

## Shared information envelope

Every communication medium may expose the same small observer-facing information vocabulary:

- **Payload** — the information being carried.
- **Selector** — a medium-owned discriminator such as channel, frequency, or frame period.
- **Validity** — whether the payload is currently usable.
- **Quality** — current medium-specific link margin, normalized to 0..100 for inspection only.
- **Freshness** — age in server ticks since the information envelope was last updated.

These fields are an interface contract, not a universal propagation model. A quality value of 80 on radio does not mean the same underlying physics as 80 on serial or optical fiber. The number exists so players can inspect whether a medium is healthy; the medium itself owns why that number changes.

## Medium identity rule

A communication medium is justified only when it has all of the following:

1. a unique advantage,
2. a unique cost or constraint,
3. a characteristic failure mode,
4. useful diagnostics for that failure,
5. at least one design situation where another medium is materially worse.

Adding a new cable, frequency band, or protocol name without a new engineering decision is not sufficient.

## Current medium roles

| Medium | Primary advantage | Main cost / constraint | Characteristic failure | Engineering use |
| --- | --- | --- | --- | --- |
| 8-bit Data Bus | Dense byte-wide, immediate local sharing | Local loading and driver coordination | Multi-driver contention; conflicting values invalidate the bus | Compact local machine/control backplane |
| Serial Data | Full byte over a single line | Frame period and utilization pressure; one live source | Over-utilization, stale frames, line break, multi-driver invalidation | Longer or wiring-efficient byte transport |
| Differential Data | Strong link margin and simple discrete semantics | One bit only; one live source | Broken pair or competing drivers | Heartbeat, permissive, interlock/status, robust discrete control |
| Radio | No physical cable route | Shared channel, range, obstacles and latency | Collision, adjacent-channel interference, dropout/handoff | Flexible wireless control/telemetry where routing matters |
| Guided Optical Fiber | Clean guided channelized transport | Deliberate topology; passive branching is forbidden | Attenuation, wrong channel, broken route | High-integrity routed optical networks with explicit split/filter design |
| Free-space Optical | Cable-free directed optical path | Line of sight and alignment | Obstruction, misalignment, distance/power-budget loss | Point-to-point links where geometry itself is the engineering challenge |
| Hydroacoustic | Propagation through a medium with amplitude/frequency/direction evidence | Medium-dependent attenuation | Weak amplitude, wrong frequency, broken medium path | Communication/sensing where the material path matters |

## Three wired digital media must not collapse into one another

### 8-bit Data Bus

The bus is the **parallel local** choice. It carries a full byte without serial framing, but larger connected bus structures consume RSE bus margin. Multiple drivers carrying the same value remain logically usable but are explicitly lower-margin; different driven values are a hard conflict.

The bus should therefore be attractive inside compact machines, but not automatically become the best long-distance link.

### Serial Data

Serial is the **wiring-efficient byte** choice. It carries a full byte over one line and exposes frame period, inter-arrival time and utilization. Length can reduce quality, but the defining pressure is temporal: a link can be physically valid yet operationally stressed by update cadence.

A regenerator may restore a degraded serial path, giving players a reason to reason about link segmentation rather than only distance.

### Differential Data

Differential data is the **high-integrity one-bit** choice. Its link-margin degradation is intentionally gentler than serial because the medium carries only a single discrete bit. That makes it useful for heartbeat, safety permissive, trip/ready state, limit/status, and other signals where information density is less important than robust delivery.

It must never become a hidden byte bus. The one-bit restriction is the price paid for its stronger RSE link margin.

## Quality and freshness are independent

A signal can be:

- high quality but stale,
- low quality but freshly updated,
- invalid but freshly diagnosed,
- valid, high-quality and fresh.

Consumers and diagnostics must not silently treat quality as age or age as quality. This separation is important for future watchdogs, stale-command handling, communication fault labs, and operations evidence.

## Information is not electrical power

Copper electrical networks transport **available energy**. Communication media transport **information**. A future actuator may require both a fresh valid command and sufficient electrical/pneumatic/mechanical capacity, but those are independent requirements.

This separation is central to RSE system design: a perfect command does not guarantee the machine has energy to act, and abundant energy does not guarantee the command is valid or fresh.

## Design restraint

RSE should prefer deeper interactions among existing media over adding more communication blocks. New mechanisms should normally appear first as:

- better medium-specific propagation rules,
- clearer diagnostics,
- failure/recovery behavior,
- interactions with watchdogs, controls, alarms and operations,
- reference systems that make the trade-offs visible.

Exact real-world protocol stacks, impedance standards, bit rates, modulation equations, and industrial certification rules are out of scope unless a simplified idea creates a meaningful RSE gameplay decision.
