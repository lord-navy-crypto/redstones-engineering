# Twelfth Eight — Guided Optical Network / Channel Integrity

Acceptance scope:

1. Optical Fiber
2. Optical Emitter
3. Optical Receiver
4. Optical Power Meter
5. Optical Splitter
6. Optical Channel Filter
7. Optical Attenuator
8. Optical Fiber Junction

This pass treats guided optical behavior as a real engineering domain. Fiber and splice blocks expose only their connected physical faces, sources and receivers expose their actual six-face optical terminals, and processors expose explicit axial input/output ports. Optical payload remains transient runtime data; configuration remains bounded server-authoritative BlockState.

The audit also closes a lifecycle defect in the prior optical network: removing a node now recomputes each adjacent component independently instead of accidentally combining split components into one graph. The Inspector projects optical intensity, channel, direction, loss/filter configuration, connection masks, and topology validity without inventing redstone ports.

Eight dedicated GameTests and the twelfth verifier gate source/receiver propagation, invalid source handling, splitter topology, channel filtering, attenuation, power-meter face truthfulness, splice cleanup, and domain declarations.

Acceptance requires the full repository verifier suite plus all 132 Minecraft GameTests on Java 21.
