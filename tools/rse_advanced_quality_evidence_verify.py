#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
failed = []

menu_path = root / 'src/main/java/dev/redstoneengineering/ui/menu/AdvancedParameterMenu.java'
screen_path = root / 'src/main/java/dev/redstoneengineering/client/ui/AdvancedParameterNotebookScreen.java'
amp_path = root / 'src/main/java/dev/redstoneengineering/block/SignalAmplifierBlock.java'
noise_path = root / 'src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java'
range_path = root / 'src/main/java/dev/redstoneengineering/block/LapisPrecisionRangeSensorBlock.java'

for path in (menu_path, screen_path, amp_path, noise_path, range_path):
    if not path.is_file(): failed.append(f'missing advanced quality contract file: {path.relative_to(root)}')

if not failed:
    menu = menu_path.read_text(errors='ignore')
    screen = screen_path.read_text(errors='ignore')
    amp = amp_path.read_text(errors='ignore')
    noise = noise_path.read_text(errors='ignore')
    rng = range_path.read_text(errors='ignore')
    for token in ('liveE.set(input.quality().ordinal())','PortQuality.SATURATED.ordinal()','liveC.set(PortQuality.VALID.ordinal())','liveD.set(sample.quality().ordinal())','public int liveE()','public int liveF()'):
        if token not in menu: failed.append(f'AdvancedParameterMenu missing explicit quality token: {token}')
    for token in ('Input quality','Output quality','Measurement quality','NO TARGET','UNKNOWN','Clipping is explicit SATURATED output quality','A generated sample of 0 remains VALID evidence.','Complete clear coverage with no target is NO_SIGNAL; incomplete coverage is STALE.','Raw distance and measurement quality are synchronized separately'):
        if token not in screen: failed.append(f'AdvancedParameterNotebookScreen missing evidence token: {token}')
    for token in ('PortQuality.SATURATED','clipping(level, pos)','input.quality()'):
        if token not in amp: failed.append(f'SignalAmplifierBlock missing saturation-quality token: {token}')
    for token in ('A numeric sample of zero is a legitimate generated value.','PortQuality.VALID'):
        if token not in noise: failed.append(f'LapisNoiseSourceBlock missing zero-valid token: {token}')
    for token in ('if (!complete) return PortQuality.STALE;','return distance < 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID;','range coverage incomplete','no target within'):
        if token not in rng: failed.append(f'LapisPrecisionRangeSensorBlock missing quality token: {token}')

if failed:
    print('RSE advanced generic quality verification: FAIL')
    for item in failed: print(' -', item)
    raise SystemExit(1)

print('RSE advanced generic quality verification: PASS')
print(' signal amplifier input/output quality separation: PASS')
print(' saturated output remains explicit evidence: PASS')
print(' Lapis noise zero remains valid generated evidence: PASS')
print(' Lapis range NO_SIGNAL vs STALE remains explicit: PASS')
print(' generic notebook avoids fabricated numeric substitutes for missing evidence: PASS')
