# TRAWA — AI Failover / Server-Controlled Update — 2026-09-19

## Changes actually made

### Production Supabase
- Applied migration `20260919_harden_ai_failover_and_runtime_control`.
- Added/ensured `public.ai_model_health` for persistent model failure/cooldown state.
- Updated default route to an explicit ordered model chain.
- Enabled server-side failover and health-cooldown rules.
- Raised runtime temperature to 0.70.
- Replaced the base system prompt so it explicitly forbids canned greetings/repeated welcome responses and fake capabilities.
- Deployed `trawa-3ar-api` Edge Function version 11 ACTIVE.

### Edge Function behavior
- Before trying a model, the server checks its cooldown state.
- Each model gets one retry for transient failures (timeouts, network errors, 408/409/425/429/5xx, missing stream body).
- A successful model clears its failure state.
- A failed model enters exponential cooldown (30s → 60s → 120s → 240s → 480s, capped at 600s).
- The next eligible model is tried automatically.
- Provider output is buffered before being emitted to the Android client, preventing half-generated responses from being mixed across failover attempts.
- `/health` now reports enabled providers, whether their configured secret exists, configured models, and current runtime version. This is configuration evidence, not proof that every provider credential has successfully completed a live inference call.

### Android UI cleanup
- Removed the unused generated-artifact preview/download control because the current backend does not implement artifact generation/download.
- Removed the unused `GeneratedArtifact`/`ArtifactEvent` client plumbing associated with that dead UI path.
- Existing working controls such as real attachments, temporary chat, regenerate, copy/share, native speech input, and native read-aloud remain.

## Important verification boundary

The provider/model IDs were checked against current official documentation. Gemini 3.8 Flash is currently documented as stable/production; Groq documents GPT-OSS 120B/20B and Qwen 3.8 27B; Mistral documents Mistral Small 4 as `mistral-small-2603`. The actual provider secret values are not exposed by the available Supabase management surface, so live end-to-end inference for every provider cannot honestly be marked VERIFIED solely from database configuration.

## Server-first guarantee

The Android client remains a client of TRAWA API. Provider secrets, model order, runtime settings, prompts, feature flags, and failover policy are server/database controlled. Changes to those items can be deployed server-side without rebuilding the APK, provided the Android client already contains the required capability and endpoint contract.

This does **not** mean a new APK is never needed: adding a new native Android capability or changing an incompatible API contract still requires a client update.
