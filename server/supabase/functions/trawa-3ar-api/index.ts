import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const PUBLIC_KEY = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || "";
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Cache-Control": "no-store",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" } });
const clean = (v: unknown) => String(v ?? "").replace(/\u0000/g, "").trim();
const tokenOf = (req: Request) => { const a = req.headers.get("authorization") || ""; return a.startsWith("Bearer ") ? a.slice(7).trim() : ""; };

async function requireUser(req: Request) {
  const token = tokenOf(req);
  if (!token) throw json({ error: "Authentication required" }, 401);
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw json({ error: "Invalid or expired session" }, 401);
  return { user: data.user, token };
}
async function publicAuthClient() {
  if (!PUBLIC_KEY) throw new Error("Supabase public auth key is not configured");
  return createClient(SUPABASE_URL, PUBLIC_KEY, { auth: { persistSession: false, autoRefreshToken: false } });
}
const userPayload = (u: any) => ({ id: u.id, email: u.email || "", name: u.user_metadata?.full_name || u.user_metadata?.name || (u.email || "User").split("@")[0] });

function needsWeb(q: string) {
  return /(latest|today|current|right now|recent|news|breaking|price|weather|score|schedule|release|update|النهارده|اليوم|دلوقتي|حاليًا|اخر|آخر|أخبار|خبر|سعر|طقس|ماتش|مباراة|نتيجة|تحديث|إصدار)/i.test(q);
}
async function searchWeb(query: string) {
  const key = Deno.env.get("TAVILY_API_KEY") || "";
  if (!key) return [] as any[];
  try {
    const r = await fetch("https://api.tavily.com/search", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ api_key: key, query, max_results: 5, search_depth: "advanced", include_answer: false }) });
    if (!r.ok) return [];
    const d = await r.json();
    return (d.results || []).map((x: any) => ({ title: x.title || "", url: x.url || "", content: x.content || "" })).filter((x: any) => x.url);
  } catch { return []; }
}

async function loadAiConfig() {
  const [runtime, prompt, providers, models, route, flags] = await Promise.all([
    admin.from("ai_runtime_config").select("*").eq("id", true).maybeSingle(),
    admin.from("ai_prompts").select("content,version").eq("prompt_key", "base_system").eq("enabled", true).maybeSingle(),
    admin.from("ai_providers").select("*").eq("enabled", true).order("priority", { ascending: true }),
    admin.from("ai_models").select("*").eq("enabled", true).order("priority", { ascending: true }),
    admin.from("ai_routes").select("*").eq("route_key", "default").eq("enabled", true).maybeSingle(),
    admin.from("ai_feature_flags").select("flag_key,enabled,config").eq("enabled", true),
  ]);
  for (const x of [runtime, prompt, providers, models, route, flags]) if ((x as any).error) throw (x as any).error;
  const flagMap = Object.fromEntries((flags.data || []).map((x: any) => [x.flag_key, x]));
  const byId = new Map((models.data || []).map((m: any) => [m.id, m]));
  const ordered = Array.isArray(route.data?.model_chain) && route.data.model_chain.length
    ? route.data.model_chain.map((id: string) => byId.get(id)).filter(Boolean)
    : [...(models.data || [])];
  return { runtime: runtime.data || { max_history: 60, max_memories: 40, web_search_enabled: true, attachment_context_enabled: true, temperature: 0.65, max_output_tokens: 8192 }, prompt: prompt.data?.content || "You are 3AR V1 Pro, the unified AI inside TRAWA.", providers: providers.data || [], models: ordered, flags: flagMap };
}

function providerFor(model: any, providers: any[]) { return providers.find((p: any) => p.id === model.provider_id); }
function providerKey(p: any) { return p ? Deno.env.get(p.secret_env) || "" : ""; }
function modelOptions(m: any) { return m.options && typeof m.options === "object" ? m.options : {}; }

async function* openAiStream(p: any, m: any, messages: any[], runtime: any) {
  const key = providerKey(p); if (!key) throw new Error(`${p.slug}: secret not configured`);
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), p.timeout_ms || 90000);
  try {
    const opts = modelOptions(m);
    const r = await fetch(p.endpoint, { method: "POST", signal: controller.signal, headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` }, body: JSON.stringify({ model: m.model, messages, stream: true, temperature: runtime.temperature, max_tokens: runtime.max_output_tokens, ...opts, ...(p.config || {}) }) });
    if (!r.ok) throw new Error(`${p.slug}/${m.model} HTTP ${r.status}: ${(await r.text()).slice(0, 500)}`);
    const reader = r.body?.getReader(); if (!reader) throw new Error(`${p.slug}/${m.model}: missing stream body`);
    const decoder = new TextDecoder(); let buffer = "";
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      buffer += decoder.decode(value, { stream: true }); const lines = buffer.split("\n"); buffer = lines.pop() || "";
      for (const line of lines) { const s = line.trim(); if (!s.startsWith("data:")) continue; const d = s.slice(5).trim(); if (!d || d === "[DONE]") continue; try { const j = JSON.parse(d); const delta = j.choices?.[0]?.delta?.content; if (typeof delta === "string" && delta) yield delta; } catch {} }
    }
  } finally { clearTimeout(timer); }
}

async function* geminiStream(p: any, m: any, messages: any[], runtime: any) {
  const key = providerKey(p); if (!key) throw new Error(`${p.slug}: secret not configured`);
  const system = messages.find((x: any) => x.role === "system")?.content || "";
  const contents = messages.filter((x: any) => x.role !== "system").map((x: any) => ({ role: x.role === "assistant" ? "model" : "user", parts: [{ text: x.content || "" }] }));
  const url = `${p.endpoint.replace(/\/$/, "")}/v1beta/models/${encodeURIComponent(m.model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(key)}`;
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), p.timeout_ms || 90000);
  try {
    const r = await fetch(url, { method: "POST", signal: controller.signal, headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system_instruction: { parts: [{ text: system }] }, contents, generationConfig: { temperature: runtime.temperature, maxOutputTokens: runtime.max_output_tokens, ...(modelOptions(m).generationConfig || {}) } }) });
    if (!r.ok) throw new Error(`${p.slug}/${m.model} HTTP ${r.status}: ${(await r.text()).slice(0, 500)}`);
    const reader = r.body?.getReader(); if (!reader) throw new Error(`${p.slug}/${m.model}: missing stream body`);
    const decoder = new TextDecoder(); let buffer = "";
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      buffer += decoder.decode(value, { stream: true }); const lines = buffer.split("\n"); buffer = lines.pop() || "";
      for (const line of lines) { const s = line.trim(); if (!s.startsWith("data:")) continue; try { const j = JSON.parse(s.slice(5).trim()); for (const part of j.candidates?.[0]?.content?.parts || []) if (typeof part.text === "string" && part.text) yield part.text; } catch {} }
    }
  } finally { clearTimeout(timer); }
}

function messagesFor(history: any[], userMessage: string, system: string) { return [{ role: "system", content: system }, ...history.map((m: any) => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content || "" })), { role: "user", content: userMessage }]; }
async function getConversation(userId: string, id: string) { const { data, error } = await admin.from("conversations").select("*").eq("id", id).eq("user_id", userId).maybeSingle(); if (error) throw error; if (!data) throw new Error("Conversation not found"); return data; }
async function getHistory(userId: string, id: string, limit: number) { const { data, error } = await admin.from("messages").select("id,role,content,attachments,created_at").eq("user_id", userId).eq("conversation_id", id).order("created_at", { ascending: true }).limit(limit); if (error) throw error; return data || []; }
async function userContext(userId: string, max: number) { const [mem,pref] = await Promise.all([admin.from("user_memory").select("id,key,value,category,enabled").eq("user_id",userId).eq("enabled",true).order("updated_at",{ascending:false}).limit(max),admin.from("user_preferences").select("*").eq("user_id",userId).maybeSingle()]); if(mem.error)throw mem.error;if(pref.error)throw pref.error;return {memories:mem.data||[],prefs:pref.data||null}; }
function contextBlock(c:any, web:any[]) { const out:string[]=[]; if(c.prefs){const p=[c.prefs.custom_instructions&&`Custom instructions: ${c.prefs.custom_instructions}`,c.prefs.communication_style&&`Communication style: ${c.prefs.communication_style}`,c.prefs.preferred_name&&`Preferred name: ${c.prefs.preferred_name}`,c.prefs.occupation&&`Occupation: ${c.prefs.occupation}`].filter(Boolean).join("\n");if(p)out.push(`[USER PERSONALIZATION]\n${p}`);} if(c.memories.length)out.push(`[PERSISTENT MEMORY]\n${c.memories.map((m:any)=>`- ${m.key||m.category||"memory"}: ${m.value}`).join("\n")}`); if(web.length)out.push(`[LIVE WEB EVIDENCE]\n${web.map((s:any,i:number)=>`[${i+1}] ${s.title}\n${s.content.slice(0,5000)}\nURL: ${s.url}`).join("\n\n")}`);return out.join("\n\n"); }

async function attachmentContext(userId:string, attachments:any[]){const out:string[]=[];for(const a of Array.isArray(attachments)?attachments.slice(0,5):[]){const id=clean(a.remoteId);if(!id)continue;const {data}=await admin.from("attachments").select("id,name,type,url").eq("id",id).eq("user_id",userId).maybeSingle();if(!data?.url)continue;try{const f=await admin.storage.from("trawa-attachments").download(data.url);if(f.error||!f.data)continue;const mime=f.data.type||"application/octet-stream";if(mime.startsWith("text/")||/json|csv|xml|javascript|typescript|markdown/.test(mime)||/\.(txt|md|csv|json|xml|kt|java|py|js|ts|html|css)$/i.test(data.name||"")){out.push(`[Attached file: ${data.name}]\n${(await f.data.text()).slice(0,30000)}`)}else out.push(`[Attached file available: ${data.name}, ${mime}]`)}catch{}}return out.join("\n\n");}

async function streamChat(req:Request,user:any){
  const body=await req.json(); const conversationId=clean(body.conversationId); const message=clean(body.message); if(!conversationId||!message)return json({error:"conversationId and message are required"},400);
  const cfg=await loadAiConfig(); const conversation=await getConversation(user.id,conversationId); const temporary=conversation.is_temporary===true;
  const history=await getHistory(user.id,conversationId,Number(cfg.runtime.max_history||60)); const context=temporary?{memories:[],prefs:null}:await userContext(user.id,Number(cfg.runtime.max_memories||40));
  const webEnabled=cfg.runtime.web_search_enabled && cfg.flags.web_search?.enabled !== false; const web=webEnabled&&needsWeb(message)?await searchWeb(message):[];
  const attachEnabled=cfg.runtime.attachment_context_enabled && cfg.flags.attachment_context?.enabled !== false; const attach=attachEnabled?await attachmentContext(user.id,body.attachments||[]):"";
  const system=`${cfg.prompt}\n${contextBlock(context,web)}`; const effective=attach?`${message}\n\n${attach}`:message; const msgs=messagesFor(history.slice(-Number(cfg.runtime.max_history||60)),effective,system);
  if(!temporary){const row={id:crypto.randomUUID(),user_id:user.id,conversation_id:conversationId,role:"user",content:message,attachments:body.attachments||[],metadata:{clientMessageId:body.clientMessageId||null}};const ins=await admin.from("messages").insert(row);if(ins.error)throw ins.error;const ids=Array.isArray(body.attachments)?body.attachments.map((a:any)=>clean(a.remoteId)).filter(Boolean):[];if(ids.length)await admin.from("attachments").update({conversation_id:conversationId,message_id:row.id}).eq("user_id",user.id).in("id",ids);}
  const enc=new TextEncoder();const stream=new ReadableStream({async start(controller){const emit=(d:any)=>controller.enqueue(enc.encode(`data: ${JSON.stringify(d)}\n\n`));emit({type:"status",phase:"thinking"});let final="",used:any=null,last="";try{for(const m of cfg.models){const p=providerFor(m,cfg.providers);if(!p||!p.enabled)continue;try{let candidate="";const it=p.protocol==="gemini"?geminiStream(p,m,msgs,cfg.runtime):openAiStream(p,m,msgs,cfg.runtime);for await(const d of it)candidate+=d;if(!candidate.trim())throw new Error("Empty provider response");final=candidate;used={provider:p.slug,model:m.model};break}catch(e){last=e instanceof Error?e.message:String(e);}}if(!final)throw new Error(last||"All configured AI models are unavailable");if(!temporary){const ins=await admin.from("messages").insert({id:crypto.randomUUID(),user_id:user.id,conversation_id:conversationId,role:"assistant",content:final,citations:web.map((x:any)=>({title:x.title,url:x.url})),metadata:used});if(ins.error)throw ins.error;await admin.from("conversations").update({updated_at:new Date().toISOString(),title:conversation.title==="New Conversation"?message.slice(0,60):conversation.title}).eq("id",conversationId).eq("user_id",user.id);}await admin.from("agent_runs").insert({id:crypto.randomUUID(),user_id:user.id,conversation_id:conversationId,task_type:web.length?"web_chat":"chat",provider_used:used.provider,model_used:used.model,status:"completed"});const chunks=final.match(/.{1,24}(?:\s+|$)/gs)||[final];for(const c of chunks)emit({delta:c});emit({content:final,provider:used.provider,model:used.model,citations:web.map((x:any)=>({title:x.title,url:x.url}))});emit({done:true});}catch(e){emit({error:e instanceof Error?e.message:"AI service unavailable"});}finally{controller.close();}}});return new Response(stream,{headers:{...CORS,"Content-Type":"text/event-stream; charset=utf-8","Connection":"keep-alive","X-Accel-Buffering":"no"}});
}

async function isAdmin(userId:string){const {data}=await admin.from("ai_admins").select("user_id").eq("user_id",userId).eq("enabled",true).maybeSingle();return Boolean(data);}
async function adminConfig(req:Request,user:any){if(!(await isAdmin(user.id)))return json({error:"Admin access required"},403);if(req.method==="GET"){const c=await loadAiConfig();return json({runtime:c.runtime,prompt:c.prompt,providers:c.providers.map((p:any)=>({...p,secret_configured:Boolean(providerKey(p))})),models:c.models,routes:(await admin.from("ai_routes").select("*")).data||[],flags:c.flags});}const b=await req.json();if(req.method==="PUT"){if(b.runtime)await admin.from("ai_runtime_config").update({...b.runtime,version:(Number(b.runtime.version)||1)+1,updated_at:new Date().toISOString()}).eq("id",true);if(typeof b.prompt==="string")await admin.from("ai_prompts").update({content:b.prompt,version:(Number(b.promptVersion)||1)+1,updated_at:new Date().toISOString()}).eq("prompt_key","base_system");if(Array.isArray(b.providers))for(const p of b.providers){await admin.from("ai_providers").upsert({id:p.id||undefined,slug:p.slug,display_name:p.display_name,protocol:p.protocol,endpoint:p.endpoint,secret_env:p.secret_env,enabled:p.enabled!==false,priority:p.priority??100,timeout_ms:p.timeout_ms??90000,config:p.config||{},updated_at:new Date().toISOString()},{onConflict:"slug"});}if(Array.isArray(b.models))for(const m of b.models){await admin.from("ai_models").upsert({id:m.id||undefined,provider_id:m.provider_id,model:m.model,display_name:m.display_name||m.model,enabled:m.enabled!==false,priority:m.priority??100,capabilities:m.capabilities||{},options:m.options||{},updated_at:new Date().toISOString()},{onConflict:"provider_id,model"});}if(b.route?.model_chain)await admin.from("ai_routes").upsert({route_key:"default",enabled:b.route.enabled!==false,priority:b.route.priority??100,model_chain:b.route.model_chain,rules:b.route.rules||{},updated_at:new Date().toISOString()},{onConflict:"route_key"});if(b.flags&&typeof b.flags==="object")for(const [k,v] of Object.entries(b.flags as any))await admin.from("ai_feature_flags").upsert({flag_key:k,enabled:Boolean((v as any).enabled),config:(v as any).config||{},updated_at:new Date().toISOString()},{onConflict:"flag_key"});return json({ok:true,config:await loadAiConfig()});}return json({error:"Method not allowed"},405);}

async function handle(req:Request){if(req.method==="OPTIONS")return new Response(null,{status:204,headers:CORS});const path=new URL(req.url).pathname.replace(/\/+$/)||"/";
  if(req.method==="GET"&&(path.endsWith("/health")||path.endsWith("/trawa-3ar-api")||path==="/health")){const c=await loadAiConfig();return json({status:"healthy",identity:"3AR V1 Pro",backend:"Supabase Edge",controlPlane:"database",configVersion:c.runtime.version,models:c.models.map((m:any)=>({provider:providerFor(m,c.providers)?.slug,model:m.model})),webSearch:Boolean(Deno.env.get("TAVILY_API_KEY"))&&c.runtime.web_search_enabled,timestamp:new Date().toISOString()});}
  if(path.endsWith("/v1/auth/send-otp")&&req.method==="POST"){const b=await req.json();const email=clean(b.email).toLowerCase();if(!email)return json({error:"Email is required"},400);const client=await publicAuthClient();const {error}=await client.auth.signInWithOtp({email,options:{shouldCreateUser:true}});if(error)return json({error:error.message},400);return json({ok:true,requiresOtp:true});}
  if(path.endsWith("/v1/auth/verify-otp")&&req.method==="POST"){const b=await req.json();const email=clean(b.email).toLowerCase();const token=clean(b.token);if(!email||!/^[0-9]{6}$/.test(token))return json({error:"Email and 6-digit code are required"},400);const client=await publicAuthClient();const {data,error}=await client.auth.verifyOtp({email,token,type:"email"});if(error||!data.session||!data.user)return json({error:error?.message||"Invalid or expired code"},401);const name=clean(b.name);if(name)await admin.auth.admin.updateUserById(data.user.id,{user_metadata:{...(data.user.user_metadata||{}),full_name:name}});const fresh=(await admin.auth.getUser(data.session.access_token)).data.user||data.user;return json({token:data.session.access_token,refreshToken:data.session.refresh_token,expiresAt:Date.now()+data.session.expires_in*1000,user:userPayload(fresh)});}
  if(path.endsWith("/v1/auth/login")&&req.method==="POST"){const b=await req.json();const client=await publicAuthClient();const {data,error}=await client.auth.signInWithPassword({email:clean(b.email),password:clean(b.password)});if(error||!data.session||!data.user)return json({error:error?.message||"Invalid credentials"},401);return json({token:data.session.access_token,refreshToken:data.session.refresh_token,expiresAt:Date.now()+data.session.expires_in*1000,user:userPayload(data.user)});}
  if(path.endsWith("/v1/auth/refresh")&&req.method==="POST"){const b=await req.json();const client=await publicAuthClient();const {data,error}=await client.auth.refreshSession({refresh_token:clean(b.refreshToken)});if(error||!data.session||!data.user)return json({error:error?.message||"Session refresh failed"},401);return json({token:data.session.access_token,refreshToken:data.session.refresh_token,expiresAt:Date.now()+data.session.expires_in*1000,user:userPayload(data.user)});}
  if(path.endsWith("/v1/auth/forgot-password")&&req.method==="POST"){const b=await req.json();const client=await publicAuthClient();const {error}=await client.auth.resetPasswordForEmail(clean(b.email),{redirectTo:"https://trawa.ai/reset-password"});if(error)return json({error:error.message},400);return json({ok:true});}
  try { const {user}=await requireUser(req);
    if(path.endsWith("/v1/admin/ai/config"))return await adminConfig(req,user);
    if(path.endsWith("/v1/chat")&&req.method==="POST")return await streamChat(req,user);
    if(path.endsWith("/v1/conversations")&&req.method==="GET"){const {data,error}=await admin.from("conversations").select("id,title,is_temporary,created_at,updated_at").eq("user_id",user.id).order("updated_at",{ascending:false}).limit(100);return error?json({error:error.message},500):json(data||[]);}
    if(path.endsWith("/v1/conversations")&&req.method==="POST"){const b=await req.json();const row={id:crypto.randomUUID(),user_id:user.id,title:clean(b.title)||"New Chat",is_temporary:Boolean(b.isTemporary),metadata:{}};const {data,error}=await admin.from("conversations").insert(row).select().single();return error?json({error:error.message},400):json(data);}
    const cm=path.match(/\/v1\/conversations\/([^/]+)$/);if(cm&&req.method==="DELETE"){const {error}=await admin.from("conversations").delete().eq("id",cm[1]).eq("user_id",user.id);return error?json({error:error.message},500):json({ok:true});}
    const mm=path.match(/\/v1\/conversations\/([^/]+)\/messages$/);if(mm&&req.method==="GET"){await getConversation(user.id,mm[1]);const {data,error}=await admin.from("messages").select("id,role,content,attachments,created_at").eq("conversation_id",mm[1]).eq("user_id",user.id).order("created_at",{ascending:true}).limit(200);return error?json({error:error.message},500):json(data||[]);}
    if(path.endsWith("/v1/profile/personalization")&&req.method==="GET"){const {data,error}=await admin.from("user_preferences").select("*").eq("user_id",user.id).maybeSingle();return error?json({error:error.message},500):json({customInstructions:data?.custom_instructions||"",preferredLanguage:data?.preferred_language||"Auto (Default)",responseTone:data?.communication_style||"Balanced & Precise"});}
    if(path.endsWith("/v1/profile/personalization")&&req.method==="PUT"){const b=await req.json();const {error}=await admin.from("user_preferences").upsert({user_id:user.id,custom_instructions:clean(b.customInstructions),preferred_language:clean(b.preferredLanguage)||"Auto (Default)",communication_style:clean(b.responseTone)||"Balanced & Precise",preferred_name:clean(b.preferredName),occupation:clean(b.occupation),updated_at:new Date().toISOString()},{onConflict:"user_id"});return error?json({error:error.message},400):json({ok:true});}
    if(path.endsWith("/v1/profile/memories")&&req.method==="GET"){const {data,error}=await admin.from("user_memory").select("id,value,category,created_at").eq("user_id",user.id).eq("enabled",true).order("updated_at",{ascending:false});return error?json({error:error.message},500):json((data||[]).map((m:any)=>({id:m.id,content:m.value,category:m.category,createdAt:new Date(m.created_at).getTime()})));}
    if(path.endsWith("/v1/profile/memories")&&req.method==="POST"){const b=await req.json();const value=clean(b.content);if(!value)return json({error:"Memory content is required"},400);const {data,error}=await admin.from("user_memory").insert({id:crypto.randomUUID(),user_id:user.id,key:"user_memory",value,category:clean(b.category)||"general",enabled:true}).select().single();return error?json({error:error.message},400):json({id:data.id,content:data.value,category:data.category,createdAt:new Date(data.created_at).getTime()});}
    const mem=path.match(/\/v1\/profile\/memories\/([^/]+)$/);if(mem&&req.method==="DELETE"){const {error}=await admin.from("user_memory").delete().eq("id",mem[1]).eq("user_id",user.id);return error?json({error:error.message},500):json({ok:true});}
    if(path.endsWith("/v1/attachments/upload")&&req.method==="POST"){const form=await req.formData();const file=form.get("file");if(!(file instanceof File))return json({error:"file is required"},400);if(file.size>25*1024*1024)return json({error:"Attachment exceeds 25 MB"},413);const id=crypto.randomUUID();const bucket="trawa-attachments";const name=`${user.id}/${id}/${file.name.replace(/[^a-zA-Z0-9._-]/g,"_")}`;const b=await admin.storage.getBucket(bucket);if(b.error){const c=await admin.storage.createBucket(bucket,{public:false,fileSizeLimit:"25MB"});if(c.error&&!String(c.error.message).toLowerCase().includes("already exists"))return json({error:c.error.message},500);}const up=await admin.storage.from(bucket).upload(name,new Uint8Array(await file.arrayBuffer()),{contentType:file.type||"application/octet-stream",upsert:false});if(up.error)return json({error:up.error.message},500);const ins=await admin.from("attachments").insert({id,user_id:user.id,conversation_id:null,message_id:null,name:file.name,type:clean(form.get("type"))||"FILE",url:name,size:String(file.size)});if(ins.error)return json({error:ins.error.message},500);return json({id,name:file.name,type:clean(form.get("type"))||"FILE",mimeType:file.type,size:file.size});}
    return json({error:"Endpoint not found"},404);
  } catch(e) { if(e instanceof Response)return e; console.error(e); return json({error:e instanceof Error?e.message:"Internal server error"},500); }
}
serve((req)=>handle(req));
