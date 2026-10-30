#!/usr/bin/env python3
"""Stream huge Minecraft GameTest output into a bounded, auditable artifact.

Do not let verbose NeoForge diagnostics create hundreds of MB of GitHub
Actions logs or artifacts. Keep startup context, a rotating tail, and
quality/safety/test-result lines in the same compact file. Read-only capture.
"""
from collections import deque
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    print("Usage: rse_bounded_gametest_log.py <log-destination>", file=sys.stderr)
    raise SystemExit(2)

destination = Path(sys.argv[1])
destination.parent.mkdir(parents=True, exist_ok=True)
MAX_HEAD_BYTES = 128 * 1024
MAX_TAIL_BYTES = 6 * 1024 * 1024
MAX_MARKERS = 700
MAX_MARKER_BYTES = 3072
MAX_LINE_BYTES = 8192
MILESTONE_BYTES = 32 * 1024 * 1024
signal = re.compile(
    rb"All \d+ required tests passed|(?:required )?tests? failed|"
    rb"LogTestReporter|GameTestRunner|GameTestHooks|"
    rb"Started game test server|tests are now running|"
    rb"Exception|Traceback|ERROR|WARN|FAIL|"
    rb"NoClassDefFoundError|Too many chained neighbor updates|"
    rb"(?:\d+) tests? (?:passed|failed)",
    flags=re.I,
)

head = bytearray()
tail = deque()
tail_size = 0
markers = deque(maxlen=MAX_MARKERS)
bytes_seen = 0
line_count = 0
marker_count = 0
live_notice_count = 0
milestone = MILESTONE_BYTES

for raw in sys.stdin.buffer:
    bytes_seen += len(raw)
    line_count += 1
    if len(head) < MAX_HEAD_BYTES:
        head.extend(raw[:MAX_HEAD_BYTES - len(head)])
    fragment = raw[-MAX_LINE_BYTES:]
    tail.append(fragment)
    tail_size += len(fragment)
    while tail_size > MAX_TAIL_BYTES and tail:
        tail_size -= len(tail.popleft())
    # The GameTest server repeats its [X+__...] progress bar hundreds of
    # thousands of times. Retain actual failures, not redundant status bars.
    is_progress_bar = b"[minecraft/GameTestServer]: [" in raw
    if not is_progress_bar and signal.search(raw):
        marker_count += 1
        markers.append(raw[-MAX_MARKER_BYTES:])
        if live_notice_count < 40:
            text = raw[-750:].decode("utf-8", errors="replace").strip()
            print(text, flush=True)
            live_notice_count += 1
    if bytes_seen >= milestone:
        print(
            f"[RSE GameTest capture] received {bytes_seen // (1024 * 1024)} MiB "
            f"of source output; final artifact bounded to ~8 MiB",
            flush=True,
        )
        milestone += MILESTONE_BYTES

with destination.open("wb") as out:
    out.write(
        f"RSE BOUNDED GAMETEST LOG\n"
        f"source_bytes={bytes_seen} source_lines={line_count} "
        f"matching_lines={marker_count}\n"
        f"first_bytes={len(head)} retained_markers={len(markers)} "
        f"last_bytes={tail_size}\n"
        f"NOTE: intermediate verbose lines are omitted; conclusions require "
        f"the GameTest summary, not this script's exit code.\n\n"
        f"=== PROCESS START ===\n".encode()
    )
    out.write(head)
    out.write(b"\n\n=== RECENT TEST / ERROR MARKERS ===\n")
    for line in markers:
        out.write(line)
        if not line.endswith(b"\n"):
            out.write(b"\n")
    out.write(b"\n\n=== PROCESS END / LAST BYTES ===\n")
    for line in tail:
        out.write(line)
    out.write(b"\n=== END BOUNDED CAPTURE ===\n")
print(
    f"[RSE GameTest capture] wrote {destination.stat().st_size} bytes; "
    f"source had {bytes_seen} bytes and {marker_count} diagnostic lines",
    flush=True,
)
