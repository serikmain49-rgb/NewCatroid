import json,re,sys,pathlib
repo=pathlib.Path(sys.argv[1]); dest=pathlib.Path(__file__).resolve().parents[1]
base=repo/'catroid/src/main/java/org/catrobat/catroid/content/bricks'
core='MoveNStepsBrick PlaceAtBrick SetXBrick SetYBrick ChangeXByNBrick ChangeYByNBrick PointInDirectionBrick TurnLeftBrick TurnRightBrick GlideToBrick IfOnEdgeBounceBrick ShowBrick HideBrick SetSizeToBrick ChangeSizeByNBrick SetLookBrick SetLookByIndexBrick NextLookBrick WaitBrick WaitUntilBrick ForeverBrick RepeatBrick RepeatUntilBrick IfThenLogicBeginBrick IfLogicBeginBrick SetVariableBrick ChangeVariableBrick ShowTextBrick HideTextBrick ShowText3Brick HideText3Brick BroadcastBrick BroadcastWaitBrick CloneBrick DeleteThisCloneBrick PlaySoundBrick PlaySoundAndWaitBrick StopAllSoundsBrick SetGravityBrick SetVelocityBrick SetFrictionBrick ApplyForceBrick ApplyImpulseBrick SetPhysicsObjectTypeBrick CreateSphereBrick Create3dObjectBrick SetPhysicsStateBrick Set3dVelocityBrick Apply3dForceBrick Apply3dTorqueBrick SetCameraPositionBrick SetCameraRotationBrick SetCameraRangeBrick RotateCameraByBrick SetObjectColorBrick LoadSceneBrick LoadSceneAdditiveBrick'.split()
core+= ['Set3dGravityBrick', 'CreateCubeBrick', 'Set3dPositionBrick', 'Set3dRotationBrick', 'Set3dScaleBrick', 'Set3dFrictionBrick', 'SetRestitutionBrick', 'SetObjectTextureBrick', 'CameraTrackingBrick', 'CameraTouchControlBrick', 'CastRayBrick', 'AttachToCameraBrick', 'Remove3dObjectBrick']
core+= [p.stem for p in sorted(base.glob('Fast2D*Brick.java'))]
# No arbitrary constructor/property guesses: derive public constructors, inheritance and fields.
cache={}
def parse(name):
 if name in cache:return cache[name]
 path=base/(name+'.java')
 if not path.exists():return None
 text=path.read_text(); cache[name]=text;return text

def chain(name):
 seen=set()
 while name not in seen:
  seen.add(name);text=parse(name)
  if not text:return
  yield text
  m=re.search(r'class\s+'+re.escape(name)+r'\s+extends\s+(\w+)',text)
  if not m:return
  name=m.group(1)

catalogue=[]; skipped=[]
for name in dict.fromkeys(core):
 text=parse(name)
 if text is None or not re.search(r'public\s+'+re.escape(name)+r'\s*\(\s*\)',text):skipped.append(name);continue
 ancestry=list(chain(name)); full='\n'.join(ancestry)
 fields=sorted(set(re.findall(r'addAllowedBrickField\(\s*(?:Brick\.)?BrickField\.(\w+)',full)))
 props=[]
 for text2 in ancestry:
  for typ,key in re.findall(r'\bprivate\s+(int|float|double|boolean|String)\s+(\w+)\s*(?:=|;)',text2):
   if key not in {'commentedOut','parent','phantom','name','broadcastMessage'}:props.append({'name':key,'type':typ})
 item={'class':name,'formulas':fields,'properties':props,'children': 'implements CompositeBrick' in full,'variable_required':'extends UserVariableBrick' in full}
 if 'setLook(' in full:item['look_reference']=True
 if 'setSound(' in full:item['sound_reference']=True
 if 'setBroadcastMessage(' in full:item['message_reference']=True
 catalogue.append(item)
(dest/'supported-blocks.json').write_text(json.dumps(catalogue,ensure_ascii=False,indent=2))
print('catalogue:',len(catalogue),'blocks; unavailable:',', '.join(skipped))
