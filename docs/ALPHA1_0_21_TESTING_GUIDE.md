# RSE Alpha 1.0.21 Testing Guide

Artifact: `1.0.21-alpha`  
Minecraft: `1.21.1`  
NeoForge: `21.1.249`  
Java: `21`

Back up any world you care about before testing.

## Required dependencies

Install the required RSE platform dependencies for Minecraft 1.21.1 NeoForge:

- JEI 19.27.0.336
- Jade 15.10.6
- GeckoLib 4.9.2
- Cloth Config 15.0.140
- Fusion 1.3.14

RSE does not bundle these jars. Missing required dependencies are expected to stop startup through NeoForge dependency resolution.

## Release-candidate gate

From the repository root:

```bash
python3 tools/rse_repo_verify.py
python3 tools/test_rse_integrated_demo.py
python3 tools/test_rse_signal_processing_lab.py
python3 tools/rse_pid_actuator_dynamics_verify.py
python3 tools/rse_ninth_ten_control_pneumatic_verify.py
python3 tools/rse_alpha1021_release_verify.py

./gradlew test --no-daemon --stacktrace
./gradlew compileJava --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
```

Before public release, also run the Minecraft runtime diagnostic:

```bash
./gradlew runGameTestServer --no-daemon --stacktrace
```

Confirm the log does not contain a required GameTest failure, a chained-neighbor-update safety cutoff, or an unhandled exception.

## In-game smoke test

Launch:

```bash
./gradlew runClient --no-daemon --stacktrace 2>&1 | tee latest.log
```

Verify:

1. RSE loads with all five required dependencies.
2. Ordinary placement, rotation, break/drop, JEI, Jade, and GUI paths work.
3. Integrated demo placement/status/retest works and accumulates sustained evidence.
4. Servo actuator movement, load/inertia behavior, position feedback, and PID control remain functional.
5. Pneumatic actuator cell shows finite pressure/flow response and cylinder motion.
6. Signal Processing Laboratory validates LPF, clock, sampler, quantizer, and end-to-end chain behavior.
7. Breaking or misrouting a connection produces bounded WAIT/FAIL evidence rather than a crash or fabricated valid signal.

Attach `latest.log` when reporting a startup, runtime, rendering, or validation failure.

## Scope boundary

Alpha 1.0.21 intentionally retains the complete-chain + actuator-deepening era and excludes the later shared Engineering Workbench / generic LAB UI redesign.
