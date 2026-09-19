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
