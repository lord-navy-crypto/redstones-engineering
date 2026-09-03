# Alpha 1.0.7 Wiring Test Matrix

## Instrument bus
1. Scope — cable — cable — Probe A: Scope must detect A.
2. Add a 90-degree bend: connection remains valid.
3. Add vertical cable: connection remains valid.
4. Branch one cable into Probe A and Probe B: both unique channels are valid.
5. Rotate/place Probe so its measured-target face points toward the cable: cable must not connect to the probe's sensing face.
6. Put a cable adjacent diagonally or against an unconnected face: it must not become part of the scan merely by proximity.
7. Mix normal and Shielded Instrument Cable: they must form one instrumentation bus.

## Insulated redstone
1. Vanilla source — Terminal(INPUT) — insulated cable — Terminal(OUTPUT) — lamp.
2. Right-click terminal and verify displayed physical Vanilla/Cable sides.
3. Straight cable and bend must propagate 0..15 with existing cable loss rules.
4. Branch degree >2 on plain cable must remain a topology error; Junction is required.

## Domain separation
Place these media face-adjacent without converters:
- insulated redstone / copper;
- copper / lapis;
- lapis / quartz;
- quartz / instrument cable.

They must remain separate and diagnostics must identify `DOMAIN_MISMATCH`.

## Surface traces
- Lapis and Quartz connect only N/E/S/W.
- U/D remain isolated.
- right-click shows exact connected directions.

## Regression gates
- redstone external boundary remains 0..15;
- previous Alpha 1.0.6 engineering language/progression/assets remain intact;
- static verifiers, compileJava, Gradle tests and clean build all pass.
