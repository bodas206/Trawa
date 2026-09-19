import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.57.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || "";

const KEYS = {
  gemini: Deno.env.get("GEMINI_API_KEY") || "",
  groq: Deno.env.get("GROQ_API_KEY") || "",
  mistral: Deno.env.get("MISTRAL_API_KEY") || "",
  openrouter: Deno.env.get("OPENROUTER_API_KEY") || "",
  tavily: Deno.env.get("TAVILY_API_KEY") || "",
  elevenlabs: Deno.env.get("ELEVENLABS_API_KEY") || "",
  deepgram: Deno.env.get("DEEPGRAM_API_KEY") || "",
};

const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Cache-Control": "no-store",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });

const cleanText = (text: string) => text.replace(/\u0000/g, "").trim();

function getToken(req: Request) {
  const value = req.headers.get("authorization") || "";
  return value.startsWith("Bearer ") ? value.slice(7).trim() : "";
}

async function requireUser(req: Request) {
  const token = getToken(req);
  if (!token) throw new Response(JSON.stringify({ error: "Authentication required" }), { status: 401, headers: { ...CORS, "Content-Type": "application/json" } });
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new Response(JSON.stringify({ error: "Invalid or expired session" }), { status: 401, headers: { ...CORS, "Content-Type": "application/json" } });
  return { user: data.user, token };
}

async function publicAuthClient() {
  if (!SUPABASE_ANON_KEY) throw new Error("Supabase public auth key is not configured");
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: false, autoRefreshToken: false } });
}

function userPayload(user: any) {
  return {
    id: user.id,
    email: user.email || "",
    name: user.user_metadata?.full_name || user.user_metadata?.name || (user.email || "User").split("@")[0],
  };
}

const BASE_SYSTEM = `You are 3AR V1 Pro, the unified AI inside TRAWA.
Act like a normal, capable assistant. Never introduce yourself as a model/provider/version unless the user explicitly asks.
Match the user's language and natural register. For Egyptian Arabic, reply naturally in Egyptian Arabic. If the user mixes Arabic and English, preserve the intended wording and do not scramble RTL/LTR text.
Be concise for simple questions and detailed only when the task requires it. Do not pad answers with generic greetings, self-descriptions, or repeated conclusions.
Use markdown only when it materially improves readability. Do not add decorative bold, excessive bullets, or unnecessary headings.
Never claim to have searched, opened, generated, uploaded, remembered, or completed an action unless it actually happened.
User personalization and persistent memory are authoritative context for this account; use them naturally without mentioning internal storage unless relevant.
`;

function needsWeb(q: string) {
  return /(latest|today|current|right now|recent|news|breaking|price|weather|score|schedule|release|update|النهارده|اليوم|دلوقتي|حاليًا|اخر|آخر|أخبار|خبر|سعر|طقس|ماتش|مباراة|نتيجة|تحديث|إصدار)/i.test(q);
}

async function searchWeb(query: string) {
  if (!KEYS.tavily) return [] as Array<{ title: string; url: string; content: string }>;
  try {
    const r = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ api_key: KEYS.tavily, query, max_results: 5, search_depth: "advanced", include_answer: false }),
    });
    if (!r.ok) return [];
    const d = await r.json();
    return (d.results || []).map((x: any) => ({ title: x.title || "", url: x.url || "", content: x.content || "" })).filter((x: any) => x.url);
  } catch { return []; }
}

function contextBlock(memories: any[], prefs: any, web: any[]) {
  const parts: string[] = [];
  if (prefs) {
    const p = [
      prefs.custom_instructions ? `Custom instructions: ${prefs.custom_instructions}` : "",
      prefs.communication_style ? `Communication style: ${prefs.communication_style}` : "",
      prefs.preferred_name ? `Preferred name: ${prefs.preferred_name}` : "",
      prefs.occupation ? `Occupation: ${prefs.occupation}` : "",
    ].filter(Boolean).join("\n");
    if (p) parts.push(`[USER PERSONALIZATION]\n${p}`);
  }
  if (memories.length) {
    parts.push(`[PERSISTENT MEMORY]\n${memories.slice(0, 40).map((m: any) => `- ${m.key || m.category || "memory"}: ${m.value}`).join("\n")}`);
  }
  if (web.length) {
    parts.push(`[LIVE WEB EVIDENCE]\n${web.map((s: any, i: number) => `[${i + 1}] ${s.title}\n${s.content.slice(0, 5000)}\nURL: ${s.url}`).join("\n\n")}`);
  }
  return parts.join("\n\n");
}

async function attachmentContext(userId: string, attachments: any[]) {
  const chunks: string[] = [];
  for (const a of Array.isArray(attachments) ? attachments.slice(0, 5) : []) {
    const id = String(a.remoteId || "");
    if (!id) continue;
    const { data } = await admin.from("attachments").select("id,name,type,url").eq("id", id).eq("user_id", userId).maybeSingle();
    if (!data?.url) continue;
    try {
      const file = await admin.storage.from("trawa-attachments").download(data.url);
      if (file.error || !file.data) continue;
      const mime = file.data.type || "application/octet-stream";
      if (mime.startsWith("text/") || /json|csv|xml|javascript|typescript|markdown/.test(mime) || /\.(txt|md|csv|json|xml|kt|java|py|js|ts|html|css)$/i.test(data.name || "")) {
        const text = await file.data.text();
        chunks.push(`[Attached file: ${data.name}]\n${text.slice(0, 30000)}`);
      } else {
        chunks.push(`[Attached file available to the assistant: ${data.name}, ${mime}]`);
      }
    } catch {}
  }
  return chunks.join("\n\n");
}

function toProviderMessages(history: any[], userMessage: string, system: string) {
  return [
    { role: "system", content: system },
    ...history.map((m: any) => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content || "" })),
    { role: "user", content: userMessage },
  ];
}

async function* openAiCompatStream(url: string, key: string, model: string, messages: any[], options: Record<string, unknown> = {}) {
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify({ model, messages, stream: true, temperature: 0.65, max_tokens: 8192, ...options }),
  });
  if (!r.ok) throw new Error(`${model} HTTP ${r.status}: ${(await r.text()).slice(0, 500)}`);
  const reader = r.body?.getReader();
  if (!reader) throw new Error(`${model}: missing stream body`);
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      const s = line.trim();
      if (!s.startsWith("data:")) continue;
      const data = s.slice(5).trim();
      if (!data || data === "[DONE]") continue;
      try {
        const d = JSON.parse(data);
        const delta = d.choices?.[0]?.delta?.content;
        if (typeof delta === "string" && delta) yield delta;
      } catch {}
    }
  }
}

async function* geminiStream(key: string, model: string, messages: any[]) {
  const system = messages.find((m) => m.role === "system")?.content || "";
  const contents = messages.filter((m) => m.role !== "system").map((m) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content || "" }] }));
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(key)}`;
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ system_instruction: { parts: [{ text: system }] }, contents, generationConfig: { temperature: 0.65, maxOutputTokens: 8192 } }),
  });
  if (!r.ok) throw new Error(`Gemini ${model} HTTP ${r.status}: ${(await r.text()).slice(0, 500)}`);
  const reader = r.body?.getReader();
  if (!reader) throw new Error("Gemini: missing stream body");
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      const s = line.trim();
      if (!s.startsWith("data:")) continue;
      try {
        const d = JSON.parse(s.slice(5).trim());
        const parts = d.candidates?.[0]?.content?.parts || [];
        for (const p of parts) if (typeof p.text === "string" && p.text) yield p.text;
      } catch {}
    }
  }
}

function providers() {
  const p: Array<{name: string; model: string; run: (messages: any[]) => AsyncGenerator<string>}> = [];
  if (KEYS.gemini) {
    p.push({ name: "gemini", model: Deno.env.get("TRAWA_GEMINI_MODEL") || "gemini-3.8-flash", run: (m) => geminiStream(KEYS.gemini, Deno.env.get("TRAWA_GEMINI_MODEL") || "gemini-3.8-flash", m) });
    p.push({ name: "gemini", model: "gemini-2.5-flash", run: (m) => geminiStream(KEYS.gemini, "gemini-2.5-flash", m) });
  }
  if (KEYS.groq) {
    p.push({ name: "groq", model: Deno.env.get("TRAWA_GROQ_MODEL") || "openai/gpt-oss-120b", run: (m) => openAiCompatStream("https://api.groq.com/openai/v1/chat/completions", KEYS.groq, Deno.env.get("TRAWA_GROQ_MODEL") || "openai/gpt-oss-120b", m, { reasoning_effort: "medium" }) });
    p.push({ name: "groq", model: "openai/gpt-oss-20b", run: (m) => openAiCompatStream("https://api.groq.com/openai/v1/chat/completions", KEYS.groq, "openai/gpt-oss-20b", m, { reasoning_effort: "medium" }) });
  }
  if (KEYS.mistral) {
    p.push({ name: "mistral", model: Deno.env.get("TRAWA_MISTRAL_MODEL") || "mistral-small-2603", run: (m) => openAiCompatStream("https://api.mistral.ai/v1/chat/completions", KEYS.mistral, Deno.env.get("TRAWA_MISTRAL_MODEL") || "mistral-small-2603", m) });
  }
  if (KEYS.openrouter) {
    p.push({ name: "openrouter", model: Deno.env.get("TRAWA_OPENROUTER_MODEL") || "openai/gpt-oss-120b", run: (m) => openAiCompatStream("https://openrouter.ai/api/v1/chat/completions", KEYS.openrouter, Deno.env.get("TRAWA_OPENROUTER_MODEL") || "openai/gpt-oss-120b", m, { http_referer: "https://trawa.ai", x_title: "TRAWA" }) });
  }
  return p;
}

async function getConversation(userId: string, id: string) {
  const { data, error } = await admin.from("conversations").select("*").eq("id", id).eq("user_id", userId).maybeSingle();
  if (error) throw error;
  if (!data) throw new Error("Conversation not found");
  return data;
}

async function getHistory(userId: string, conversationId: string) {
  const { data, error } = await admin.from("messages").select("id,role,content,attachments,created_at").eq("user_id", userId).eq("conversation_id", conversationId).order("created_at", { ascending: true }).limit(80);
  if (error) throw error;
  return data || [];
}

async function buildUserContext(userId: string) {
  const [mem, pref] = await Promise.all([
    admin.from("user_memory").select("id,key,value,category,enabled").eq("user_id", userId).eq("enabled", true).order("updated_at", { ascending: false }).limit(40),
    admin.from("user_preferences").select("*").eq("user_id", userId).maybeSingle(),
  ]);
  if (mem.error) throw mem.error;
  if (pref.error) throw pref.error;
  return { memories: mem.data || [], prefs: pref.data || null };
}

async function streamChat(req: Request, user: any) {
  const body = await req.json();
  const conversationId = String(body.conversationId || "");
  const message = cleanText(String(body.message || ""));
  if (!conversationId || !message) return json({ error: "conversationId and message are required" }, 400);
  const conversation = await getConversation(user.id, conversationId);
  // Server truth wins over any client-provided temporary flag.
  const temporary = conversation.is_temporary === true;

  const history = await getHistory(user.id, conversationId);
  const context = temporary ? { memories: [], prefs: null } : await buildUserContext(user.id);
  const web = needsWeb(message) ? await searchWeb(message) : [];
  const attachmentInfo = await attachmentContext(user.id, body.attachments || []);
  const system = `${BASE_SYSTEM}\n${contextBlock(context.memories, context.prefs, web)}`;
  const effectiveMessage = attachmentInfo ? `${message}\n\n${attachmentInfo}` : message;
  const messages = toProviderMessages(history.slice(-60), effectiveMessage, system);

  if (!temporary) {
    const userRow = {
      id: crypto.randomUUID(), user_id: user.id, conversation_id: conversationId,
      role: "user", content: message, attachments: body.attachments || [], metadata: { clientMessageId: body.clientMessageId || null },
    };
    const ins = await admin.from("messages").insert(userRow);
    if (ins.error) throw ins.error;
    const remoteIds = Array.isArray(body.attachments) ? body.attachments.map((a:any) => String(a.remoteId || "")).filter(Boolean) : [];
    if (remoteIds.length) {
      await admin.from("attachments").update({ conversation_id: conversationId, message_id: userRow.id }).eq("user_id", user.id).in("id", remoteIds);
    }
  }

  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    async start(controller) {
      const emit = (data: any) => controller.enqueue(encoder.encode(`data: ${JSON.stringify(data)}\n\n`));
      emit({ type: "status", phase: "thinking" });
      let final = "";
      let used: any = null;
      let lastError = "";
      try {
        for (const provider of providers()) {
          try {
            let candidate = "";
            for await (const delta of provider.run(messages)) {
              candidate += delta;
            }
            if (!candidate.trim()) throw new Error("Empty provider response");
            // Only expose a completed provider attempt, avoiding duplicate partial streams on mid-stream failover.
            final = candidate;
            used = provider;
            const chunks = candidate.match(/.{1,24}(?:\s+|$)/gs) || [candidate];
            for (const chunk of chunks) emit({ delta: chunk });
            break;
          } catch (e) {
            lastError = e instanceof Error ? e.message : String(e);
          }
        }
        if (!final) throw new Error(lastError || "All AI providers are unavailable");

        if (!temporary) {
          const assistantRow = {
            id: crypto.randomUUID(), user_id: user.id, conversation_id: conversationId,
            role: "assistant", content: final, citations: web.map((x: any) => ({ title: x.title, url: x.url })),
            metadata: { provider: used.name, model: used.model },
          };
          const ins = await admin.from("messages").insert(assistantRow);
          if (ins.error) throw ins.error;
          await admin.from("conversations").update({ updated_at: new Date().toISOString(), title: conversation.title === "New Conversation" ? message.slice(0, 60) : conversation.title }).eq("id", conversationId).eq("user_id", user.id);
        }
        await admin.from("agent_runs").insert({ id: crypto.randomUUID(), user_id: user.id, conversation_id: conversationId, task_type: needsWeb(message) ? "web_chat" : "chat", provider_used: used.name, model_used: used.model, status: "completed" });
        emit({ content: final, provider: used.name, model: used.model, citations: web.map((x: any) => ({ title: x.title, url: x.url })) });
        emit({ done: true });
      } catch (e) {
        emit({ error: e instanceof Error ? e.message : "AI service unavailable" });
      } finally { controller.close(); }
    },
  });
  return new Response(stream, { headers: { ...CORS, "Content-Type": "text/event-stream; charset=utf-8", "Connection": "keep-alive", "X-Accel-Buffering": "no" } });
}

async function handle(req: Request) {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";

  if (req.method === "GET" && (path.endsWith("/health") || path.endsWith("/trawa-3ar-api") || path === "/health")) {
    return json({ status: "healthy", identity: "3AR V1 Pro", backend: "Supabase Edge", version: "3.0", providers: providers().map(p => `${p.name}:${p.model}`), webSearch: Boolean(KEYS.tavily), timestamp: new Date().toISOString() });
  }

  if (path.endsWith("/v1/auth/login") && req.method === "POST") {
    const { email, password } = await req.json();
    const client = await publicAuthClient();
    const { data, error } = await client.auth.signInWithPassword({ email: String(email || "").trim(), password: String(password || "") });
    if (error || !data.session || !data.user) return json({ error: error?.message || "Invalid email or password" }, 401);
    return json({ token: data.session.access_token, refreshToken: data.session.refresh_token, expiresAt: Date.now() + data.session.expires_in * 1000, user: userPayload(data.user) });
  }

  if (path.endsWith("/v1/auth/signup") && req.method === "POST") {
    const { email, password, name } = await req.json();
    const client = await publicAuthClient();
    const { data, error } = await client.auth.signUp({ email: String(email || "").trim(), password: String(password || ""), options: { data: { full_name: String(name || "").trim() } } });
    if (error) return json({ error: error.message }, 400);
    if (!data.user) return json({ error: "Could not create account" }, 400);
    return json({ requiresEmailConfirmation: !data.session, token: data.session?.access_token || null, refreshToken: data.session?.refresh_token || null, expiresAt: data.session ? Date.now() + data.session.expires_in * 1000 : null, user: userPayload(data.user) });
  }

  if (path.endsWith("/v1/auth/refresh") && req.method === "POST") {
    const { refreshToken } = await req.json();
    const client = await publicAuthClient();
    const { data, error } = await client.auth.refreshSession({ refresh_token: String(refreshToken || "") });
    if (error || !data.session || !data.user) return json({ error: error?.message || "Session refresh failed" }, 401);
    return json({
      token: data.session.access_token,
      refreshToken: data.session.refresh_token,
      expiresAt: Date.now() + data.session.expires_in * 1000,
      user: userPayload(data.user)
    });
  }

  if (path.endsWith("/v1/auth/forgot-password") && req.method === "POST") {
    const { email } = await req.json();
    const client = await publicAuthClient();
    const { error } = await client.auth.resetPasswordForEmail(String(email || "").trim(), { redirectTo: "https://trawa.ai/reset-password" });
    if (error) return json({ error: error.message }, 400);
    return json({ ok: true });
  }

  const protectedPrefixes = ["/v1/chat", "/v1/conversations", "/v1/profile", "/v1/attachments", "/v1/stt", "/v1/tts", "/v1/voice-call-turn", "/v1/image-generate"];
  if (protectedPrefixes.some(p => path.endsWith(p) || path.includes(p + "/"))) {
    const { user } = await requireUser(req);

    if (path.endsWith("/v1/chat") && req.method === "POST") return await streamChat(req, user);

    if (path.endsWith("/v1/conversations") && req.method === "GET") {
      const { data, error } = await admin.from("conversations").select("id,title,is_temporary,created_at,updated_at").eq("user_id", user.id).order("updated_at", { ascending: false }).limit(100);
      if (error) return json({ error: error.message }, 500);
      return json(data || []);
    }
    if (path.endsWith("/v1/conversations") && req.method === "POST") {
      const b = await req.json();
      const row = { id: crypto.randomUUID(), user_id: user.id, title: String(b.title || "New Chat").slice(0, 120), is_temporary: Boolean(b.isTemporary), metadata: {} };
      const { data, error } = await admin.from("conversations").insert(row).select().single();
      if (error) return json({ error: error.message }, 400);
      return json(data);
    }
    const convMatch = path.match(/\/v1\/conversations\/([^/]+)$/);
    if (convMatch && req.method === "DELETE") {
      const id = convMatch[1];
      const { error } = await admin.from("conversations").delete().eq("id", id).eq("user_id", user.id);
      if (error) return json({ error: error.message }, 500);
      return json({ ok: true });
    }
    const msgMatch = path.match(/\/v1\/conversations\/([^/]+)\/messages$/);
    if (msgMatch && req.method === "GET") {
      const id = msgMatch[1];
      await getConversation(user.id, id);
      const { data, error } = await admin.from("messages").select("id,role,content,attachments,created_at").eq("conversation_id", id).eq("user_id", user.id).order("created_at", { ascending: true }).limit(200);
      if (error) return json({ error: error.message }, 500);
      return json(data || []);
    }

    if (path.endsWith("/v1/profile/personalization") && req.method === "GET") {
      const { data, error } = await admin.from("user_preferences").select("*").eq("user_id", user.id).maybeSingle();
      if (error) return json({ error: error.message }, 500);
      return json({
        customInstructions: data?.custom_instructions || "",
        preferredLanguage: data?.preferred_language || "Auto (Default)",
        responseTone: data?.communication_style || "Balanced & Precise"
      });
    }
    if (path.endsWith("/v1/profile/personalization") && req.method === "PUT") {
      const b = await req.json();
      const row = {
        user_id: user.id,
        custom_instructions: String(b.customInstructions || ""),
        preferred_language: String(b.preferredLanguage || "Auto (Default)"),
        communication_style: String(b.responseTone || "Balanced & Precise"),
        preferred_name: String(b.preferredName || ""),
        occupation: String(b.occupation || ""),
        updated_at: new Date().toISOString()
      };
      const { error } = await admin.from("user_preferences").upsert(row, { onConflict: "user_id" });
      if (error) return json({ error: error.message }, 400);
      return json({ ok: true });
    }
    if (path.endsWith("/v1/attachments/upload") && req.method === "POST") {
      const form = await req.formData();
      const file = form.get("file");
      const type = String(form.get("type") || "FILE");
      if (!(file instanceof File)) return json({ error: "file is required" }, 400);
      if (file.size > 25 * 1024 * 1024) return json({ error: "Attachment exceeds 25 MB" }, 413);
      const attachmentId = crypto.randomUUID();
      const bucket = "trawa-attachments";
      const pathName = `${user.id}/${attachmentId}/${file.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
      const bucketResult = await admin.storage.getBucket(bucket);
      if (bucketResult.error) {
        const created = await admin.storage.createBucket(bucket, { public: false, fileSizeLimit: "25MB" });
        if (created.error && !String(created.error.message).toLowerCase().includes("already exists")) return json({ error: created.error.message }, 500);
      }
      const bytes = new Uint8Array(await file.arrayBuffer());
      const uploaded = await admin.storage.from(bucket).upload(pathName, bytes, { contentType: file.type || "application/octet-stream", upsert: false });
      if (uploaded.error) return json({ error: uploaded.error.message }, 500);
      const row = { id: attachmentId, user_id: user.id, conversation_id: null, message_id: null, name: file.name, type, url: pathName, size: String(file.size) };
      const ins = await admin.from("attachments").insert(row);
      if (ins.error) return json({ error: ins.error.message }, 500);
      return json({ id: attachmentId, name: file.name, type, mimeType: file.type, size: file.size });
    }

    if (path.endsWith("/v1/profile/memories") && req.method === "GET") {
      const { data, error } = await admin.from("user_memory").select("id,value,category,created_at").eq("user_id", user.id).eq("enabled", true).order("updated_at", { ascending: false });
      if (error) return json({ error: error.message }, 500);
      return json((data || []).map((m: any) => ({ id: m.id, content: m.value, category: m.category, createdAt: new Date(m.created_at).getTime() })));
    }
    if (path.endsWith("/v1/profile/memories") && req.method === "POST") {
      const b = await req.json();
      const value = cleanText(String(b.content || ""));
      if (!value) return json({ error: "Memory content is required" }, 400);
      const row = { id: crypto.randomUUID(), user_id: user.id, key: "user_memory", value, category: String(b.category || "general"), enabled: true };
      const { data, error } = await admin.from("user_memory").insert(row).select().single();
      if (error) return json({ error: error.message }, 400);
      return json({ id: data.id, content: data.value, category: data.category, createdAt: new Date(data.created_at).getTime() });
    }
    const memMatch = path.match(/\/v1\/profile\/memories\/([^/]+)$/);
    if (memMatch && req.method === "DELETE") {
      const { error } = await admin.from("user_memory").delete().eq("id", memMatch[1]).eq("user_id", user.id);
      if (error) return json({ error: error.message }, 500);
      return json({ ok: true });
    }

    return json({ error: "Endpoint not found" }, 404);
  }

  return json({ error: "Endpoint not found" }, 404);
}

serve((req) => handle(req).catch((err) => {
  if (err instanceof Response) return err;
  console.error("TRAWA API error", err);
  return json({ error: err instanceof Error ? err.message : "Internal server error" }, 500);
}));
