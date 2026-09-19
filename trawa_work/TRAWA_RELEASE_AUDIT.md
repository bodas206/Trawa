# TRAWA Release Audit — 2026-09-19

## Verification policy
This audit distinguishes runtime/build evidence from static inspection. No feature is called VERIFIED unless the available environment produced concrete evidence.

## Changes actually made

### Branding / UI
- Replaced the in-app transparent emblem with the user-supplied transparent logo reference.
- Replaced all legacy/malformed raster launcher assets with valid WebP launcher icons generated from the user-supplied original logo.
- Replaced adaptive-icon foreground XML with a valid transparent PNG foreground.
- Added a one-time initial brand reveal: 280 ms, transparent emblem, fade + scale. The preference `initial_brand_reveal_seen` prevents the reveal from repeating on subsequent launches.
- Removed the large branded logo from the session-check screen; session checking now uses a lightweight progress indicator.
- Drawer opening now clears focus and hides the IME for both button-open and drawer-state changes.
- Extended the streaming HTTP read timeout to 180 seconds.
- Improved SSE parsing so backend `{error: ...}` events become explicit UI stream errors instead of silently becoming an empty assistant message.
- Voice-call turn-taking now automatically resumes listening after TTS completes while the call remains open.
- Added refresh-token persistence and a server `/v1/auth/refresh` route; session restoration refreshes near-expiry sessions when a refresh token is available.
- Typography hierarchy was updated toward the requested 900/Black display weights and separate Arabic/Latin font families.

### Backend / database
- Supabase Edge Function `trawa-3ar-api` deployed as version 7.
- Server remains the only place containing provider credentials.
- Server enforces conversation `is_temporary` as the source of truth instead of trusting the client flag.
- Added `preferred_language` to `user_preferences` and wired GET/PUT personalization to it.
- Hardened RLS policies so user-owned data is restricted to authenticated users and `auth.uid()` is statement-cached with `(select auth.uid())`.
- Revoked direct execution of `public.handle_new_user()` from anonymous/authenticated roles.
- Added missing foreign-key indexes for user/message/attachment relationships.

## Static architecture checks
- No Android source references provider API keys through `BuildConfig` after the current source audit.
- No production localhost/LAN/Vite endpoint is configured in the current Android source.
- The Android client talks to the TRAWA API abstraction; provider selection is server-side.
- The server has provider cascade entries for Gemini, Groq, Mistral, and OpenRouter when their server secrets are configured.
- Web search is server-side through Tavily when configured.
- Persistent memory and personalization are server-side context for non-temporary chats.

## Current known limitations / NOT VERIFIED

1. **Android release build: NOT VERIFIED.** The project does not contain `gradle/wrapper/gradle-wrapper.jar`, and this environment has no `gradle` executable. Therefore a real APK/AAB build could not be executed here.
2. **Exact requested Black font binaries: NOT VERIFIED.** The supplied font reference image specifies Outfit Black and Alexandria Black. The project currently contains static binaries whose metadata identifies `Outfit Thin` and `Alexandria Regular`; changing the Compose weight declarations does not prove the binaries are the exact Black cuts. The exact font files are required for final typography verification.
3. **api.trawa.ai live DNS/HTTP reachability: NOT VERIFIED.** The available execution environment cannot resolve external DNS. The Supabase Edge Function deployment itself is verified by the Supabase deployment result, but the custom `api.trawa.ai` routing could not be runtime-probed from this environment.
4. **Live provider response/failover: NOT VERIFIED.** Provider secrets are server-side and were not exposed. The deployed code contains the cascade, but live calls to provider APIs could not be executed from this environment.
5. **Camera / microphone / MediaProjection runtime behavior: NOT VERIFIED.** These require an Android device/emulator with permissions.
6. **Continuous voice-call loop: STATICALLY VERIFIED, RUNTIME NOT VERIFIED.** The ViewModel now chains STT -> chat -> TTS -> STT, but actual microphone/TTS behavior needs device execution.
7. **Real screen recording/share: NOT FIXED.** The current project does not contain a MediaProjection recording pipeline. No fake UI was added for it.
8. **Server-side image generation: NOT FIXED.** The server lists `/v1/image-generate` in its protected route family but does not implement a generation handler, so no fake image-generation button was added.
9. **Server-side Deepgram/ElevenLabs voice endpoints: NOT FIXED.** The server reads those secret names but does not expose complete `/v1/stt` or `/v1/tts` handlers. Current voice functionality uses Android SpeechRecognizer/TextToSpeech instead.
10. **Supabase leaked-password protection: NOT FIXED.** The security advisor still reports this Auth setting as disabled; changing it requires the Supabase Auth configuration surface, which was not available through the current project-management tools.
11. **`vector` extension in `public`: NOT FIXED.** Supabase still reports this as a warning. It was not moved because doing so can affect existing vector-dependent database objects and requires a controlled migration.

## Validation executed
- Inspected the complete Android source tree and server function.
- Ran a Kotlin compiler syntax pass on the changed Kotlin files; no parser errors were reported (dependency-resolution errors are expected because the standalone compiler invocation lacks the Android/Compose classpath).
- Inspected Android resources and launcher assets.
- Inspected bundled font metadata with `fc-scan` and `fontTools`.
- Generated and visually inspected the transparent logo and launcher icon.
- Applied database migrations and re-ran Supabase security/performance advisors.
- Deployed `trawa-3ar-api` successfully as Edge Function version 6.
- Verified current Supabase schema, row counts, RLS state, and remaining advisor findings.
- Removed stale unit tests that referenced deleted Android-side AI classes/provider keys and updated the API-client test constructor usage.

## Build result
**NOT VERIFIED / BLOCKED** — no Gradle executable and no Gradle wrapper JAR are available in the working project, so no APK was produced or claimed.
