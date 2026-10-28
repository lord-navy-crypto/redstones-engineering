#!/usr/bin/env python3
"""Summarize the *observed* bounded NeoForge GameTest run; never infer a pass.

The report lists distinct LogTestReporter failures and whether the formal
all-required-tests-passed marker was actually present. This is diagnosis,
not a mechanism to suppress/whitelist failures.
"""
from collections import Counter
from pathlib import Path
import re
import sys

log = Path(sys.argv[1] if len(sys.argv) > 1 else ".rse-audit/gametest-server.log")
output = Path(sys.argv[2] if len(sys.argv) > 2 else ".rse-audit/gametest-triage.md")
output.parent.mkdir(parents=True, exist_ok=True)

def family(name: str) -> str:
    s = name.lower()
    if any(part in s for part in ("copper", "fuse", "resistor", "capacitor", "voltage", "junction")):
        return "Copper / electrical"
    if any(part in s for part in ("pneumatic", "pressure", "valve", "compressor", "reservoir")):
        return "Pneumatic / pressure"
    if any(part in s for part in ("radio", "serial", "byte", "bus", "differential", "regenerator", "serializer", "deserializer")):
        return "Communications"
    if any(part in s for part in ("conditioner", "filter", "pulse", "samplehold", "calibration", "scaler", "analyzer", "pid")):
        return "Signal / control / UI"
    if any(part in s for part in ("transducer", "sensor", "receiver", "measurement", "sculk", "optical", "magnetic", "quartz")):
        return "Sensors / domain transport"
    if any(part in s for part in ("topology", "port", "role", "test")):
        return "Topology / test harness"
    return "Other"

failures: dict[str, str] = {}
summary = False
if log.exists():
    # The new bounded collector stores a readable diagnostic excerpt. Stream
    # it anyway; do not accidentally reintroduce a 222-MB read_text().
    with log.open("r", encoding="utf-8", errors="replace") as source:
        for line in source:
            if re.search(r"All \d+ required tests passed :\)", line):
                summary = True
            if "[minecraft/LogTestReporter]:" not in line or " failed " not in line:
                continue
            payload = line.split("[minecraft/LogTestReporter]:", 1)[1].strip()
            name, separator, detail = payload.partition(" failed ")
            if separator and name and name not in failures:
                description = detail.partition("! ")[2] or detail
                failures[name] = description[:550].replace("|", "/").replace("\r", " ")

counts = Counter(family(name) for name in failures)
result = "PASS (log evidence)" if summary and not failures else "INCOMPLETE OR FAILED (not accepted)"
lines = [
    "# RSE GameTest observed failure triage",
    "",
    f"Input log: `{log.name}`",
    f"Evidence result: **{result}**",
    f"Distinct reported failures: **{len(failures)}**",
    f"Formal all-required-tests-passed marker: **{'present' if summary else 'absent'}**",
    "",
    "This report is a literal extraction of runtime evidence; categories are",
    "triage labels, not proven root causes. A successful Actions job or a",
    "running GameTest process alone is NOT evidence that the suite passed.",
    "",
    "## Failure families",
    "",
]
for group, count in sorted(counts.items()):
    lines.append(f"- {group}: {count}")
if not failures:
    lines.append("- No distinct LogTestReporter failures in captured log (does not imply pass)")
lines.extend(["", "## Individual failures", "", "| Test | Family | Reported reason |",
              "| --- | --- | --- |"])
for name, detail in sorted(failures.items()):
    lines.append(f"| `{name}` | {family(name)} | {detail} |")
output.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"RSE GameTest triage: {result}; distinct_failures={len(failures)}; formal_pass={summary}; report={output}")
