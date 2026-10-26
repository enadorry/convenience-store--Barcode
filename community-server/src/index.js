// Public reads; invited contributors submit; only administrators publish reports.
class ApiError extends Error { constructor(status,message){super(message);this.status=status;} }
const fail=(status,message)=>{throw new ApiError(status,message);};
const json=(data,status=200)=>Response.json(data,{status,headers:{"Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
export function validJan(jan){
 if(typeof jan!=="string"||!/^([0-9]{8}|[0-9]{13})$/.test(jan))return false;
 let sum=0;for(let i=jan.length-2,p=0;i>=0;i--,p++)sum+=Number(jan[i])*(p%2===0?3:1);
 return (10-sum%10)%10===Number(jan.at(-1));
}
function text(value,max,required=true){
 if(typeof value!=="string")fail(400,"文字列の項目を確認してください。");
 const result=value.trim();if((required&&!result)||result.length>max||/[\x00-\x1f\x7f]/.test(result))fail(400,"入力の長さ・文字を確認してください。");
 return result;
}
const today=()=>new Date(Date.now()+9*3600000).toISOString().slice(0,10);
const earliest=()=>new Date(Date.parse(today())-90*86400000).toISOString().slice(0,10);
export function validateReport(body){
 if(!body||typeof body!=="object"||Array.isArray(body))fail(400,"投稿内容が不正です。");
 if(!validJan(body.jan))fail(400,"JANコードが不正です。");
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.request_id||""))fail(400,"投稿IDが不正です。");
 if(!Number.isSafeInteger(body.unit_cents)||body.unit_cents<1||body.unit_cents>110000000)fail(400,"1個分の税込価格を確認してください。");
 const observed=text(body.observed_on,10);
 const parsed=Date.parse(observed+"T00:00:00Z");
 if(!/^\d{4}-\d{2}-\d{2}$/.test(observed)||!Number.isFinite(parsed)||new Date(parsed).toISOString().slice(0,10)!==observed||observed>today()||observed<earliest())fail(400,"確認日は過去90日以内で指定してください。");
 if(body.shelf_confirmed!==true)fail(400,"店頭で確認した1個分の税込価格のみ投稿できます。");
 return {request_id:body.request_id.toLowerCase(),jan:body.jan,name:text(body.name,200),unit_cents:body.unit_cents,store_name:text(body.store_name,120),area:text(body.area??"",120,false),observed_on:observed};
}
export function validateBinding(body){
 if(!body||typeof body!=="object"||Array.isArray(body)||!validJan(body.jan))fail(400,"JANコードが不正です。");
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.request_id||""))fail(400,"投稿IDが不正です。");
 if(!Number.isSafeInteger(body.unit_cents)||body.unit_cents<1||body.unit_cents>110000000)fail(400,"税込価格が不正です。");
 if(!["manual","seven"].includes(body.source_kind))fail(400,"価格の取得元が不正です。");
 const date=text(body.observed_on,10);if(date!==today())fail(400,"作成日が不正です。");
 let source=text(body.source_url??"",500,false);
 if(body.source_kind==="seven"){
  let u;try{u=new URL(source);}catch{fail(400,"公式URLが不正です。");}
  if(u.protocol!=="https:"||u.hostname!=="www.sej.co.jp"||u.username||u.password||u.port||u.search||u.hash||!/^\/products\/a\/item\/[0-9]+\/$/.test(u.pathname))fail(400,"セブン公式の商品URLを指定してください。");
 }else if(source)fail(400,"手入力では取得URLを指定できません。");
 return {request_id:body.request_id.toLowerCase(),jan:body.jan,name:text(body.name,200),unit_cents:body.unit_cents,source_kind:body.source_kind,source_url:source,observed_on:date};
}
async function digest(value){return [...new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(value)))].map(b=>b.toString(16).padStart(2,"0")).join("");}
async function readBody(request){
 if(!(request.headers.get("Content-Type")||"").toLowerCase().startsWith("application/json"))fail(415,"JSONで送信してください。");
 const reader=request.body?.getReader();if(!reader)fail(400,"本文がありません。");
 let size=0,chunks=[];try{while(true){let {value,done}=await reader.read();if(done)break;size+=value.length;if(size>8192){await reader.cancel();fail(413,"本文が大きすぎます。");}chunks.push(value);}}finally{reader.releaseLock();}
 const data=new Uint8Array(size);let at=0;for(const c of chunks){data.set(c,at);at+=c.length;}
 try{return JSON.parse(new TextDecoder().decode(data));}catch{fail(400,"JSONが不正です。");}
}
async function admin(request,env){
 if(!env.ADMIN_TOKEN||env.ADMIN_TOKEN.length<32)fail(503,"管理用キーが未設定です。");
 const supplied=request.headers.get("Authorization")||"";
 const a=await digest(supplied),b=await digest("Bearer "+env.ADMIN_TOKEN);
 let diff=0;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);if(diff)fail(401,"管理者認証が必要です。");
}
async function contributor(request,env){
 const auth=request.headers.get("Authorization")||"";
 if(!/^Bearer [0-9a-f]{64}$/i.test(auth))fail(401,"投稿用キーを確認してください。");
 const row=await env.DB.prepare("SELECT id FROM contributors WHERE token_hash=? AND revoked=0").bind(await digest(auth.slice(7))).first();
 if(!row)fail(401,"投稿用キーが無効、または停止されています。");return row.id;
}
export default {
 async fetch(request,env){
  try{
   if(!env.DB)fail(503,"データベースが未設定です。");
   const url=new URL(request.url),path=url.pathname,method=request.method;
   if(path==="/health"&&method==="GET"){await env.DB.prepare("SELECT 1 FROM products LIMIT 1").all();return json({api_version:1,status:"ok",auto_bindings:true});}
   const productMatch=path.match(/^\/v1\/products\/([0-9]+)$/);
   if(productMatch&&method==="GET"){
    const jan=productMatch[1];if(!validJan(jan))fail(400,"JANコードが不正です。");
    const product=await env.DB.prepare("SELECT jan,name FROM products WHERE jan=?").bind(jan).first();
    const reports=await env.DB.prepare("SELECT id,name,unit_cents,store_name,area,observed_on FROM price_reports WHERE jan=? AND status='approved' AND observed_on>=? AND observed_on<=? ORDER BY observed_on DESC,created_at DESC,id DESC LIMIT 20").bind(jan,earliest(),today()).all();
    const bindings=await env.DB.prepare("SELECT id,jan,name,unit_cents,source_kind,source_url,observed_on FROM auto_bindings WHERE jan=? AND status='approved' AND observed_on>=? AND observed_on<=? ORDER BY observed_on DESC,created_at DESC,id DESC LIMIT 20").bind(jan,earliest(),today()).all();
    return json({api_version:1,product:reports.results.length?product:null,reports:reports.results,bindings:bindings.results});
   }
   if(path==="/v1/bindings"&&method==="POST"){
    const raw=await readBody(request);
    // Validate retry dates too, while allowing persisted queues from the last 90 days.
    const date=raw?.observed_on;
    if(typeof date!=="string"||!/^\d{4}-\d{2}-\d{2}$/.test(date)||!Number.isFinite(Date.parse(date+"T00:00:00Z"))||new Date(Date.parse(date+"T00:00:00Z")).toISOString().slice(0,10)!==date||date<earliest()||date>today())fail(400,"作成日が不正です。");
    const r={...validateBinding({...raw,observed_on:today()}),observed_on:date},hash=await digest(JSON.stringify(r));
    const now=Date.now(),bucket=await digest((request.headers.get("CF-Connecting-IP")||"local")+"|"+today());
    const limit=Math.max(1,Math.min(100,Number(env.AUTO_DAILY_LIMIT)||50));
    await env.DB.prepare("INSERT OR IGNORE INTO auto_bindings(id,request_id,payload_hash,rate_bucket,jan,name,unit_cents,source_kind,source_url,observed_on,created_at) SELECT ?,?,?,?,?,?,?,?,?,?,? WHERE (SELECT count(*) FROM auto_bindings WHERE rate_bucket=? AND created_at>?)<? AND (SELECT count(*) FROM auto_bindings WHERE created_at>?)<5000")
     .bind(crypto.randomUUID(),r.request_id,hash,bucket,r.jan,r.name,r.unit_cents,r.source_kind,r.source_url,r.observed_on,now,bucket,now-86400000,limit,now-86400000).run();
    const saved=await env.DB.prepare("SELECT id,status,payload_hash FROM auto_bindings WHERE request_id=?").bind(r.request_id).first();
    if(!saved)fail(429,"自動送信の上限です。時間を置いて再送してください。");
    if(saved.payload_hash!==hash)fail(409,"同じ投稿IDの内容が一致しません。");
    return json({id:saved.id,status:saved.status},202);
   }
   if(path==="/v1/reports"&&method==="POST"){
    const owner=await contributor(request,env),r=validateReport(await readBody(request)),hash=await digest(JSON.stringify(r)),now=Date.now();
    const limit=Math.max(1,Math.min(50,Number(env.DAILY_LIMIT)||30));
    // INSERT..SELECT enforces the daily limit in the same atomic SQL statement.
    await env.DB.prepare("INSERT OR IGNORE INTO price_reports(id,request_id,payload_hash,contributor_id,jan,name,unit_cents,store_name,area,observed_on,created_at) SELECT ?,?,?,?,?,?,?,?,?,?,? WHERE (SELECT count(*) FROM price_reports WHERE contributor_id=? AND created_at>?)<?")
      .bind(crypto.randomUUID(),r.request_id,hash,owner,r.jan,r.name,r.unit_cents,r.store_name,r.area,r.observed_on,now,owner,now-86400000,limit).run();
    const saved=await env.DB.prepare("SELECT id,status,payload_hash,contributor_id FROM price_reports WHERE request_id=?").bind(r.request_id).first();
    if(!saved)fail(429,"24時間の投稿上限です。時間を置いて再送してください。");
    if(saved.contributor_id!==owner||saved.payload_hash!==hash)fail(409,"同じ投稿IDの内容が一致しません。");
    return json({id:saved.id,status:saved.status},202);
   }
   if(path.startsWith("/v1/admin/")){
    await admin(request,env);
    if(path==="/v1/admin/bindings"&&method==="GET")return json(await env.DB.prepare("SELECT id,jan,name,unit_cents,source_kind,source_url,observed_on,created_at FROM auto_bindings WHERE status='pending' ORDER BY created_at LIMIT 100").all());
    const bindingReview=path.match(/^\/v1\/admin\/bindings\/([0-9a-f-]{36})\/(approve|reject)$/);
    if(bindingReview&&method==="POST"){
     if(!await env.DB.prepare("SELECT id FROM auto_bindings WHERE id=?").bind(bindingReview[1]).first())fail(404,"ひも付けがありません。");
     const status=bindingReview[2]==="approve"?"approved":"rejected";
     await env.DB.prepare("UPDATE auto_bindings SET status=?,reviewed_at=? WHERE id=?").bind(status,Date.now(),bindingReview[1]).run();
     return json({status});
    }
    if(path==="/v1/admin/contributors"&&method==="POST"){
     const label=text((await readBody(request))?.label,80),id=crypto.randomUUID();
     const token=[...crypto.getRandomValues(new Uint8Array(32))].map(b=>b.toString(16).padStart(2,"0")).join("");
     await env.DB.prepare("INSERT INTO contributors(id,label,token_hash,created_at) VALUES(?,?,?,?)").bind(id,label,await digest(token),Date.now()).run();
     return json({id,label,token},201);
    }
    if(path==="/v1/admin/contributors"&&method==="GET")return json(await env.DB.prepare("SELECT id,label,revoked,created_at FROM contributors ORDER BY created_at DESC LIMIT 200").all());
    const revoke=path.match(/^\/v1\/admin\/contributors\/([0-9a-f-]{36})\/revoke$/);
    if(revoke&&method==="POST"){await env.DB.prepare("UPDATE contributors SET revoked=1 WHERE id=?").bind(revoke[1]).run();return json({status:"revoked"});}
    if(path==="/v1/admin/reports"&&method==="GET")return json(await env.DB.prepare("SELECT id,jan,name,unit_cents,store_name,area,observed_on,contributor_id,created_at FROM price_reports WHERE status='pending' ORDER BY created_at LIMIT 100").all());
    const review=path.match(/^\/v1\/admin\/reports\/([0-9a-f-]{36})\/(approve|reject)$/);
    if(review&&method==="POST"){
     const row=await env.DB.prepare("SELECT id FROM price_reports WHERE id=?").bind(review[1]).first();if(!row)fail(404,"投稿がありません。");
     const status=review[2]==="approve"?"approved":"rejected";
     await env.DB.batch([
      env.DB.prepare("UPDATE price_reports SET status=?,reviewed_at=? WHERE id=?").bind(status,Date.now(),review[1]),
      env.DB.prepare("INSERT INTO products(jan,name,created_at) SELECT jan,name,created_at FROM price_reports WHERE jan=(SELECT jan FROM price_reports WHERE id=?) AND status='approved' ORDER BY reviewed_at DESC,created_at DESC,id DESC LIMIT 1 ON CONFLICT(jan) DO UPDATE SET name=excluded.name").bind(review[1]),
      env.DB.prepare("DELETE FROM products WHERE jan=(SELECT jan FROM price_reports WHERE id=?) AND NOT EXISTS (SELECT 1 FROM price_reports r WHERE r.jan=products.jan AND r.status='approved')").bind(review[1])
     ]);
     return json({status:review[2]==="approve"?"approved":"rejected"});
    }
   }
   fail(404,"このAPIはありません。");
  }catch(e){return json({error:e instanceof ApiError?e.message:"処理に失敗しました。時間を置いて再試行してください。"},e instanceof ApiError?e.status:500);}
 }
};
