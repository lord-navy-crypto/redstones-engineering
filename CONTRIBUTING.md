# Contributing to Redstone Systems Engineering

RSE is an engineering-systems project built around a simple compatibility rule: **Minecraft-facing redstone remains 0–15, while richer measurements and runtime state live behind that boundary.**

Contributions should strengthen engineering behavior without turning RSE into a generic technology mod that replaces vanilla redstone.

## Engineering workflow

Prefer this development sequence:

```text
Measurement → Conditioning → Sampling → Control → Actuation → Optimization
```

When adding or refining a system, first define what is measured, what the ports mean, what state is stored, what non-ideal behavior is modeled, and how a player can diagnose it.

## Design rules

- Preserve ordinary redstone compatibility and the 0–15 world-facing signal boundary.
- Prefer explicit engineering ports and understandable interactions over hidden automation.
- Keep high-cardinality measurements out of `BlockState`; use runtime storage or an appropriate data object instead.
- Model useful non-ideal behavior when it teaches something: saturation, finite slew, loss, contention, interference, queueing, safety limits, or similar effects.
- Diagnostics should describe the system rather than silently optimize it.
- Avoid adding large systems only for feature count. A smaller block set with coherent measurement/control behavior is preferred.
- Keep Java, resource JSON, language keys, models, recipes, loot tables, and verification contracts synchronized.

## Before opening a pull request

Use JDK 21 and run from the repository root:

```bash
python3 tools/rse_repo_verify.py
python3 tools/rse_alpha103_closed_loop_verify.py
./gradlew compileJava --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
```

For behavior changes, also launch:

```bash
./gradlew runClient
```

Use the milestone test matrix under `docs/` for interactive validation when one exists.

## Pull request scope

Keep a pull request focused enough that its engineering behavior can be reviewed and tested. A good PR explains:

- the engineering problem being solved;
- port/input/output semantics;
- internal state and units/scales;
- expected non-ideal or failure behavior;
- diagnostic behavior;
- compatibility implications;
- verification and `runClient` tests performed.

Do not include generated build directories, IDE state, logs, temporary compiler argument files, crash dumps, or large reconstructed source archives.

## Version and documentation discipline

A feature is not considered complete merely because a manifest or README says it exists. The implementation, static verifier, documentation, Gradle/NeoForge build, and interactive behavior should agree.

Do not create a public release solely from a green compile. Release candidates should also complete the corresponding `runClient` test matrix for their critical paths.

## License

By contributing to this repository, you agree that your contribution may be distributed under the repository's MIT License.
