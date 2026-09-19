create table if not exists public.ai_model_health (
  model_id uuid primary key references public.ai_models(id) on delete cascade,
  consecutive_failures integer not null default 0,
  cooldown_until timestamptz,
  last_error text,
  last_failure_at timestamptz,
  last_success_at timestamptz,
  updated_at timestamptz not null default now()
);
alter table public.ai_model_health enable row level security;
drop policy if exists ai_model_health_no_client_access on public.ai_model_health;

update public.ai_models
set enabled=true,
    capabilities = case
      when model='gemini-3.8-flash' then '{"chat":true,"web":true,"coding":true,"reasoning":true,"vision":true}'::jsonb
      when model='gemini-3.7-flash' then '{"chat":true,"web":true,"coding":true,"reasoning":true,"vision":true}'::jsonb
      when model='gemini-2.5-flash' then '{"chat":true,"web":true,"coding":true,"vision":true}'::jsonb
      when model='openai/gpt-oss-120b' and exists(select 1 from public.ai_providers p where p.id=provider_id and p.slug='groq') then '{"chat":true,"reasoning":true,"coding":true,"web":true}'::jsonb
      when model='qwen/qwen3.8-27b' then '{"chat":true,"reasoning":true,"coding":true,"vision":true}'::jsonb
      when model='openai/gpt-oss-20b' then '{"chat":true,"reasoning":true,"coding":true}'::jsonb
      when model='mistral-small-2603' then '{"chat":true,"reasoning":true,"coding":true,"structured_output":true}'::jsonb
      else capabilities end,
    updated_at=now();

with ordered as (
  select m.id,
         case
           when p.slug='gemini' and m.model='gemini-3.8-flash' then 10
           when p.slug='groq' and m.model='openai/gpt-oss-120b' then 20
           when p.slug='gemini' and m.model='gemini-3.7-flash' then 30
           when p.slug='groq' and m.model='qwen/qwen3.8-27b' then 40
           when p.slug='mistral' and m.model='mistral-small-2603' then 50
           when p.slug='groq' and m.model='openai/gpt-oss-20b' then 60
           when p.slug='openrouter' and m.model='openai/gpt-oss-120b' then 70
           when p.slug='gemini' and m.model='gemini-2.5-flash' then 80
           else 999 end as ord
  from public.ai_models m join public.ai_providers p on p.id=m.provider_id
  where m.enabled and p.enabled
)
update public.ai_routes r
set model_chain=(select jsonb_agg(to_jsonb(id) order by ord) from ordered),
    enabled=true, priority=10, updated_at=now(),
    rules='{"web_search":true,"failover":true,"health_cooldown":true}'::jsonb
where r.route_key='default';

update public.ai_runtime_config
set version=version+1, temperature=0.7, max_output_tokens=8192,
    web_search_enabled=true, attachment_context_enabled=true, updated_at=now()
where id=true;

update public.ai_prompts
set content=$prompt$
You are 3AR V1 Pro, the unified AI inside TRAWA.

Answer the user's actual request directly. Do not use a fixed welcome message, canned opening, repeated self-introduction, or generic "How can I help?" unless the user actually greets you or asks for an introduction.

You are one coherent assistant even though the server may use multiple internal providers. Never expose provider/model routing in normal conversation. Never pretend a capability worked when it did not.

Match the user's language and natural register. For Egyptian Arabic, reply naturally in Egyptian Arabic. If the user mixes Arabic and English, preserve the intended meaning and formatting.

Use conversation history, relevant persistent memory, and personalization when available. Do not mention internal memory/storage unless relevant. Do not let stale or irrelevant memory override the current user request.

Be accurate and substantive. For simple questions, answer simply. For difficult tasks, reason carefully and provide the necessary detail. Avoid padding, repetitive conclusions, fake confirmations, or unnecessary headings.

If current/fresh information is needed and live web evidence is supplied, use it and distinguish current evidence from general knowledge. If live search is unavailable, do not claim that you verified current information.

Never claim to have searched, opened, generated, uploaded, remembered, executed, or completed an action unless the server actually did it.

If the requested capability is unavailable, say so plainly. Do not simulate it with fake UI, fake artifacts, placeholder output, or fabricated success.

The current user message always has priority over generic conversational habits.
$prompt$,
version=version+1, updated_at=now()
where prompt_key='base_system';
