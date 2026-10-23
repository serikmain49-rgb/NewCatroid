import json,sqlite3,sys
req=json.load(sys.stdin)
c=sqlite3.connect(req['path']);c.row_factory=sqlite3.Row
out=[]
try:
 with c:
  for q in req['queries']:
   before=c.total_changes;r=c.execute(q['sql'],q['args']);rows=[dict(x) for x in r.fetchall()]
   out.append({'results':rows,'meta':{'changes':c.total_changes-before},'success':True})
 print(json.dumps(out))
finally:c.close()
