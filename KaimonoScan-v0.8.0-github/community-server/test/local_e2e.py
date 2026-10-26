"""Actual local Workers + D1 integration. Requires npm ci, Node.js and Python3."""
from pathlib import Path
import datetime,json,os,secrets,subprocess,tempfile,time,urllib.request,uuid,shutil
root=Path(__file__).resolve().parents[1]
node=shutil.which('node')
wrangler=root/'node_modules/wrangler/bin/wrangler.js'
env=os.environ.copy();env['WRANGLER_SEND_METRICS']='false';env['CI']='1'
with tempfile.TemporaryDirectory(prefix='kaimono-worker-') as temp:
 temp=Path(temp);admin=secrets.token_hex(32);port=8791
 config={'name':'kaimono-local-test','main':str(root/'src/index.js'),'compatibility_date':'2026-10-23','vars':{'ADMIN_TOKEN':admin},'d1_databases':[{'binding':'DB','database_name':'kaimono-community','database_id':'00000000-0000-0000-0000-000000000000','migrations_dir':str(root/'migrations')}]}
 path=temp/'wrangler.json';path.write_text(json.dumps(config));path.chmod(0o600)
 common=['--config',str(path),'--persist-to',str(temp/'state')]
 with (temp/'worker.log').open('w+') as log:
  subprocess.run([node,str(wrangler),'d1','migrations','apply','kaimono-community','--local',*common],cwd=root,env=env,stdout=log,stderr=log,check=True,timeout=60)
  process=subprocess.Popen([node,str(wrangler),'dev','--ip','127.0.0.1','--port',str(port),*common],cwd=root,env=env,stdout=log,stderr=log)
  def api(path,method='GET',body=None,token=None):
   headers={'Content-Type':'application/json'}
   if token:headers['Authorization']='Bearer '+token
   request=urllib.request.Request('http://127.0.0.1:'+str(port)+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
   with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request,timeout=5) as response:return json.load(response)
  try:
   ready=False
   for _ in range(100):
    if process.poll() is not None:raise RuntimeError('Worker exited before startup. See local runtime compatibility.')
    try:
     ready=api('/health')['status']=='ok'
     if ready:break
    except Exception:time.sleep(.2)
   if not ready:raise RuntimeError('Worker did not become ready.')
   date=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=9)).date().isoformat();ids=[]
   for store,price in [('テスト店A',18252),('テスト店B',20000)]:
    invite=api('/v1/admin/contributors','POST',{'label':'local-e2e'},admin)
    payload={'request_id':str(uuid.uuid4()),'jan':'4901234567894','name':'共有テスト60g','unit_cents':price,'store_name':store,'area':'長崎県','observed_on':date,'shelf_confirmed':True}
    response=api('/v1/reports','POST',payload,invite['token']);assert response['status']=='pending';assert api('/v1/reports','POST',payload,invite['token'])['id']==response['id'];ids.append(response['id'])
   assert api('/v1/products/4901234567894')['reports']==[]
   for id in ids:assert api('/v1/admin/reports/'+id+'/approve','POST',{},admin)['status']=='approved'
   public=api('/v1/products/4901234567894');assert len(public['reports'])==2;assert sorted(r['unit_cents'] for r in public['reports'])==[18252,20000];assert all('contributor_id' not in r for r in public['reports'])
   print('Actual local Workers + D1: two users, submit, duplicate retry, approve, public JAN lookup: PASS')
  finally:
   process.terminate()
   try:process.wait(timeout=10)
   except subprocess.TimeoutExpired:process.kill();process.wait()
