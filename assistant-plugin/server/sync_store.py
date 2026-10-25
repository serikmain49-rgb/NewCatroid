"""Lossless four-file partial exports. The project XML is never parsed/reserialized."""
import hashlib,json,re,time
FILES={'project.json','code.xml','PROTOCOL.md','supported-blocks.json'}
def digest(text):return hashlib.sha256(text.encode()).hexdigest()
def accept_sync(store,data):
 def need(ok,msg,code='INVALID_SYNC'):
  if not ok:raise ValueError(code+': '+msg)
 need(data.get('sync_version')==1 and data.get('mode') in ('full','delta'),'unsupported sync mode')
 pid=data.get('project_id');rid=data.get('request_id');name=data.get('project')
 need(isinstance(pid,str) and re.fullmatch('[a-f0-9]{64}',pid),'missing project identity')
 need(isinstance(rid,str) and 0<len(rid)<=200,'missing request_id')
 need(isinstance(name,str) and 0<len(name)<=500,'missing project name')
 files=data.get('files');changed=data.get('changed')
 need(isinstance(files,dict) and set(files)==FILES and isinstance(changed,dict),'exactly four exported files required')
 need(len(json.dumps(files).encode())<=1024*1024,'manifest too large')
 with store.lock:
  if store.pending and store.pending['request_id']==rid and store.pending.get('expected_project_id') and store.pending['expected_project_id']!=pid:raise ValueError('WRONG_PROJECT: open the requested project or upload the new project manually')
  baseline=store.sync_data
  if data['mode']=='delta' and (not baseline or not store.state or store.state['project'].get('project_id')!=pid or baseline['project_id']!=pid or baseline['base_sha256']!=data.get('base_sha256') or store.state['project']['base_sha256']!=data.get('base_sha256')):return {'ok':False,'code':'FULL_EXPORT_REQUIRED','message':'Server base changed'}
  used=set();count=0
  for file,manifest in files.items():
   need(isinstance(manifest,list) and len(manifest)>0,'empty file manifest')
   for part in manifest:
    need(isinstance(part,dict) and isinstance(part.get('sha256'),str) and re.fullmatch('[a-f0-9]{64}',part['sha256']),'invalid content hash')
    need(all(isinstance(part.get(k),str) and len(part[k])<=200 for k in ('kind','scene_id','script_id')),'invalid fragment scope');used.add(part['sha256']);count+=1
  need(count<=1000,'too many fragments')
  blocks={} if data['mode']=='full' else dict(baseline['blocks'])
  for key,value in changed.items():
   need(key in used and isinstance(value,str) and len(value.encode('utf-16-le'))//2<=250000,'invalid changed fragment')
   need(digest(value)==key,'fragment SHA-256 mismatch','FRAGMENT_HASH_MISMATCH');blocks[key]=value
  if not used.issubset(blocks):return {'ok':False,'code':'FULL_EXPORT_REQUIRED','message':'Missing export fragment'}
  text={file:''.join(blocks[p['sha256']] for p in manifest) for file,manifest in files.items()}
  total=sum(len(v.encode()) for v in text.values());need(total<=8*1024*1024,'snapshot exceeds 8 MiB','SNAPSHOT_TOO_LARGE')
  project=json.loads(text['project.json']);catalogue=json.loads(text['supported-blocks.json'])
  need(isinstance(project,dict) and project.get('format')==1 and isinstance(project.get('scenes'),list) and len(project['scenes'])>0,'not an exported NewCatroid project','NOT_A_PROJECT')
  need(re.match(r'\s*<program(?:\s|>)',text['code.xml']),'expected Catrobat <program>','NOT_A_PROJECT')
  need(project.get('project_id')==pid and project.get('project')==name,'project identity/name mismatch','WRONG_PROJECT')
  need(project.get('request_id')==rid,'project request_id mismatch','STALE_EXPORT')
  need(isinstance(catalogue,list),'invalid catalogue')
  need(digest(text['code.xml'])==data.get('target_sha256')==project.get('base_sha256'),'reconstructed XML SHA-256 mismatch','SNAPSHOT_HASH_MISMATCH')
  stats={'mode':data['mode'],'parts_sent':len(changed),'total_parts':count,'bytes_received':len(json.dumps(data,ensure_ascii=False).encode()),'reconstructed_bytes':total,'changed_scripts':list(dict.fromkeys(p['script_id'] for p in files['code.xml'] if p['kind']=='script' and p['sha256'] in changed and p['script_id']))}
  store.accept_state({'project':project,'code_xml':text['code.xml'],'protocol':text['PROTOCOL.md'],'supported_blocks':catalogue,'request_id':rid})
  store.state['sync']=stats;store.save('state.json',store.state)
  store.sync_data={'project_id':pid,'base_sha256':data['target_sha256'],'blocks':{k:blocks[k] for k in used},'files':files};store.save('sync.json',store.sync_data)
  return {'ok':True,'sync_version':1,'project_id':pid,'base_sha256':data['target_sha256'],'request_id':rid,**stats}
