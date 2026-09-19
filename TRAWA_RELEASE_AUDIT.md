
## 2026-09-19 server-control-plane pass

### VERIFIED
- Supabase production project was cleaned of existing account/chat data before the new account bootstrap: `auth.users`, `profiles`, `conversations`, `messages`, `attachments`, `user_memory`, `user_preferences`, and `agent_runs` are now zero rows.
- A server-side AI control plane was added with `ai_runtime_config`, `ai_prompts`, `ai_providers`, `ai_models`, `ai_routes`, `ai_feature_flags`, and `ai_admins`.
- AI control-plane tables are RLS-protected from `anon` and `authenticated` clients; only the server/service role can read or mutate them. Security advisor is currently clean (`0` lints).
- The `vector` extension was moved out of `public` into `extensions`; the prior `extension_in_public` security lint is cleared.
- `trawa-3ar-api` is deployed and ACTIVE at Edge Function version 8.
- Provider API keys remain server-side Edge Function secrets. The database stores only the secret variable name, never the key value.
- The API now reads the active system prompt, runtime limits, provider pool, model pool, route chain, and feature flags from Supabase at request time.
- OpenAI-compatible providers are data-driven; changing model IDs, priority, enable/disable state, request options, and failover order does not require an APK.
- Gemini uses a server adapter; additional providers with a different protocol still require a server adapter once, after which their model/configuration can be remote-controlled.
- `/v1/admin/ai/config` exists for authenticated users present in `ai_admins`; it returns configuration without secret values and supports server-side updates.
- `/v1/auth/send-otp` and `/v1/auth/verify-otp` were added for six-digit email OTP authentication.
- Android auth code was updated to use the OTP flow instead of requiring a password in the primary UI.

### NOT VERIFIED / BLOCKED
- End-to-end provider inference from this environment was not verified because external DNS/network access is unavailable in the execution container.
- Actual six-digit email delivery depends on the Supabase Auth email template being configured to render `{{ .Token }}`; the official Supabase documentation states that email OTP uses a six-digit code and that the email template must use the token variable. This dashboard setting was not exposed through the available project-management tool surface.
- Android APK build remains BLOCKED because the repository still lacks `gradle-wrapper.jar` and no system Gradle executable is installed in the execution environment.
- Real device verification of OTP, chat streaming, camera, MediaProjection, native voice, and other Android capabilities remains NOT VERIFIED.
