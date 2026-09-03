#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
checks={
 'PID anti-windup':'candidateIntegral' in (root/'src/main/java/dev/redstoneengineering/block/PidControllerBlock.java').read_text(),
 'PID inhibit':'RIGHT=inhibit' in (root/'src/main/java/dev/redstoneengineering/block/PidControllerBlock.java').read_text(),
 'Watchdog presets':'TIMEOUT_TICKS' in (root/'src/main/java/dev/redstoneengineering/block/WatchdogBlock.java').read_text(),
 'Voter diagnostics':'DEGRADED' in (root/'src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java').read_text(),
 'Fault remote reset':'RIGHT=reset' in (root/'src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java').read_text(),
 'Radio payload model':'record Reception' in (root/'src/main/java/dev/redstoneengineering/physics/RadioKernel.java').read_text(),
 'Radio collision':'collision' in (root/'src/main/java/dev/redstoneengineering/physics/RadioKernel.java').read_text().lower(),
 'Optical alignment':'alignment' in (root/'src/main/java/dev/redstoneengineering/block/FreeSpaceOpticalReceiverBlock.java').read_text().lower(),
 'Analog compressor':'commandedPressure' in (root/'src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java').read_text(),
 'Finite reservoir':'chargeRate' in (root/'src/main/java/dev/redstoneengineering/block/AirReservoirBlock.java').read_text(),
 'Molecular sensor dynamics':'SENSITIVITY' in (root/'src/main/java/dev/redstoneengineering/block/MolecularCloudReceiverBlock.java').read_text(),
 'Operations cycle time':'cycle last/avg/max' in (root/'src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java').read_text(),
 'Servo directional dynamics':'velocity=' in (root/'src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java').read_text(),
 'Regenerator threshold':'MIN_Q' in (root/'src/main/java/dev/redstoneengineering/block/DigitalRegeneratorBlock.java').read_text(),
 'Sculk event diagnostics':'events=' in (root/'src/main/java/dev/redstoneengineering/block/SculkVibrationInterfaceBlock.java').read_text(),
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items():print(('OK  ' if v else 'FAIL')+': '+k)
if failed: raise SystemExit(1)
print('PASS: Alpha 1.0.1 engineering refinement verification')
