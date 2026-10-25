"""Source-reviewed adapters; runtime Catalogue probes installed classes, never exports this as availability."""
import pathlib,re,json
root=pathlib.Path(__file__).resolve().parents[1];base=root.parent;host=base/'NewCatroid' if (base/'NewCatroid/catroid').exists() else base;repo=host/'catroid/src/main/java/org/catrobat/catroid'
old=json.loads((root/'supported-blocks.json').read_text())
names={e['class'] for e in old}
names.update(p.stem for p in (repo/'content/bricks').glob('*Light*Brick.java') if not p.stem.startswith('Phiro'))
names.update('SceneTransitionBrick SceneStartBrick WriteVariableOnDeviceBrick ReadVariableFromDeviceBrick WriteListOnDeviceBrick ReadListFromDeviceBrick WriteToFilesBrick ReadFromFilesBrick WriteVariableToFileBrick ReadVariableFromFileBrick WriteListToFileBrick ReadListFromFileBrick SaveToInternalStorageBrick LoadFromInternalStorageBrick AddItemToUserListBrick DeleteItemOfUserListBrick ReplaceItemInUserListBrick InsertItemIntoUserListBrick'.split())
# Match inheritance declarations, formula labels and primitive selectors in reviewed source.
cache={}
def chain(name):
 while name and name not in cache:
  p=repo/'content/bricks'/(name+'.java');p=p if p.exists() else p.with_suffix('.kt')
  if not p.exists():break
  text=p.read_text();yield text
  m=re.search(r'(?:extends\s+|:\s*)(\w+)',text[text.find('class '+name):]);name=m.group(1) if m else None
rows=[]
for name in sorted(names):
 texts=list(chain(name));full='\n'.join(texts)
 if not full:continue
 fields={}
 for text in texts:
  for field,label in re.findall(r'addAllowedBrickField\(\s*(?:Brick\.)?BrickField\.(\w+),\s*R\.id\.(\w+)',text):fields[field]=label
 props=[]
 for t in texts:
  for typ,key in re.findall(r'(?:private|protected)\s+(int|float|double|boolean|String)\s+(\w+)\s*(?:=|;)',t):
   if key not in {'commentedOut','parent','phantom','name','broadcastMessage','sceneToStart','sceneForTransition'}:props.append(key)
 rows.append(dict(class_name=name,property_names=sorted(set(props)),field_labels=fields))
(root/'catalogue-seeds.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
# Runtime keyboard generates parameter token lists for these resource IDs, so arity comes from installed code.
a=(repo/'formulaeditor/InternFormulaKeyboardAdapter.java').read_text();resources={}
for m in re.finditer(r'case\s+R\.(string|id)\.(\w+)\s*:',a):
 start=m.end();n=re.search(r'\bcase\b|\bdefault\s*:',a[start:]);piece=a[start:start+n.start()] if n else a[start:]
 f=re.search(r'Functions\.(\w+)',piece)
 if f:resources[f.group(1)]={'resource_class':m.group(1),'resource_name':m.group(2)}
(root/'function-resources.json').write_text(json.dumps(resources,indent=2))
print(len(rows),'block adapters;',len(resources),'runtime function token probes')
