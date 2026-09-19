-- TRAWA: server-controlled AI control plane
-- The mobile APK is a client. AI behavior, routing, prompts and provider/model configuration live here.

create table if not exists public.ai_runtime_config (
  id boolean primary key default true check (id = true),
  version bigint not null default 1,
  max_history integer not null default 60 check (max_history between 1 and 200),
  max_memories integer not null default 40 check (max_memories between 0 and 200),
  web_search_enabled boolean not null default true,
  attachment_context_enabled boolean not null default true,
  temperature numeric not null default 0.65 check (temperature >= 0 and temperature <= 2),
  max_output_tokens integer not null default 8192 check (max_output_tokens between 1 and 32768),
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_prompts (
  prompt_key text primary key,
  content text not null,
  enabled boolean not null default true,
  version bigint not null default 1,
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_providers (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  display_name text not null,
  protocol text not null check (protocol in ('gemini','openai_compatible')),
  endpoint text not null,
  secret_env text not null,
  enabled boolean not null default true,
  priority integer not null default 100,
  timeout_ms integer not null default 90000 check (timeout_ms between 5000 and 180000),
  config jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_models (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.ai_providers(id) on delete cascade,
  model text not null,
  display_name text,
  enabled boolean not null default true,
  priority integer not null default 100,
  capabilities jsonb not null default '{}'::jsonb,
  options jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(provider_id, model)
);

create table if not exists public.ai_routes (
  route_key text primary key,
  enabled boolean not null default true,
  priority integer not null default 100,
  model_chain jsonb not null default '[]'::jsonb,
  rules jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_feature_flags (
  flag_key text primary key,
  enabled boolean not null default true,
  config jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.ai_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists idx_ai_models_provider_priority on public.ai_models(provider_id, enabled, priority);
create index if not exists idx_ai_providers_priority on public.ai_providers(enabled, priority);
create index if not exists idx_ai_routes_priority on public.ai_routes(enabled, priority);

-- Control-plane tables are never writable/readable by ordinary clients.
alter table public.ai_runtime_config enable row level security;
alter table public.ai_prompts enable row level security;
alter table public.ai_providers enable row level security;
alter table public.ai_models enable row level security;
alter table public.ai_routes enable row level security;
alter table public.ai_feature_flags enable row level security;
alter table public.ai_admins enable row level security;

drop policy if exists ai_runtime_no_client_access on public.ai_runtime_config;
drop policy if exists ai_prompts_no_client_access on public.ai_prompts;
drop policy if exists ai_providers_no_client_access on public.ai_providers;
drop policy if exists ai_models_no_client_access on public.ai_models;
drop policy if exists ai_routes_no_client_access on public.ai_routes;
drop policy if exists ai_flags_no_client_access on public.ai_feature_flags;
drop policy if exists ai_admins_no_client_access on public.ai_admins;

-- Intentionally no authenticated/anon policies. Service-role/server access only.

insert into public.ai_runtime_config (id) values (true)
on conflict (id) do nothing;

insert into public.ai_prompts(prompt_key, content)
values ('base_system', $$You are 3AR V1 Pro, the unified AI inside TRAWA.
Act like a normal, capable assistant. Never introduce yourself as a model/provider/version unless the user explicitly asks.
Match the user's language and natural register. For Egyptian Arabic, reply naturally in Egyptian Arabic. If the user mixes Arabic and English, preserve the intended wording and do not scramble RTL/LTR text.
Be concise for simple questions and detailed only when the task requires it. Do not pad answers with generic greetings, self-descriptions, or repeated conclusions.
Use markdown only when it materially improves readability. Do not add decorative bold, excessive bullets, or unnecessary headings.
Never claim to have searched, opened, generated, uploaded, remembered, or completed an action unless it actually happened.
User personalization and persistent memory are authoritative context for this account; use them naturally without mentioning internal storage unless relevant.
$$)
on conflict (prompt_key) do nothing;

insert into public.ai_feature_flags(flag_key, enabled, config)
values
  ('web_search', true, '{}'::jsonb),
  ('attachment_context', true, '{}'::jsonb)
on conflict (flag_key) do nothing;

-- Seed the current provider pool. Keys remain server secrets; the database stores only their secret variable names.
insert into public.ai_providers(slug, display_name, protocol, endpoint, secret_env, priority, config)
values
  ('gemini','Google Gemini','gemini','https://generativelanguage.googleapis.com','GEMINI_API_KEY',10,'{}'),
  ('groq','Groq','openai_compatible','https://api.groq.com/openai/v1/chat/completions','GROQ_API_KEY',20,'{}'),
  ('mistral','Mistral','openai_compatible','https://api.mistral.ai/v1/chat/completions','MISTRAL_API_KEY',30,'{}'),
  ('openrouter','OpenRouter','openai_compatible','https://openrouter.ai/api/v1/chat/completions','OPENROUTER_API_KEY',40,'{"http_referer":"https://trawa.ai","x_title":"TRAWA"}')
on conflict (slug) do update set
  display_name=excluded.display_name,
  protocol=excluded.protocol,
  endpoint=excluded.endpoint,
  secret_env=excluded.secret_env;

insert into public.ai_models(provider_id, model, display_name, priority, capabilities, options)
select p.id, x.model, x.display_name, x.priority, x.capabilities::jsonb, x.options::jsonb
from public.ai_providers p
join (values
  ('gemini','gemini-3.8-flash','Gemini Flash',10,'{"chat":true,"web":true}','{}'),
  ('gemini','gemini-2.5-flash','Gemini 2.5 Flash',20,'{"chat":true,"web":true}','{}'),
  ('groq','openai/gpt-oss-120b','GPT OSS 120B',10,'{"chat":true,"reasoning":true}','{"reasoning_effort":"medium"}'),
  ('groq','openai/gpt-oss-20b','GPT OSS 20B',20,'{"chat":true,"reasoning":true}','{"reasoning_effort":"medium"}'),
  ('mistral','mistral-small-2603','Mistral Small',10,'{"chat":true}','{}'),
  ('openrouter','openai/gpt-oss-120b','OpenRouter GPT OSS 120B',10,'{"chat":true,"reasoning":true}','{}')
) as x(slug,model,display_name,priority,capabilities,options) on x.slug=p.slug
on conflict (provider_id, model) do update set
  display_name=excluded.display_name,
  priority=excluded.priority,
  capabilities=excluded.capabilities,
  options=excluded.options;

insert into public.ai_routes(route_key, enabled, priority, model_chain, rules)
select 'default', true, 10,
  coalesce(jsonb_agg(to_jsonb(m.id) order by p.priority, m.priority), '[]'::jsonb),
  '{"web_search":true}'::jsonb
from public.ai_models m join public.ai_providers p on p.id=m.provider_id
where m.enabled and p.enabled
on conflict (route_key) do update set model_chain=excluded.model_chain, rules=excluded.rules;

-- The database is now the source of truth for AI behavior. Secrets remain outside it.
