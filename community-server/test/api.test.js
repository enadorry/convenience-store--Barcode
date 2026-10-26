import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import worker,{validJan} from '../src/index.js';
const scratch=[];
after(()=>scratch.forEach(d=>rmSync(d,{recursive:true,force:true})));
class DB {
 constructor(file){this.file=file;}
 call(statements){return JSON.parse(execFileSync('python3',['test/sqlite_bridge.py',this.file],{input:JSON.stringify({statements}),encoding:'utf8',stdio:['pipe','pipe','pipe']}));}
 prepare(sql){const db=this;return {sql,args:[],bind(...args){this.args=args;return this;},async all(){return db.call([this])[0];},async first(){return db.call([this])[0].results[0]??null;},async run(){return db.call([this])[0];}};}
 async batch(statements){return this.call(statements);}
}
async function setup(){
 const dir=mkdtempSync(join(tmpdir(),'kaimono-db-'));scratch.push(dir);const file=join(dir,'test.sqlite');
 execFileSync('python3',['test/sqlite_bridge.py',file],{input:JSON.stringify({schema:readFileSync('migrations/0001_catalog.sql','utf8')+readFileSync('migrations/0002_auto_bindings.sql','utf8')}),stdio:['pipe','pipe','pipe']});
 const env={DB:new DB(file),ADMIN_TOKEN:'a'.repeat(64)};
 const call=async(path,method='GET',body,token='')=>{
  const headers={};if(token)headers.Authorization='Bearer '+token;if(body!==undefined)headers['Content-Type']='application/json';
  const response=await worker.fetch(new Request('https://test.example'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)}),env);
  return {status:response.status,body:await response.json()};
 };
 const contributor=await call('/v1/admin/contributors','POST',{label:'friend'},env.ADMIN_TOKEN);assert.equal(contributor.status,201);
 return {env,call,token:contributor.body.token,contributor:contributor.body.id};
}
const report=(changes={})=>({request_id:crypto.randomUUID(),jan:'4901234567894',name:'テスト お菓子 60g',unit_cents:18252,store_name:'テストストア',area:'長崎県',observed_on:new Date(Date.now()+9*3600000).toISOString().slice(0,10),shelf_confirmed:true,...changes});
test('JAN checksum validation',()=>{assert.equal(validJan('4901234567894'),true);assert.equal(validJan('12345670'),true);assert.equal(validJan('4901234567890'),false);});
test('submit -> pending -> approve -> public scan, without credentials leaking',async()=>{
 const {env,call,token}=await setup(),payload=report();
 let r=await call('/v1/reports','POST',payload,token);assert.equal(r.status,202);assert.equal(r.body.status,'pending');const id=r.body.id;
 assert.equal((await call('/v1/products/'+payload.jan)).body.reports.length,0);
 assert.equal((await call('/v1/admin/reports','GET',undefined,env.ADMIN_TOKEN)).body.results.length,1);
 assert.equal((await call('/v1/admin/reports/'+id+'/approve','POST',{},env.ADMIN_TOKEN)).status,200);
 const publicData=(await call('/v1/products/'+payload.jan)).body;
 assert.equal(publicData.reports[0].unit_cents,18252);assert.equal(publicData.product.jan,payload.jan);
 assert.ok(!JSON.stringify(publicData).includes(token));assert.ok(!JSON.stringify(publicData).includes('contributor_id'));
});
test('two contributors can report different shops and prices under same JAN',async()=>{
 const {call,env,token}=await setup();const other=(await call('/v1/admin/contributors','POST',{label:'friend2'},env.ADMIN_TOKEN)).body.token;
 for(const [key,price,shop] of [[token,18000,'店A'],[other,20000,'店B']]){const r=await call('/v1/reports','POST',report({unit_cents:price,store_name:shop}),key);await call('/v1/admin/reports/'+r.body.id+'/approve','POST',{},env.ADMIN_TOKEN);}
 const rows=(await call('/v1/products/4901234567894')).body.reports;assert.equal(rows.length,2);assert.deepEqual(rows.map(r=>r.unit_cents).sort(),[18000,20000]);
});
test('retries are idempotent; reused id with different payload conflicts',async()=>{
 const {call,token}=await setup(),p=report();const responses=await Promise.all([call('/v1/reports','POST',p,token),call('/v1/reports','POST',p,token)]);
 assert.equal(responses[0].body.id,responses[1].body.id);assert.equal((await call('/v1/reports','POST',{...p,name:'別の商品'},token)).status,409);
});
test('authentication, revocation and admin boundaries',async()=>{
 const {call,env,token,contributor}=await setup();assert.equal((await call('/v1/reports','POST',report())).status,401);
 assert.equal((await call('/v1/admin/reports','GET',undefined,token)).status,401);
 assert.equal((await call('/v1/admin/contributors/'+contributor+'/revoke','POST',{},env.ADMIN_TOKEN)).status,200);
 assert.equal((await call('/v1/reports','POST',report(),token)).status,401);
});
test('malformed prices, dates, JANs, control chars and missing confirmation rejected',async()=>{
 const {call,token}=await setup();
 for(const changes of [{unit_cents:0},{unit_cents:1.1},{unit_cents:110000001},{observed_on:'2026-02-30'},{observed_on:'2099-01-01'},{observed_on:'2000-01-01'},{jan:'4901234567890'},{shelf_confirmed:false},{store_name:''},{name:'bad\u001bname'}])assert.equal((await call('/v1/reports','POST',report(changes),token)).status,400,JSON.stringify(changes));
});
test('daily limit does not prevent retrying already accepted report',async()=>{
 const {call,env,token}=await setup();env.DAILY_LIMIT=2;const p=report();assert.equal((await call('/v1/reports','POST',p,token)).status,202);
 assert.equal((await call('/v1/reports','POST',report(),token)).status,202);assert.equal((await call('/v1/reports','POST',report(),token)).status,429);assert.equal((await call('/v1/reports','POST',p,token)).status,202);
});
test('approved report can be withdrawn; old prices do not stay in public results',async()=>{
 const {call,env,token}=await setup();let r=await call('/v1/reports','POST',report(),token);await call('/v1/admin/reports/'+r.body.id+'/approve','POST',{},env.ADMIN_TOKEN);
 await call('/v1/admin/reports/'+r.body.id+'/reject','POST',{},env.ADMIN_TOKEN);assert.equal((await call('/v1/products/4901234567894')).body.product,null);
});
test('database batches roll back on failure',async()=>{
 const {env}=await setup();await assert.rejects(env.DB.batch([env.DB.prepare("INSERT INTO products VALUES('12345670','rollback',0)"),env.DB.prepare('INSERT INTO missing_table VALUES(1)')]));assert.equal(await env.DB.prepare("SELECT * FROM products WHERE jan='12345670'").first(),null);
});
test('JSON size bounded and unknown endpoints fail closed',async()=>{
 const {call,token}=await setup();assert.equal((await call('/v1/reports','POST',report({name:'x'.repeat(9000)}),token)).status,413);assert.equal((await call('/unknown')).status,404);assert.equal((await call('/v1/products/12345678')).status,400);
});

test('withdrawing an incorrect name allows an approved correction to replace canonical name',async()=>{
 const {call,env,token}=await setup();
 const wrong=await call('/v1/reports','POST',report({name:'間違った商品名'}),token);await call('/v1/admin/reports/'+wrong.body.id+'/approve','POST',{},env.ADMIN_TOKEN);
 await call('/v1/admin/reports/'+wrong.body.id+'/reject','POST',{},env.ADMIN_TOKEN);
 const corrected=await call('/v1/reports','POST',report({name:'訂正した商品名'}),token);await call('/v1/admin/reports/'+corrected.body.id+'/approve','POST',{},env.ADMIN_TOKEN);
 assert.equal((await call('/v1/products/4901234567894')).body.product.name,'訂正した商品名');
});
const binding=(changes={})=>({request_id:crypto.randomUUID(),jan:'4901234567894',name:'自動 お菓子 60g',unit_cents:18252,source_kind:'manual',source_url:'',observed_on:new Date(Date.now()+9*3600000).toISOString().slice(0,10),...changes});
test('keyless mapping -> pending -> moderation -> public mapping; separate from shelf prices',async()=>{
 const {call,env}=await setup(),p=binding();const accepted=await call('/v1/bindings','POST',p);
 assert.equal(accepted.status,202);assert.equal(accepted.body.status,'pending');
 assert.equal((await call('/v1/products/'+p.jan)).body.bindings.length,0);
 assert.equal((await call('/v1/admin/bindings')).status,401);
 assert.equal((await call('/v1/admin/bindings','GET',undefined,env.ADMIN_TOKEN)).body.results.length,1);
 await call('/v1/admin/bindings/'+accepted.body.id+'/approve','POST',{},env.ADMIN_TOKEN);
 let pub=(await call('/v1/products/'+p.jan)).body;
 assert.equal(pub.reports.length,0);assert.equal(pub.bindings[0].unit_cents,18252);assert.equal(pub.bindings[0].source_kind,'manual');
 assert.ok(!JSON.stringify(pub).includes('rate_bucket'));assert.ok(!JSON.stringify(pub).includes('payload_hash'));
 await call('/v1/admin/bindings/'+accepted.body.id+'/reject','POST',{},env.ADMIN_TOKEN);
 assert.equal((await call('/v1/products/'+p.jan)).body.bindings.length,0);
});
test('automatic submission is idempotent, bounded and rejects conflicting retries',async()=>{
 const {call,env}=await setup();env.AUTO_DAILY_LIMIT=1;const p=binding();const one=await call('/v1/bindings','POST',p);assert.equal(one.status,202);
 const two=await call('/v1/bindings','POST',p);assert.equal(two.status,202);assert.equal(one.body.id,two.body.id);
 assert.equal((await call('/v1/bindings','POST',{...p,unit_cents:19000})).status,409);
 assert.equal((await call('/v1/bindings','POST',binding())).status,429);
});
test('automatic mapping validates source, URL, dates, barcode, cents and strips extras',async()=>{
 const {call,env}=await setup();
 for(const changes of [{unit_cents:0},{unit_cents:1.5},{jan:'12345678'},{name:''},{source_kind:'shelf'},{source_kind:'seven',source_url:'https://evil.example/products/a/item/123/'},{source_kind:'seven',source_url:'https://www.sej.co.jp@evil.example/products/a/item/123/'},{source_kind:'manual',source_url:'https://example.com/'},{observed_on:'2099-01-01'},{observed_on:'2026-02-30'}])assert.equal((await call('/v1/bindings','POST',binding(changes))).status,400,JSON.stringify(changes));
 const r=await call('/v1/bindings','POST',binding({source_kind:'seven',source_url:'https://www.sej.co.jp/products/a/item/123/',walletBalance:1000}));assert.equal(r.status,202);
 const rows=(await call('/v1/admin/bindings','GET',undefined,env.ADMIN_TOKEN)).body.results;assert.equal(rows[0].source_kind,'seven');assert.ok(!JSON.stringify(rows).includes('walletBalance'));
});
