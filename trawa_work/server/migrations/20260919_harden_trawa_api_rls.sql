alter table public.agent_runs enable row level security;
alter table public.attachments enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.user_memory enable row level security;
alter table public.user_preferences enable row level security;

drop policy if exists "Users can access own agent runs" on public.agent_runs;
create policy "Authenticated users can access own agent runs" on public.agent_runs for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can access own attachments" on public.attachments;
create policy "Authenticated users can access own attachments" on public.attachments for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can access own conversations" on public.conversations;
create policy "Authenticated users can access own conversations" on public.conversations for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can access own messages" on public.messages;
create policy "Authenticated users can access own messages" on public.messages for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can access own memory" on public.user_memory;
create policy "Authenticated users can access own memory" on public.user_memory for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can access own preferences" on public.user_preferences;
create policy "Authenticated users can access own preferences" on public.user_preferences for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
