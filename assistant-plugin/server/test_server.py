import unittest,tempfile,threading,json,hashlib,urllib.request,urllib.error
from http.server import ThreadingHTTPServer
from server import Store,handler
class Tests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.s=Store(self.tmp.name)
  self.http=ThreadingHTTPServer(('127.0.0.1',0),handler(self.s));threading.Thread(target=self.http.serve_forever,daemon=True).start()
  self.url='http://127.0.0.1:'+str(self.http.server_port)
 def tearDown(self):self.http.shutdown();self.http.server_close();self.tmp.cleanup()
 def call(self,path,data=None,role='assistant',origin=None):
  headers={'Authorization':'Bearer '+self.s.keys[role],'Content-Type':'application/json'}
  if origin:headers['Origin']=origin
  req=urllib.request.Request(self.url+path,data=None if data is None else json.dumps(data).encode(),headers=headers)
  try:
   with urllib.request.urlopen(req) as r:return r.status,json.loads(r.read() or b'null')
  except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
 def state(self,xml='<project/>',rid=''):
  return {'project':{'project':'Game','base_sha256':hashlib.sha256(xml.encode()).hexdigest()},'code_xml':xml,'protocol':'rules','supported_blocks':[],'request_id':rid}
 def patch(self):
  return {'format':1,'project':'Game','base_sha256':self.s.state['project']['base_sha256'],'id':'p1','title':'Change','operations':[{'op':'script.add'}]}
 def test_roles(self):
  self.assertEqual(self.call('/api/status',role='device')[0],401)
  self.assertEqual(self.call('/api/device/poll')[0],401)
  self.assertEqual(self.call('/api/patch',{'patch':{}},role='device')[0],401)
 def test_origin(self):self.assertEqual(self.call('/api/status',origin='https://evil.invalid')[0],403)
 def test_roundtrip(self):
  _,r=self.call('/api/request',{});rid=r['request_id']
  self.assertEqual(self.call('/api/device/poll',role='device')[1]['request_id'],rid)
  self.assertEqual(self.call('/api/device/state',self.state(rid=rid),'device')[0],200)
  self.assertIsNone(self.s.pending)
  self.assertEqual(self.call('/api/read',{'file':'code.xml','offset':0,'limit':3})[1]['text'],'<pr')
  self.assertEqual(self.call('/api/patch',{'patch':self.patch()})[0],200)
  self.assertEqual(self.call('/api/device/patch',role='device')[1]['patch']['id'],'p1')
  self.call('/api/device/state',self.state('<changed/>'),'device')
  self.assertIsNone(self.call('/api/device/patch',role='device')[1]['patch'])
 def test_stale(self):
  self.s.accept_state(self.state());p=self.patch();p['base_sha256']='bad'
  self.assertEqual(self.call('/api/patch',{'patch':p})[0],400)
 def test_bad_snapshot(self):
  s=self.state();s['code_xml']='other'
  self.assertEqual(self.call('/api/device/state',s,'device')[0],400);self.assertIsNone(self.s.state)
 def test_request_race(self):
  rid=self.s.request()['request_id'];self.s.accept_state(self.state(rid='older'))
  self.assertEqual(self.s.pending['request_id'],rid)
  self.s.accept_state({'error':'Game running','request_id':rid});self.assertIsNone(self.s.pending);self.assertIsNotNone(self.s.last_error)
 def test_persistence(self):
  self.s.accept_state(self.state());self.s.submit(self.patch());new=Store(self.tmp.name)
  self.assertEqual(new.keys,self.s.keys);self.assertEqual(new.state,self.s.state);self.assertEqual(new.patch,self.s.patch)
 def test_mcp(self):
  def rpc(method,params={}):return self.call('/mcp',{'jsonrpc':'2.0','id':1,'method':method,'params':params})[1]
  self.assertEqual(rpc('initialize')['result']['protocolVersion'],'2025-03-26')
  self.assertEqual(len(rpc('tools/list')['result']['tools']),5)
  self.assertFalse(rpc('tools/call',{'name':'request_state'})['result']['isError'])
  self.assertTrue(rpc('tools/call',{'name':'read_project_file','arguments':{'file':'code.xml'}})['result']['isError'])
  self.assertEqual(rpc('bogus')['error']['code'],-32601)
 def test_bad_json(self):
  self.assertEqual(self.call('/api/patch',{'patch':None})[0],400)
 def test_private_mcp_link(self):
  path='/mcp/'+self.s.keys['mcp_link']
  req=urllib.request.Request(self.url+path,data=json.dumps({'jsonrpc':'2.0','id':1,'method':'tools/list'}).encode(),headers={'Content-Type':'application/json'})
  with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(req)
  self.assertEqual(e.exception.code,401)
  self.s.link_access=True
  with urllib.request.urlopen(req) as r:self.assertEqual(len(json.load(r)['result']['tools']),5)
  wrong=urllib.request.Request(self.url+'/mcp/wrong',data=b'{}')
  with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(wrong)
  self.assertEqual(e.exception.code,401)
 def test_full_archive(self):
  import zipfile,pathlib
  archive=pathlib.Path(__file__).resolve().parents[2]/'upload/NewCatroid-context-1792698113261.zip'
  if not archive.exists():self.skipTest('Original export unavailable')
  with zipfile.ZipFile(archive) as z:
   snapshot={'project':json.loads(z.read('project.json')),'code_xml':z.read('code.xml').decode(),'protocol':z.read('PROTOCOL.md').decode(),'supported_blocks':json.loads(z.read('supported-blocks.json'))}
  self.assertEqual(self.call('/api/device/state',snapshot,'device')[0],200)
  pieces=[];offset=0
  while True:
   _,part=self.call('/api/read',{'file':'code.xml','offset':offset,'limit':10000});pieces.append(part['text']);offset=part['next_offset']
   if offset is None:break
  self.assertEqual(''.join(pieces),snapshot['code_xml'])
class LifecycleTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.s=Store(self.tmp.name)
  self.rid=self.s.request()['request_id'];xml='<program/>';self.sha=hashlib.sha256(xml.encode()).hexdigest()
  self.s.accept_state({'project':{'project':'Game','base_sha256':self.sha,'capabilities':{'strict_request_id':True}},'code_xml':xml,'protocol':'test','supported_blocks':[],'request_id':self.rid})
 def tearDown(self):self.tmp.cleanup()
 def patch(self,id='life'):return {'format':1,'id':id,'title':'test','project':'Game','base_sha256':self.sha,'request_id':self.rid,'operations':[{'op':'scene.create','scene':'Room'}]}
 def test_stale_request(self):
  p=self.patch();p['request_id']='old'
  with self.assertRaisesRegex(ValueError,'STALE_EXPORT'):self.s.submit(p)
  self.s.request()
  with self.assertRaisesRegex(ValueError,'STALE_EXPORT'):self.s.submit(self.patch())
 def test_results_and_replay_after_restart(self):
  self.assertEqual(self.s.submit(self.patch())['status'],'queued')
  e={'patch_id':'life','project':'Game','base_sha256':self.sha,'request_id':self.rid,'status':'preview','details':'opened','event_id':'1'}
  self.s.result(e);self.assertEqual(self.s.status()['patch_result']['status'],'preview')
  self.assertTrue(self.s.result(e)['duplicate']);e.update(status='applied',event_id='2');self.s.result(e)
  self.assertIsNone(self.s.patch);s2=Store(self.tmp.name)
  self.assertEqual(s2.patch_status('life')['result']['status'],'applied')
  with self.assertRaisesRegex(ValueError,'DUPLICATE_PATCH'):s2.submit(self.patch())
  e.update(status='preview',event_id='3');self.s.result(e);self.assertEqual(self.s.patch_status('life')['result']['status'],'applied')
 def test_rejection_error(self):
  for state in ['rejected','error']:
   self.s.submit(self.patch(state));self.s.result({'patch_id':state,'project':'Game','base_sha256':self.sha,'request_id':self.rid,'status':state,'details':'why','event_id':state})
   self.assertEqual(self.s.patch_status(state)['result']['status'],state);self.assertIsNone(self.s.patch)
 def test_hash_not_substituted(self):
  p=self.patch();p['base_sha256']='wrong'
  with self.assertRaises(ValueError):self.s.submit(p)
  self.assertEqual(p['base_sha256'],'wrong')
if __name__=='__main__':unittest.main(verbosity=2)
