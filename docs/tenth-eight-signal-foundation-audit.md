# Tenth Eight — Signal Formation / Foundational Domain Integrity

Acceptance scope:

1. Edge Detector
2. Pulse Shaper
3. Signal Tap
4. Range Sensor
5. Lapis Signal Line
6. Lapis Precision Source
7. Quartz Timing Line
8. Quartz Oscillator

Primary audit risks discovered before implementation:

- Signal Tap physically reads BACK and drives FRONT plus LEFT, while its inherited engineering contract declares only BACK/FRONT;
- Range Sensor produces a real directional redstone output but has no EngineeringPort contract or Inspector projection;
- Lapis and Quartz sources/lines are operational domain devices but are invisible to the common EngineeringPort layer;
- removing a middle Lapis or Quartz trace clears only the removed node, leaving stale precision/timing runtime on the separated island;
- Edge Detector and Pulse Shaper have transient scheduled state that the Field Device Inspector cannot expose authoritatively;
- foundational non-redstone sources must remain electrically isolated from vanilla redstone connectivity.

Acceptance contract:

- physical behavior, vanilla connectivity, EngineeringPort topology, and Inspector UI must describe the same device;
- transient edge/pulse state remains server-authoritative and scheduled rather than idle per-tick polling;
- high-cardinality Lapis/Quartz runtime remains outside BlockState;
- breaking a trace recomputes every adjacent surviving component;
- eight dedicated GameTests and the tenth verifier gate the batch in CI.
