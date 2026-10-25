import worker from './newcatroid-worker.js';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,rmSync,readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const here=dirname(fileURLToPath(import.meta.url));
const dir=mkdtempSync(join(tmpdir(),'nc-d1-')),path=join(dir,'test.sqlite');
function run(queries){const r=spawnSync('python3',[join(here,'sqlite-test.py')],{input:JSON.stringify({path,queries}),encoding:'utf8',maxBuffer:30*1024*1024});if(r.status!==0)throw Error(r.stderr);return JSON.parse(r.stdout);}
const DB={prepare(sql){return {sql,args:[],bind(...args){this.args=args;return this;},async run(){return run([this])[0]},async all(){return run([this])[0]},async first(){return run([this])[0].results[0]??null}}},async batch(q){return run(q)}};
const env={DB,DEVICE_TOKEN:'d'.repeat(64),ASSISTANT_TOKEN:'a'.repeat(64),MCP_LINK_SECRET:'m'.repeat(64)};
let passed=0;function ok(){passed++}
async function req(url,data,token=env.ASSISTANT_TOKEN,origin){const headers={};if(token)headers.Authorization='Bearer '+token;if(origin)headers.Origin=origin;const r=await worker.fetch(new Request('https://bridge.example'+url,{method:data===undefined?'GET':'POST',headers,body:data===undefined?undefined:JSON.stringify(data)}),env);return {status:r.status,data:r.status===202?null:await r.json()};}
try {
 assert.equal((await req('/api/status',undefined,'bad')).status,401);ok();
 assert.equal((await req('/api/status',undefined,env.DEVICE_TOKEN)).status,401);ok();
 assert.equal((await req('/api/status',undefined,env.ASSISTANT_TOKEN,'https://evil.example')).status,403);ok();
 assert.equal((await req('/api/status')).data.project,null);ok();
 const pending=(await req('/api/request',{})).data;
 assert.deepEqual((await req('/api/request',{})).data,pending);
 assert.deepEqual((await req('/api/device/poll',undefined,env.DEVICE_TOKEN)).data,pending);ok();
 const hash=async x=>Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(x))).toString('hex');
 const xml='<program><header><programName>Test</programName></header><scenes><scene><name>Игра</name></scene></scenes></program>';
 let snapshot={project:{project:'Breaking Bad',base_sha256:await hash(xml)},code_xml:xml,protocol:'Read four files',supported_blocks:[],request_id:pending.request_id};
 assert.equal((await req('/api/device/state',{...snapshot,code_xml:'wrong'},env.DEVICE_TOKEN)).status,400);ok();
 assert.equal((await req('/api/device/state',snapshot,env.DEVICE_TOKEN)).data.ok,true);
 assert.equal((await req('/api/status')).data.pending_request,null);ok();
 for(const f of ['project.json','code.xml','PROTOCOL.md','supported-blocks.json'])assert.equal((await req('/api/read',{file:f})).status,200);ok();
 let text='',offset=0;do{const r=(await req('/api/read',{file:'code.xml',offset,limit:60000})).data;text+=r.text;offset=r.next_offset;}while(offset!==null);assert.equal(text,xml);ok();
 const patch={format:1,id:'test',title:'Test',project:'Breaking Bad',base_sha256:snapshot.project.base_sha256,operations:[{op:'sprite.create',scene:'Игра',sprite:'Куб'}]};
 assert.equal((await req('/api/patch',{patch:{...patch,base_sha256:'old'}})).status,400);ok();
 assert.equal((await req('/api/patch',{patch})).data.applied,false);
 assert.deepEqual((await req('/api/device/patch',undefined,env.DEVICE_TOKEN)).data.patch,patch);ok();
 const next=(await req('/api/request',{})).data;
 await req('/api/device/state',{error:'Game running',request_id:'older'},env.DEVICE_TOKEN);
 assert.equal((await req('/api/status')).data.pending_request.request_id,next.request_id);ok();
 // Large snapshot, Cyrillic and non-BMP characters spanning storage chunk boundaries.
 const big='Куб🟦'.repeat(70000);snapshot={...snapshot,code_xml:big,project:{...snapshot.project,base_sha256:await hash(big)},request_id:next.request_id};
 assert.equal((await req('/api/device/state',snapshot,env.DEVICE_TOKEN)).data.ok,true);
 text='';offset=0;do{const r=(await req('/api/read',{file:'code.xml',offset,limit:60000})).data;text+=r.text;offset=r.next_offset;}while(offset!==null);assert.equal(text,big);ok();
 assert.equal((await req('/api/device/patch',undefined,env.DEVICE_TOKEN)).data.patch,null);ok();
 assert.equal((await req('/api/status')).data.pending_request,null);ok();
 const endpoint='/mcp/'+env.MCP_LINK_SECRET;
 const init=await req(endpoint,{jsonrpc:'2.0',id:1,method:'initialize'},null);
 assert.equal(init.data.result.protocolVersion,'2025-03-26');ok();
 assert.equal((await req(endpoint,{jsonrpc:'2.0',id:2,method:'tools/list'},null)).data.result.tools.length,5);ok();
 assert.equal((await req('/mcp/wrong',{jsonrpc:'2.0',id:3,method:'ping'},null)).status,401);ok();
 assert.equal((await req(endpoint,{jsonrpc:'2.0',method:'notifications/initialized'},null)).status,202);ok();
 const call=await req(endpoint,{jsonrpc:'2.0',id:4,method:'tools/call',params:{name:'read_project_file',arguments:{file:'code.xml',limit:10}}},null);
 assert.equal(call.data.result.isError,false);ok();
 const bad=await req(endpoint,{jsonrpc:'2.0',id:5,method:'tools/call',params:{name:'read_project_file',arguments:{file:'unknown'}}},null);
 assert.equal(bad.data.result.isError,true);ok();
 // Modern protocol: request receipt, lifecycle, persistent replay protection.
 const fresh=(await req('/api/request',{})).data;
 snapshot={...snapshot,project:{...snapshot.project,capabilities:{strict_request_id:true},request_id:fresh.request_id},request_id:fresh.request_id};
 await req('/api/device/state',snapshot,env.DEVICE_TOKEN);
 const modern={...patch,id:'modern',request_id:fresh.request_id,base_sha256:snapshot.project.base_sha256};
 const staleReceipt=await req('/api/patch',{patch:{...modern,request_id:'wrong'}});assert.equal(staleReceipt.data.code,'STALE_EXPORT');ok();
 assert.equal((await req('/api/patch',{patch:modern})).data.status,'queued');ok();
 assert.equal((await req('/api/patch',{patch:modern})).data.code,'DUPLICATE_PATCH');ok();
 const event={patch_id:modern.id,project:modern.project,base_sha256:modern.base_sha256,request_id:modern.request_id,status:'preview',details:'Review opened',event_id:'preview-1'};
 assert.equal((await req('/api/device/result',event,env.ASSISTANT_TOKEN)).status,401);ok();
 assert.equal((await req('/api/device/result',event,env.DEVICE_TOKEN)).data.status,'preview');
 assert.equal((await req('/api/device/result',event,env.DEVICE_TOKEN)).data.duplicate,true);ok();
 assert.equal((await req('/api/patch/status',{id:'modern'})).data.result.status,'preview');ok();
 await req('/api/device/result',{...event,event_id:'apply-1',status:'applied',after_sha256:'after',details:'Verified saved'},env.DEVICE_TOKEN);
 assert.equal((await req('/api/status')).data.patch_result.status,'applied');assert.equal((await req('/api/device/patch',undefined,env.DEVICE_TOKEN)).data.patch,null);ok();
 await req('/api/device/result',{...event,event_id:'late-preview'},env.DEVICE_TOKEN);
 assert.equal((await req('/api/patch/status',{id:'modern'})).data.result.status,'applied');ok();
 assert.equal((await req('/api/patch',{patch:modern})).data.code,'DUPLICATE_PATCH');ok();
 for(const status of ['rejected','error']){
   const id='terminal-'+status;await req('/api/patch',{patch:{...modern,id}});
   assert.equal((await req('/api/device/result',{...event,patch_id:id,event_id:id,status,details:status},env.DEVICE_TOKEN)).data.status,status);
   assert.equal((await req('/api/patch/status',{id})).data.result.status,status);ok();
 }
 const racing={...modern,id:'racing'};
 const race=await Promise.all([req('/api/patch',{patch:racing}),req('/api/patch',{patch:racing})]);assert.equal(race.filter(r=>r.status===200).length,1);ok();
 await req('/api/device/result',{...event,patch_id:'racing',event_id:'race-end',status:'rejected'},env.DEVICE_TOKEN);
 const pendingNew=(await req('/api/request',{})).data;
 assert.equal((await req('/api/patch',{patch:{...modern,id:'while-pending'}})).data.code,'STALE_EXPORT');ok();
 await req('/api/device/state',{...snapshot,request_id:pendingNew.request_id,project:{...snapshot.project,request_id:pendingNew.request_id}},env.DEVICE_TOKEN);
 assert.equal((await req('/api/patch',{patch:{...modern,id:'old-receipt'}})).data.code,'STALE_EXPORT');ok();
 // Uploads larger than the old 4 MiB cap, and useful device rejection diagnostics.
 const expanded='x'.repeat(4500000);const expandedSnapshot={...snapshot,code_xml:expanded,project:{...snapshot.project,base_sha256:await hash(expanded)}};
 assert.equal((await req('/api/device/state',expandedSnapshot,env.DEVICE_TOKEN)).data.ok,true);ok();
 const rejected=await req('/api/device/state',{...expandedSnapshot,project:{...expandedSnapshot.project,base_sha256:'wrong'}},env.DEVICE_TOKEN);
 assert.equal(rejected.status,400);assert.equal((await req('/api/status')).data.last_error.message,'Snapshot hash mismatch');ok();
 const oversized=await req('/api/device/state',{pad:'x'.repeat(8*1024*1024)},env.DEVICE_TOKEN);
 assert.equal(oversized.data.code,'SNAPSHOT_TOO_LARGE');assert.equal((await req('/api/status')).data.last_error.code,'SNAPSHOT_TOO_LARGE');ok();
 // Partial exports and project identity. Optional fixture is emitted by the Java exporter tests.
 let fixtures;
 if(process.argv[2])fixtures=JSON.parse(readFileSync(process.argv[2],'utf8'));
 else{
  const pid=await hash('project-A'),huge='<program><scenes><scene><script>'+('x'.repeat(100000))+'</script>',tail='</scene></scenes></program>';
  const make=async(value,rid,base='')=>{const xml=huge+value+tail,meta={format:1,project:'Same name',project_id:pid,request_id:rid,base_sha256:await hash(xml),scenes:[{name:'One'}],capabilities:{strict_request_id:true,project_identity:true}},files={},changed={};
   for(const [file,texts] of Object.entries({'project.json':[JSON.stringify(meta)],'code.xml':[huge,value,tail],'PROTOCOL.md':['protocol'],'supported-blocks.json':['[]']})){
    files[file]=[];for(const text of texts){const key=await hash(text);files[file].push({sha256:key,kind:file==='code.xml'&&text===value?'script':'scene',scene_id:'one',script_id:text===value?'small-script':''});changed[key]=text;}}
   return {sync_version:1,mode:base?'delta':'full',project_id:pid,project:'Same name',request_id:rid,base_sha256:base,target_sha256:meta.base_sha256,files,changed};};
  const full=await make('1','r1'),delta=await make('2','r2',full.target_sha256);for(const key of Object.keys(delta.changed))if(Object.hasOwn(full.changed,key))delete delta.changed[key];fixtures={full,delta};
 }
 const full=fixtures.full,delta=structuredClone(fixtures.delta);
 assert.equal((await req('/api/device/sync',full,env.DEVICE_TOKEN)).data.ok,true);ok();
 assert.equal((await req('/api/status')).data.project_id,full.project_id);ok();
 const syncRequest=(await req('/api/request',{})).data;assert.equal(syncRequest.expected_project_id,full.project_id);ok();
 assert.equal((await req('/api/device/sync',{...delta,request_id:syncRequest.request_id,project_id:'b'.repeat(64)},env.DEVICE_TOKEN)).data.code,'WRONG_PROJECT');ok();
 // Echo the fresh request in the project metadata fragment as well as in the envelope.
 const oldMeta=delta.files['project.json'][0].sha256,projectMeta=JSON.parse(delta.changed[oldMeta]);projectMeta.request_id=syncRequest.request_id;
 const metaText=JSON.stringify(projectMeta),metaHash=await hash(metaText);delete delta.changed[oldMeta];delta.changed[metaHash]=metaText;delta.files['project.json']=[{sha256:metaHash,kind:'project',scene_id:'',script_id:''}];delta.request_id=syncRequest.request_id;
 const deltaReply=(await req('/api/device/sync',delta,env.DEVICE_TOKEN)).data;
 assert.equal(deltaReply.ok,true);assert.equal(deltaReply.mode,'delta');assert.ok(deltaReply.parts_sent<Object.keys(full.changed).length);ok();
 let rebuilt='',nextOffset=0;do{const r=(await req('/api/read',{file:'code.xml',offset:nextOffset,limit:60000})).data;rebuilt+=r.text;nextOffset=r.next_offset;assert.equal(r.project_id,delta.project_id);}while(nextOffset!==null);
 assert.equal(await hash(rebuilt),delta.target_sha256);ok();
 const readProject=(await req('/api/read',{file:'project.json',limit:60000})).data;
 assert.equal(JSON.parse(readProject.text).request_id,delta.request_id);ok();
 assert.equal((await req('/api/device/sync',{...delta,base_sha256:'wrong'},env.DEVICE_TOKEN)).data.code,'FULL_EXPORT_REQUIRED');ok();
 const noChange={...delta,base_sha256:delta.target_sha256,changed:{}};
 assert.equal((await req('/api/device/sync',noChange,env.DEVICE_TOKEN)).data.parts_sent,0);ok();
 const corrupt=structuredClone(noChange);corrupt.changed[metaHash]='corrupted';assert.equal((await req('/api/device/sync',corrupt,env.DEVICE_TOKEN)).data.code,'FRAGMENT_HASH_MISMATCH');ok();
 const identityPatch={format:1,id:'identity-check',title:'Identity',project:delta.project,project_id:'f'.repeat(64),base_sha256:delta.target_sha256,request_id:delta.request_id,operations:[{op:'sprite.create',scene:'One',sprite:'Hero'}]};
 assert.equal((await req('/api/patch',{patch:identityPatch})).data.code,'WRONG_PROJECT');ok();
 identityPatch.project_id=delta.project_id;assert.equal((await req('/api/patch',{patch:identityPatch})).data.queued,true);ok();
 await req('/api/device/result',{patch_id:identityPatch.id,project_id:delta.project_id,project:delta.project,base_sha256:delta.target_sha256,request_id:delta.request_id,event_id:'id-end',status:'rejected',details:'test done'},env.DEVICE_TOKEN);
 assert.equal((await req('/api/device/state',snapshot,env.DEVICE_TOKEN)).data.ok,true);assert.equal((await req('/api/read',{file:'code.xml',limit:10})).data.text,Array.from(snapshot.code_xml).slice(0,10).join(''));ok();
 console.log('PASS: '+passed+' checks, SQLite-backed D1 adapter, auth, snapshots, Unicode chunks, stale patch invalidation, pending requests, MCP.');
}finally{rmSync(dir,{recursive:true,force:true})}
