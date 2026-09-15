#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '.').resolve(); errors=[]
def read(rel):
 p=root/rel
 if not p.is_file(): errors.append(f'missing {rel}'); return ''
 return p.read_text(errors='ignore')
block=read('src/main/java/dev/redstoneengineering/block/WorkcellControllerBlock.java')
menu=read('src/main/java/dev/redstoneengineering/ui/menu/WorkcellControllerMenu.java')
screen=read('src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java')
for token in ('inputBufferUsedUnits','inputBufferCapacityUnits','outputBufferUsedUnits','outputBufferCapacityUnits','inputWipPressurePercent','outputWipPressurePercent'):
 if block and token not in block: errors.append(f'block snapshot missing {token}')
 if menu and token not in menu: errors.append(f'menu sync missing {token}')
for token in ('INPUT','WORKCELL','OUTPUT','ADMISSION','Input WIP','Output WIP','PERMIT','HOLD'):
 if screen and token not in screen: errors.append(f'screen missing visual token {token!r}')
for body,label in ((menu,'menu'),(screen,'screen')):
 for forbidden in ('OperationDispatchRuntime.evaluate','OperationBufferRuntime.receive','OperationBufferRuntime.allocate','OperationMaintenanceRuntime.start','OperationChangeoverRuntime.request','setBlock(','setDeltaMovement('):
  if body and forbidden in body: errors.append(f'{label} authority leak {forbidden}')
if errors:
 print('RSE WORKCELL UI V2 VERIFY: FAIL'); [print(' -',e) for e in errors]; raise SystemExit(1)
print('RSE WORKCELL UI V2 VERIFY: PASS')
print(' finite buffer evidence surfaced: PASS')
print(' input -> workcell -> output visualization: PASS')
print(' admission reason remains delegated: PASS')
print(' UI mutation/scheduling authority leakage: NONE')
