create policy ai_runtime_deny_client on public.ai_runtime_config for all to anon, authenticated using (false) with check (false);
create policy ai_prompts_deny_client on public.ai_prompts for all to anon, authenticated using (false) with check (false);
create policy ai_providers_deny_client on public.ai_providers for all to anon, authenticated using (false) with check (false);
create policy ai_models_deny_client on public.ai_models for all to anon, authenticated using (false) with check (false);
create policy ai_routes_deny_client on public.ai_routes for all to anon, authenticated using (false) with check (false);
create policy ai_flags_deny_client on public.ai_feature_flags for all to anon, authenticated using (false) with check (false);
create policy ai_admins_deny_client on public.ai_admins for all to anon, authenticated using (false) with check (false);
create schema if not exists extensions;
alter extension vector set schema extensions;
