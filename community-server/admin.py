"""Administrator CLI. Credentials come from environment variables, never command-line arguments."""
import argparse,json,os,urllib.request,urllib.parse,sys
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):return None
p=argparse.ArgumentParser(description='Kaimono shared catalog administration')
p.add_argument('action',choices=['invite','contributors','pending','approve','reject','revoke','bindings','approve-binding','reject-binding'])
p.add_argument('value',nargs='?')
a=p.parse_args()
base=os.environ.get('COMMUNITY_API_URL','').rstrip('/')
u=urllib.parse.urlsplit(base)
if u.scheme!='https' or not u.hostname or u.username or u.password or u.path or u.query or u.fragment:
 raise SystemExit('Set COMMUNITY_API_URL to the HTTPS origin.')
token=os.environ.get('COMMUNITY_ADMIN_TOKEN','')
if len(token)<32:raise SystemExit('Set COMMUNITY_ADMIN_TOKEN. Do not put it into Android or GitHub.')
method='GET';body=None
if a.action=='invite':
 if not a.value:p.error('invite needs a short private label')
 path='/v1/admin/contributors';method='POST';body={'label':a.value}
elif a.action=='contributors':path='/v1/admin/contributors'
elif a.action=='pending':path='/v1/admin/reports'
elif a.action=='bindings':path='/v1/admin/bindings'
else:
 import uuid
 try:identifier=str(uuid.UUID(a.value))
 except Exception:p.error('action needs a valid report/contributor UUID')
 path='/v1/admin/'+('contributors/'+identifier+'/revoke' if a.action=='revoke' else 'reports/'+identifier+'/'+a.action)
 if a.action.endswith('-binding'):path='/v1/admin/bindings/'+identifier+'/'+a.action.split('-')[0]
 method='POST';body={}
headers={'Authorization':'Bearer '+token,'Accept':'application/json','User-Agent':'KaimonoScan-Admin/0.7.0'}
if body is not None:headers['Content-Type']='application/json'
request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
try:
 with urllib.request.build_opener(NoRedirect).open(request,timeout=30) as response:
  print(json.dumps(json.load(response),ensure_ascii=False,indent=2))
except urllib.error.HTTPError as e:
 print('API request failed (HTTP '+str(e.code)+').',file=sys.stderr)
 if e.code==401:print('Check COMMUNITY_ADMIN_TOKEN matches the Worker ADMIN_TOKEN secret.',file=sys.stderr)
 elif e.code==503:print('Check that ADMIN_TOKEN and the D1 binding are configured on the Worker.',file=sys.stderr)
 elif e.code==403:print('Access was forbidden before the expected API authentication response. Check Cloudflare access/security rules and the API URL.',file=sys.stderr)
 sys.exit(1)
