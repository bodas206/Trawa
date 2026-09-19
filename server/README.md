# TRAWA server

The Android app is server-first. Provider credentials never belong in the APK.

## Supabase Edge Function

Function: `trawa-3ar-api`

Source: `supabase/functions/trawa-3ar-api/index.ts`

The function exposes:
- `GET /health`
- `POST /v1/auth/login`
- `POST /v1/auth/signup`
- `POST /v1/auth/forgot-password`
- `GET/POST /v1/conversations`
- `GET/DELETE /v1/conversations/:id`
- `GET /v1/conversations/:id/messages`
- `POST /v1/chat` (SSE)
- `POST /v1/attachments/upload`
- `GET/PUT /v1/profile/personalization`
- `GET/POST /v1/profile/memories`
- `DELETE /v1/profile/memories/:id`

## Server secrets

Configure these in Supabase Edge Function secrets, never in Android:
- `GEMINI_API_KEY`
- `GROQ_API_KEY`
- `MISTRAL_API_KEY`
- `OPENROUTER_API_KEY`
- `TAVILY_API_KEY`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` (normally supplied by Supabase)
- `SUPABASE_SERVICE_ROLE_KEY` (server-only)
- optional model overrides: `TRAWA_GEMINI_MODEL`, `TRAWA_GROQ_MODEL`, `TRAWA_MISTRAL_MODEL`, `TRAWA_OPENROUTER_MODEL`

Current defaults use production model IDs and can be changed remotely with the model override secrets.

## Security

The function keeps `verify_jwt=false` because login/signup/forgot-password are public endpoints, but every protected API route performs explicit Supabase JWT validation before accessing account data. User-owned tables are RLS-protected for `authenticated` users.

## Server-controlled AI control plane

TRAWA now keeps AI behavior in Supabase instead of compiling provider/model choices into Android.

Control-plane tables:
- `ai_runtime_config` — history limits, temperature, output limits, web/attachment switches.
- `ai_prompts` — live 3AR V1 Pro system prompt and future prompt versions.
- `ai_providers` — provider endpoint/protocol/priority and the server-side secret variable name.
- `ai_models` — provider models, priority, capabilities and provider options.
- `ai_routes` — ordered model failover chains.
- `ai_feature_flags` — server-side feature switches.
- `ai_admins` — server control-plane administrators.

Provider API keys are **not** stored in Postgres and never go to Android. They remain Edge Function secrets. Supabase documents Edge Function secrets as server-side credentials and notes that they are available without redeploy after being changed. (see Supabase Edge Function secrets documentation)

The API exposes an authenticated admin control endpoint:
`/v1/admin/ai/config`

`GET` returns the effective AI configuration (never secret values). `PUT` updates runtime settings, prompt, providers, models, route chain, and feature flags. The caller must exist in `ai_admins`.

### Adding a provider without an APK

For an OpenAI-compatible provider, add a provider row, add its server secret, add one or more model rows, then put the model IDs into `ai_routes.model_chain`. The Android app does not need to know the provider exists.

For a provider that uses a different protocol, the server needs one adapter implementation once. After that, its models and priority can be changed remotely.

### Changing behavior without an APK

Examples that are server-controlled:
- system prompt / 3AR identity instructions
- model selection
- model priority and failover
- provider enable/disable
- temperature and output limits
- history/memory limits
- web-search switch
- attachment-context switch
- feature flags
- provider-specific request options

Native Android capabilities and UI components remain compiled in the APK. A brand-new native capability still requires an APK update.
