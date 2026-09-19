alter table public.user_preferences
  add column if not exists preferred_language text not null default 'Auto (Default)';
