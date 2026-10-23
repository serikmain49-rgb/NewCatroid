import worker from './newcatroid-worker.js';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,rmSync} from 'node:fs';
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
 assert.equal((await req(endpoint,{jsonrpc:'2.0',id:2,method:'tools/list'},null)).data.result.tools.length,4);ok();
 assert.equal((await req('/mcp/wrong',{jsonrpc:'2.0',id:3,method:'ping'},null)).status,401);ok();
 assert.equal((await req(endpoint,{jsonrpc:'2.0',method:'notifications/initialized'},null)).status,202);ok();
 const call=await req(endpoint,{jsonrpc:'2.0',id:4,method:'tools/call',params:{name:'read_project_file',arguments:{file:'code.xml',limit:10}}},null);
 assert.equal(call.data.result.isError,false);ok();
 const bad=await req(endpoint,{jsonrpc:'2.0',id:5,method:'tools/call',params:{name:'read_project_file',arguments:{file:'unknown'}}},null);
 assert.equal(bad.data.result.isError,true);ok();
 console.log('PASS: '+passed+' checks, SQLite-backed D1 adapter, auth, snapshots, Unicode chunks, stale patch invalidation, pending requests, MCP.');
}finally{rmSync(dir,{recursive:true,force:true})}
