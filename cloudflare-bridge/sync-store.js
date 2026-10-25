const enc=new TextEncoder();
const FILES=['project.json','code.xml','PROTOCOL.md','supported-blocks.json'];
const hash=async text=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',enc.encode(text))),b=>b.toString(16).padStart(2,'0')).join('');
export async function acceptSync(store,data,InputError,limit){
 const need=(v,m,code='INVALID_SYNC')=>{if(!v)throw new InputError(m,code)};
 const object=x=>x&&typeof x==='object'&&!Array.isArray(x);
 need(data.sync_version===1&&['full','delta'].includes(data.mode),'Unsupported sync version/mode');
 need(typeof data.project_id==='string'&&/^[a-f0-9]{64}$/.test(data.project_id),'Missing project identity');
 need(typeof data.request_id==='string'&&data.request_id.length>0&&data.request_id.length<=200,'Missing request_id');
 need(typeof data.project==='string'&&data.project.length>0&&data.project.length<=500,'Missing project name');
 need(object(data.files)&&object(data.changed)&&Object.keys(data.files).sort().join('|')===FILES.slice().sort().join('|'),'Exactly four exported files required');
 need(enc.encode(JSON.stringify(data.files)).length<=1024*1024,'Manifest too large');
 const rows=(await store.sql("SELECT k,v FROM nc_bridge WHERE k IN ('state','sync','pending') OR k GLOB 'part:*'").all()).results;
 const rowMap=new Map(rows.map(r=>[r.k,r.v]));const state=rowMap.has('state')?JSON.parse(rowMap.get('state')):null;
 const pending=rowMap.has('pending')?JSON.parse(rowMap.get('pending')):null;
 if(pending?.request_id===data.request_id&&pending.expected_project_id&&pending.expected_project_id!==data.project_id)throw new InputError('Открыт другой проект. Вернитесь к запрошенному проекту или отправьте новый вручную.','WRONG_PROJECT');
 if(data.mode==='delta'&&(!state||state.project_id!==data.project_id||state.base_sha256!==data.base_sha256||!rowMap.has('sync')))return {ok:false,code:'FULL_EXPORT_REQUIRED',message:'База на сервере изменилась. Требуется полный экспорт.'};
 const used=new Set();let count=0;
 for(const file of FILES){need(Array.isArray(data.files[file])&&data.files[file].length>0,'Empty file manifest');for(const part of data.files[file]){need(object(part)&&/^[a-f0-9]{64}$/.test(part.sha256),'Invalid content hash');for(const field of ['kind','scene_id','script_id'])need(typeof part[field]==='string'&&part[field].length<=200,'Invalid fragment scope');used.add(part.sha256);count++;}}
 need(count<=1000,'Too many export fragments');
 const changed=Object.entries(data.changed);need(changed.every(([k,v])=>used.has(k)&&typeof v==='string'&&v.length<=250000),'Invalid changed fragment');
 for(let i=0;i<changed.length;i+=16)await Promise.all(changed.slice(i,i+16).map(async([k,v])=>need(await hash(v)===k,'Changed fragment SHA-256 mismatch','FRAGMENT_HASH_MISMATCH')));
 const text={};let total=0;for(const file of FILES){let parts=[];for(const part of data.files[file]){const value=Object.hasOwn(data.changed,part.sha256)?data.changed[part.sha256]:rowMap.get('part:'+part.sha256);if(value===undefined)return {ok:false,code:'FULL_EXPORT_REQUIRED',message:'На сервере отсутствует часть экспорта.'};parts.push(value);}text[file]=parts.join('');total+=enc.encode(text[file]).length;}
 need(total<=limit,'Reconstructed snapshot exceeds 8 MiB','SNAPSHOT_TOO_LARGE');
 let project,catalogue;try{project=JSON.parse(text['project.json']);catalogue=JSON.parse(text['supported-blocks.json']);}catch{throw new InputError('Invalid project/catalogue JSON','INVALID_SYNC');}
 need(object(project)&&project.format===1&&Array.isArray(project.scenes)&&project.scenes.length>0,'Not an exported NewCatroid project','NOT_A_PROJECT');
 need(/^\s*<program(?:\s|>)/.test(text['code.xml']),'Expected Catrobat <program>','NOT_A_PROJECT');
 need(project.project_id===data.project_id&&project.project===data.project,'Project identity/name mismatch','WRONG_PROJECT');
 need(project.request_id===data.request_id,'Project request_id mismatch','STALE_EXPORT');
 need(Array.isArray(catalogue),'Invalid catalogue');
 need(await hash(text['code.xml'])===data.target_sha256&&project.base_sha256===data.target_sha256,'Reconstructed XML SHA-256 mismatch','SNAPSHOT_HASH_MISMATCH');
 const revision=crypto.randomUUID(),received_at=Date.now()/1000;
 const stats={mode:data.mode,parts_sent:changed.length,total_parts:count,bytes_received:enc.encode(JSON.stringify(data)).length,reconstructed_bytes:total,changed_scripts:[...new Set(data.files['code.xml'].filter(p=>p.kind==='script'&&Object.hasOwn(data.changed,p.sha256)).map(p=>p.script_id))].filter(Boolean)};
 const metadata={project:data.project,project_id:data.project_id,base_sha256:data.target_sha256,request_id:data.request_id,received_at,strict:true,identity:true,revision,sync:stats};
 const info={...metadata,files:data.files};
 const statements=[store.sql("INSERT OR REPLACE INTO nc_bridge(k,v) SELECT 'state',? WHERE COALESCE((SELECT v FROM nc_bridge WHERE k='state'),'')=?",JSON.stringify(metadata),rowMap.get('state')||'')];
 let page={},size=0;const flush=()=>{if(!Object.keys(page).length)return;statements.push(store.sql("INSERT OR IGNORE INTO nc_bridge(k,v) SELECT 'part:'||key,value FROM json_each(?)",JSON.stringify(page)));page={};size=0;};
 for(const [k,v] of changed){const bytes=enc.encode(JSON.stringify({[k]:v})).length;if(size+bytes>900000)flush();page[k]=v;size+=bytes;}flush();
 const guard="EXISTS(SELECT 1 FROM nc_bridge WHERE k='state' AND json_extract(v,'$.revision')=?)";
 statements.push(store.sql("INSERT OR REPLACE INTO nc_bridge(k,v) SELECT 'sync',? WHERE "+guard,JSON.stringify(info),revision),store.sql("INSERT OR REPLACE INTO nc_bridge(k,v) SELECT 'last_device',? WHERE "+guard,JSON.stringify(received_at),revision),store.sql("DELETE FROM nc_bridge WHERE k='error' AND "+guard,revision),store.sql("DELETE FROM nc_bridge WHERE k='pending' AND json_extract(v,'$.request_id')=? AND "+guard,data.request_id,revision),store.sql("DELETE FROM nc_bridge WHERE k GLOB 'snap:*' AND "+guard,revision),store.sql("DELETE FROM nc_bridge WHERE k GLOB 'part:*' AND k NOT IN (SELECT 'part:'||value FROM json_each(?)) AND "+guard,JSON.stringify([...used]),revision),store.sql("UPDATE nc_bridge SET v=json_set(v,'$.status','rejected','$.details','STALE_EXPORT: project changed','$.source','bridge') WHERE k=('result:'||(SELECT json_extract(v,'$.id') FROM nc_bridge WHERE k='patch')) AND (json_extract(v,'$.base_sha256')!=? OR json_extract(v,'$.project_id') IS NOT ?) AND "+guard,data.target_sha256,data.project_id,revision),store.sql("DELETE FROM nc_bridge WHERE k='patch' AND (json_extract(v,'$.base_sha256')!=? OR json_extract(v,'$.project_id') IS NOT ?) AND "+guard,data.target_sha256,data.project_id,revision));
 need(statements.length<=45,'Too many sync writes');
 const result=await store.db.batch(statements);
 if(result[0].meta.changes!==1)return {ok:false,code:'FULL_EXPORT_REQUIRED',message:'Состояние изменилось во время отправки.'};
 return {ok:true,sync_version:1,project_id:data.project_id,base_sha256:data.target_sha256,request_id:data.request_id,...stats};
}
export function unpackSync(rows){
 const infoRow=rows.find(r=>r.k==='sync');if(!infoRow)return null;const info=JSON.parse(infoRow.v),parts=new Map(rows.filter(r=>r.k.startsWith('part:')).map(r=>[r.k.slice(5),r.v]));
 const files={};for(const [name,manifest] of Object.entries(info.files))files[name]=manifest.map(p=>{if(!parts.has(p.sha256))throw Error('Incomplete sync storage');return parts.get(p.sha256)}).join('');
 return {info,files};
}
