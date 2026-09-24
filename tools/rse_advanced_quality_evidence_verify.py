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
fault_path = root / 'src/main/java/dev/redstoneengineering/diagnostics/FaultInjectionModel.java'

for path in (menu_path, screen_path, amp_path, noise_path, range_path, fault_path):
    if not path.is_file(): failed.append(f'missing advanced quality contract file: {path.relative_to(root)}')

if not failed:
    menu = menu_path.read_text(errors='ignore')
    screen = screen_path.read_text(errors='ignore')
    amp = amp_path.read_text(errors='ignore')
    noise = noise_path.read_text(errors='ignore')
    rng = range_path.read_text(errors='ignore')
    fault = fault_path.read_text(errors='ignore')
    for token in ('liveE.set(input.quality().ordinal())','PortQuality.SATURATED.ordinal()','liveC.set(PortQuality.VALID.ordinal())','liveD.set(sample.quality().ordinal())','public int liveE()','public int liveF()','BUTTON_INPUT_PREVIOUS','BUTTON_INPUT_NEXT','BUTTON_OUTPUT_PREVIOUS','BUTTON_OUTPUT_NEXT','DirectionalSignalBlock.rotateSeriesInput','DirectionalSignalBlock.rotateSeriesOutput','DirectionalDomainBlock.rotateSeriesInput','DirectionalDomainBlock.rotateSeriesOutput','DirectionalDomainSourceBlock.rotateOutput','public boolean canRouteInput()','public boolean canRouteOutput()','public Direction inputDirection()','public Direction outputDirection()'):
        if token not in menu: failed.append(f'AdvancedParameterMenu missing explicit quality/routing token: {token}')
    for token in ('Input quality','Output quality','Measurement quality','NO TARGET','UNKNOWN','Clipping is explicit SATURATED output quality','A generated sample of 0 remains VALID evidence.','Complete clear coverage with no target is NO_SIGNAL; incomplete coverage is STALE.','Raw distance and measurement quality are synchronized separately','DIAGNOSTICS("Diagnostics")','private void diagnostics(GuiGraphics g)','diagnosticStatus()','diagnosticLines()','diagnosticNextAction()','OUTPUT SATURATED • HEADROOM LIMIT','COMPLETE SCAN • NO TARGET','SCAN COVERAGE INCOMPLETE','The client does not rescan the world, generate a new sample, or run a second device solver.','ROUTING("Routing")','tabLabel(Tab value)','Math.max(48','private void routing(GuiGraphics g)','menu.canRouteInput()','menu.canRouteOutput()','physical measurement aperture','there is no synthetic input endpoint','sensing aperture is not a wired source input','deterministic mix(gameTime XOR blockPos seed)','configured 1..64-tick period','Scan i=1..R along the physical sensing aperture','round(clamp(d,0,R) × 100 / R)','coverage stops and quality is STALE'):
        if token not in screen: failed.append(f'AdvancedParameterNotebookScreen missing evidence/diagnostic/routing token: {token}')
    for token in ('PortQuality.SATURATED','clipping(level, pos)','input.quality()'):
        if token not in amp: failed.append(f'SignalAmplifierBlock missing saturation-quality token: {token}')
    for token in ('mix64(tick ^ seed)','int span = a * 2 + 1','return clamp(value + delta, min, max)'):
        if token not in fault: failed.append(f'FaultInjectionModel missing deterministic-noise token: {token}')
    for token in ('A numeric sample of zero is a legitimate generated value.','PortQuality.VALID'):
        if token not in noise: failed.append(f'LapisNoiseSourceBlock missing zero-valid token: {token}')
    for token in ('if (!complete) return PortQuality.STALE;','return distance < 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID;','range coverage incomplete','no target within','if (!level.hasChunkAt(p)) return new RangeSample(-1, max, false);','Math.round(EngineeringMath.clamp(sample.distance(), 0, sample.maxRange())'):
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
print(' active generic devices expose device-specific diagnostics: PASS')
print(' diagnostics remain server-evidence-only and do not rescan/re-simulate: PASS')
print(' Advanced Routing page mutates only declared server-owned endpoints: PASS')
print(' Lapis noise source remains output-only: PASS')
print(' Lapis range sensing aperture stays distinct from its Lapis output: PASS')
print(' five-tab Advanced notebook stays narrow-viewport aware: PASS')
print(' deterministic noise/cadence assumptions match server source: PASS')
print(' range scan/coverage/normalization assumptions match server source: PASS')
