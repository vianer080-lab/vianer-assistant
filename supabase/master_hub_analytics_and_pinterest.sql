create table if not exists private.affiliate_links (
  slug text primary key check (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
  provider text not null check (provider in ('amazon','temu','aliexpress','other')),
  label text not null,
  destination_url text not null check (destination_url ~ '^https://'),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists private.affiliate_clicks (
  id bigint generated always as identity primary key,
  slug text not null references private.affiliate_links(slug),
  referrer_host text not null default '' check (length(referrer_host) <= 200),
  clicked_at timestamptz not null default now()
);

create index if not exists affiliate_clicks_clicked_at_idx
  on private.affiliate_clicks(clicked_at desc);
create index if not exists affiliate_clicks_slug_clicked_at_idx
  on private.affiliate_clicks(slug, clicked_at desc);

create table if not exists private.publications (
  id bigint generated always as identity primary key,
  platform text not null check (platform in ('telegram','facebook','instagram','pinterest','youtube','other')),
  external_id text,
  title text not null default '',
  url text,
  status text not null default 'published' check (status in ('queued','published','failed')),
  published_at timestamptz,
  created_at timestamptz not null default now(),
  unique nulls not distinct (platform, external_id)
);

alter table private.affiliate_links enable row level security;
alter table private.affiliate_clicks enable row level security;
alter table private.publications enable row level security;

revoke all on private.affiliate_links from public, anon, authenticated;
revoke all on private.affiliate_clicks from public, anon, authenticated;
revoke all on private.publications from public, anon, authenticated;

insert into private.affiliate_links(slug, provider, label, destination_url, active)
values
  ('temu-main', 'temu', 'Temu MasterPick Georgia', 'https://temu.to/k/efotg114b1z', true),
  ('amazon-earbuds', 'amazon', 'Amazon earbuds', 'https://amzn.to/4crjKYl', false),
  ('aliexpress-main', 'aliexpress', 'AliExpress offer', 'https://ali.click/bogoj1m', false)
on conflict (slug) do update set
  provider = excluded.provider,
  label = excluded.label,
  destination_url = excluded.destination_url,
  active = excluded.active,
  updated_at = now();

create or replace function private.master_hub_device_authorized(p_device_token text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_device_token is not null and exists (
    select 1 from public.liya_devices
    where enabled = true
      and device_key_hash = encode(extensions.digest(p_device_token, 'sha256'), 'hex')
  );
$$;

create or replace function private.master_hub_record_click_impl(
  p_device_token text,
  p_slug text,
  p_referrer_host text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare link private.affiliate_links%rowtype;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false);
  end if;
  select * into link from private.affiliate_links
    where slug = lower(p_slug) and active = true;
  if not found then return jsonb_build_object('ok', false); end if;
  insert into private.affiliate_clicks(slug, referrer_host)
    values (link.slug, left(coalesce(p_referrer_host, ''), 200));
  return jsonb_build_object('ok', true, 'destination', link.destination_url, 'provider', link.provider);
end;
$$;

create or replace function private.master_hub_analytics_impl(p_device_token text, p_days integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare safe_days integer := greatest(1, least(coalesce(p_days, 1), 30));
declare click_count bigint;
declare publication_count bigint;
declare by_provider jsonb;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  select count(*) into click_count from private.affiliate_clicks
    where clicked_at >= now() - make_interval(days => safe_days);
  select count(*) into publication_count from private.publications
    where status = 'published' and published_at >= now() - make_interval(days => safe_days);
  select coalesce(jsonb_object_agg(provider, clicks), '{}'::jsonb) into by_provider
  from (
    select l.provider, count(*)::bigint clicks
    from private.affiliate_clicks c join private.affiliate_links l using(slug)
    where c.clicked_at >= now() - make_interval(days => safe_days)
    group by l.provider
  ) totals;
  return jsonb_build_object(
    'ok', true,
    'days', safe_days,
    'metrics', jsonb_build_object('publications', publication_count, 'clicks', click_count, 'orders', 0, 'revenue', 0),
    'clicksByProvider', by_provider,
    'measuredFrom', now() - make_interval(days => safe_days),
    'measuredAt', now()
  );
end;
$$;

create or replace function private.master_hub_publications_impl(p_device_token text, p_limit integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare items jsonb;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  select coalesce(jsonb_agg(to_jsonb(p) order by p.created_at desc), '[]'::jsonb) into items
  from (
    select id, platform, external_id, title, url, status, published_at, created_at
    from private.publications
    order by created_at desc
    limit greatest(1, least(coalesce(p_limit, 100), 200))
  ) p;
  return jsonb_build_object('ok', true, 'items', items);
end;
$$;

create or replace function public.master_hub_record_click(p_device_token text, p_slug text, p_referrer_host text default '')
returns jsonb language sql set search_path = ''
as $$ select private.master_hub_record_click_impl(p_device_token, p_slug, p_referrer_host); $$;

create or replace function public.master_hub_analytics(p_device_token text, p_days integer default 1)
returns jsonb language sql set search_path = ''
as $$ select private.master_hub_analytics_impl(p_device_token, p_days); $$;

create or replace function public.master_hub_publications(p_device_token text, p_limit integer default 100)
returns jsonb language sql set search_path = ''
as $$ select private.master_hub_publications_impl(p_device_token, p_limit); $$;

revoke all on function public.master_hub_record_click(text,text,text) from public;
revoke all on function public.master_hub_analytics(text,integer) from public;
revoke all on function public.master_hub_publications(text,integer) from public;
grant execute on function public.master_hub_record_click(text,text,text) to anon, authenticated, service_role;
grant execute on function public.master_hub_analytics(text,integer) to anon, authenticated, service_role;
grant execute on function public.master_hub_publications(text,integer) to anon, authenticated, service_role;

-- Pinterest was missing from the existing OAuth storage allow-list.
create or replace function private.master_hub_store_oauth_impl(p_device_token text, p_service text, p_token jsonb, p_metadata jsonb)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_service not in ('youtube', 'instagram', 'facebook', 'pinterest')
     or not private.master_hub_device_authorized(p_device_token) then return false;
  end if;
  insert into private.oauth_connections(service, token_cipher, metadata, connected_at, updated_at)
  values (p_service, extensions.pgp_sym_encrypt(p_token::text, p_device_token, 'cipher-algo=aes256'), coalesce(p_metadata, '{}'::jsonb), now(), now())
  on conflict (service) do update
    set token_cipher=excluded.token_cipher, metadata=excluded.metadata, updated_at=now();
  return true;
end;
$$;

notify pgrst, 'reload schema';
