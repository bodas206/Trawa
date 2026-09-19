# TRAWA — Full-stack repaired working tree

This working tree contains the Android client plus the deployed Supabase Edge backend source used for TRAWA/3AR V1 Pro.

## Important
The final release APK is **not** included because the available environment could not execute Gradle: the project is missing `gradle/wrapper/gradle-wrapper.jar` and no system `gradle` executable is installed.

See `TRAWA_RELEASE_AUDIT.md` for the exact verification status. In particular, the exact Black font binaries requested by the supplied reference image are not yet verified.

## Server
- Edge Function: `trawa-3ar-api`
- Latest deployed version at audit time: **7**
- Provider secrets remain server-side.
- Android must not contain provider API keys.

## Branding
- `branding/font_reference.png`
- `branding/logo-original.png`
- `branding/logo-transparent-reference.png`
- Android launcher and in-app transparent emblem use the supplied logo assets.
