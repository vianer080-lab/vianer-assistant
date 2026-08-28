-- Durable publication queue for Master Hub.

create table if not exists private.publication_queue (
  id uuid primary key default gen_random_uuid(),
  platform text not null check (platform in ('telegram','facebook','instagram','pinterest','youtube')),
  title text not null check (length(title) between 1 and 100),
  description text not null default '' check (length(description) <= 5000),
  media_url text check (media_url is null or media_url ~ '^https://'),
  privacy_status text not null default 'private' check (privacy_status in ('private','unlisted','public')),
  scheduled_at timestamptz not null default now(),
  status text not null default 'queued' check (status in ('queued','processing','published','failed','cancelled')),
  attempt_count integer not null default 0 check (attempt_count >= 0),
  max_attempts integer not null default 3 check (max_attempts between 1 and 10),
  last_error text,
  external_id text,
  url text,
  locked_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists publication_queue_due_idx
  on private.publication_queue(scheduled_at, created_at)
  where status = 'queued';
create index if not exists publication_queue_status_created_idx
  on private.publication_queue(status, created_at desc);

alter table private.publication_queue enable row level security;
revoke all on private.publication_queue from public, anon, authenticated, service_role;

create or replace function private.master_hub_enqueue_publication_impl(
  p_device_token text,
  p_platform text,
  p_title text,
  p_description text default '',
  p_media_url text default null,
  p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare new_id uuid;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  if p_platform not in ('telegram','facebook','instagram','pinterest','youtube')
     or length(trim(coalesce(p_title, ''))) not between 1 and 100
     or length(coalesce(p_description, '')) > 5000
     or p_privacy_status not in ('private','unlisted','public')
     or (p_media_url is not null and p_media_url !~ '^https://')
     or (p_platform = 'youtube' and p_media_url is null) then
    return jsonb_build_object('ok', false, 'error', 'invalid_publication');
  end if;

  insert into private.publication_queue(
    platform, title, description, media_url, privacy_status, scheduled_at
  ) values (
    p_platform, trim(p_title), coalesce(p_description, ''), p_media_url,
    p_privacy_status, greatest(coalesce(p_scheduled_at, now()), now())
  ) returning id into new_id;
  return jsonb_build_object('ok', true, 'id', new_id, 'status', 'queued');
end;
$$;

create or replace function private.master_hub_claim_publication_impl(p_device_token text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare job private.publication_queue%rowtype;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;

  update private.publication_queue
     set status = 'queued', locked_at = null, updated_at = now(),
         last_error = coalesce(last_error, 'stale_worker_lock')
   where status = 'processing' and locked_at < now() - interval '15 minutes';

  select * into job
    from private.publication_queue
   where status = 'queued'
     and scheduled_at <= now()
     and attempt_count < max_attempts
   order by scheduled_at, created_at
   for update skip locked
   limit 1;

  if not found then return jsonb_build_object('ok', true, 'job', null); end if;

  update private.publication_queue
     set status = 'processing', attempt_count = attempt_count + 1,
         locked_at = now(), updated_at = now(), last_error = null
   where id = job.id
   returning * into job;
  return jsonb_build_object('ok', true, 'job', to_jsonb(job));
end;
$$;

create or replace function private.master_hub_finish_publication_impl(
  p_device_token text,
  p_id uuid,
  p_success boolean,
  p_external_id text default null,
  p_url text default null,
  p_error text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare job private.publication_queue%rowtype;
declare next_status text;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  select * into job from private.publication_queue where id = p_id for update;
  if not found or job.status <> 'processing' then
    return jsonb_build_object('ok', false, 'error', 'job_not_processing');
  end if;

  if p_success then
    update private.publication_queue set
      status='published', external_id=nullif(p_external_id,''), url=nullif(p_url,''),
      published_at=now(), locked_at=null, updated_at=now(), last_error=null
    where id=p_id returning * into job;
    insert into private.publications(platform, external_id, title, url, status, published_at)
    values(job.platform, job.external_id, job.title, job.url, 'published', job.published_at)
    on conflict (platform, external_id) do update set
      title=excluded.title, url=excluded.url, status='published', published_at=excluded.published_at;
  else
    next_status := case when job.attempt_count < job.max_attempts then 'queued' else 'failed' end;
    update private.publication_queue set
      status=next_status, last_error=left(coalesce(p_error,'publication_failed'),1000),
      scheduled_at=case when next_status='queued' then now()+interval '5 minutes' else scheduled_at end,
      locked_at=null, updated_at=now()
    where id=p_id returning * into job;
  end if;
  return jsonb_build_object('ok', true, 'id', job.id, 'status', job.status);
end;
$$;

create or replace function private.master_hub_publication_queue_impl(p_device_token text, p_limit integer default 100)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare queued_items jsonb;
declare history_items jsonb;
declare safe_limit integer := greatest(1, least(coalesce(p_limit,100),200));
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  select coalesce(jsonb_agg(to_jsonb(q) order by q.scheduled_at, q.created_at),'[]'::jsonb)
    into queued_items from (
      select id,platform,title,status,scheduled_at,attempt_count,max_attempts,last_error,created_at
      from private.publication_queue where status in ('queued','processing') limit safe_limit
    ) q;
  select coalesce(jsonb_agg(to_jsonb(h) order by h.updated_at desc),'[]'::jsonb)
    into history_items from (
      select id,platform,title,status,url,published_at,last_error,updated_at
      from private.publication_queue where status in ('published','failed','cancelled')
      order by updated_at desc limit safe_limit
    ) h;
  return jsonb_build_object('ok',true,'queue',queued_items,'history',history_items);
end;
$$;

create or replace function public.master_hub_enqueue_publication(
  p_device_token text, p_platform text, p_title text, p_description text default '',
  p_media_url text default null, p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now()
) returns jsonb language sql set search_path=''
as $$ select private.master_hub_enqueue_publication_impl($1,$2,$3,$4,$5,$6,$7); $$;

create or replace function public.master_hub_claim_publication(p_device_token text)
returns jsonb language sql set search_path=''
as $$ select private.master_hub_claim_publication_impl($1); $$;

create or replace function public.master_hub_finish_publication(
  p_device_token text, p_id uuid, p_success boolean, p_external_id text default null,
  p_url text default null, p_error text default null
) returns jsonb language sql set search_path=''
as $$ select private.master_hub_finish_publication_impl($1,$2,$3,$4,$5,$6); $$;

create or replace function public.master_hub_publication_queue(p_device_token text, p_limit integer default 100)
returns jsonb language sql set search_path=''
as $$ select private.master_hub_publication_queue_impl($1,$2); $$;

revoke all on function public.master_hub_enqueue_publication(text,text,text,text,text,text,timestamptz) from public, authenticated;
revoke all on function public.master_hub_claim_publication(text) from public, authenticated;
revoke all on function public.master_hub_finish_publication(text,uuid,boolean,text,text,text) from public, authenticated;
revoke all on function public.master_hub_publication_queue(text,integer) from public, authenticated;
grant execute on function public.master_hub_enqueue_publication(text,text,text,text,text,text,timestamptz) to anon, service_role;
grant execute on function public.master_hub_claim_publication(text) to anon, service_role;
grant execute on function public.master_hub_finish_publication(text,uuid,boolean,text,text,text) to anon, service_role;
grant execute on function public.master_hub_publication_queue(text,integer) to anon, service_role;

notify pgrst, 'reload schema';
