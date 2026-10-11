create extension if not exists pgcrypto;

create table if not exists public.patrix_licenses (
  id uuid primary key default gen_random_uuid(),
  key_digest text not null unique,
  label text,
  status text not null default 'active' check (status in ('active','revoked','suspended')),
  expires_at timestamptz not null,
  bound_device_id text,
  max_devices integer not null default 1 check (max_devices between 1 and 5),
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.patrix_login_audit (
  id bigint generated always as identity primary key,
  license_id uuid references public.patrix_licenses(id) on delete set null,
  device_id text,
  outcome text not null check (outcome in ('accepted','rejected')),
  reason text,
  created_at timestamptz not null default now()
);

create index if not exists patrix_licenses_key_digest_idx on public.patrix_licenses(key_digest);
create index if not exists patrix_login_audit_created_at_idx on public.patrix_login_audit(created_at desc);

alter table public.patrix_licenses enable row level security;
alter table public.patrix_login_audit enable row level security;
revoke all on public.patrix_licenses from anon, authenticated;
revoke all on public.patrix_login_audit from anon, authenticated;
create policy patrix_licenses_deny_all on public.patrix_licenses for all to anon, authenticated using (false) with check (false);
create policy patrix_login_audit_deny_all on public.patrix_login_audit for all to anon, authenticated using (false) with check (false);

-- Insert licenses from a secure admin workflow; never store raw keys.
