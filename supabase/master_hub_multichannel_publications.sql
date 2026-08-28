-- Platform-specific options for real Video Pins and Telegram video posts.
alter table private.publication_queue
  add column if not exists options jsonb not null default '{}'::jsonb;

alter table private.publication_queue
  drop constraint if exists publication_queue_options_object;
alter table private.publication_queue
  add constraint publication_queue_options_object check (jsonb_typeof(options) = 'object');

create or replace function private.master_hub_enqueue_publication_v2_impl(
  p_device_token text,
  p_platform text,
  p_title text,
  p_description text default '',
  p_media_url text default null,
  p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now(),
  p_options jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare new_id uuid;
declare destination text := trim(coalesce(p_options->>'destination_url',''));
declare board_id text := trim(coalesce(p_options->>'board_id',''));
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  if p_platform not in ('telegram','pinterest','youtube')
     or length(trim(coalesce(p_title,''))) not between 1 and 100
     or length(coalesce(p_description,'')) > 5000
     or p_privacy_status not in ('private','unlisted','public')
     or p_media_url is null or p_media_url !~ '^https://'
     or jsonb_typeof(coalesce(p_options,'{}'::jsonb)) <> 'object'
     or (p_platform in ('telegram','pinterest') and destination !~ '^https://')
     or (board_id <> '' and board_id !~ '^\d+$') then
    return jsonb_build_object('ok', false, 'error', 'invalid_publication');
  end if;
  insert into private.publication_queue(platform,title,description,media_url,privacy_status,scheduled_at,options)
  values(p_platform,trim(p_title),coalesce(p_description,''),p_media_url,p_privacy_status,
    greatest(coalesce(p_scheduled_at,now()),now()),jsonb_strip_nulls(coalesce(p_options,'{}'::jsonb)))
  returning id into new_id;
  return jsonb_build_object('ok',true,'id',new_id,'status','queued');
end;
$$;

create or replace function public.master_hub_enqueue_publication_v2(
  p_device_token text, p_platform text, p_title text, p_description text default '',
  p_media_url text default null, p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now(), p_options jsonb default '{}'::jsonb
) returns jsonb language sql set search_path=''
as $$ select private.master_hub_enqueue_publication_v2_impl($1,$2,$3,$4,$5,$6,$7,$8); $$;

revoke all on function public.master_hub_enqueue_publication_v2(text,text,text,text,text,text,timestamptz,jsonb) from public, authenticated;
grant execute on function public.master_hub_enqueue_publication_v2(text,text,text,text,text,text,timestamptz,jsonb) to anon, service_role;

create or replace function private.master_hub_enqueue_publications_impl(
  p_device_token text,
  p_platforms text[],
  p_title text,
  p_description text default '',
  p_media_url text default null,
  p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now(),
  p_options jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare platform text;
declare result jsonb;
declare jobs jsonb := '[]'::jsonb;
begin
  if coalesce(array_length(p_platforms,1),0) not between 1 and 3
     or (select count(distinct x) from unnest(p_platforms) x) <> array_length(p_platforms,1) then
    return jsonb_build_object('ok',false,'error','invalid_platforms');
  end if;
  foreach platform in array p_platforms loop
    result := private.master_hub_enqueue_publication_v2_impl(p_device_token,platform,p_title,p_description,
      p_media_url,p_privacy_status,p_scheduled_at,p_options);
    if not coalesce((result->>'ok')::boolean,false) then
      raise exception using message = coalesce(result->>'error','queue_failed');
    end if;
    jobs := jobs || jsonb_build_array(jsonb_build_object('platform',platform,'id',result->>'id','status','queued'));
  end loop;
  return jsonb_build_object('ok',true,'status','queued','jobs',jobs);
exception when others then
  return jsonb_build_object('ok',false,'error',sqlerrm);
end;
$$;

create or replace function public.master_hub_enqueue_publications(
  p_device_token text, p_platforms text[], p_title text, p_description text default '',
  p_media_url text default null, p_privacy_status text default 'private',
  p_scheduled_at timestamptz default now(), p_options jsonb default '{}'::jsonb
) returns jsonb language sql set search_path=''
as $$ select private.master_hub_enqueue_publications_impl($1,$2,$3,$4,$5,$6,$7,$8); $$;

revoke all on function public.master_hub_enqueue_publications(text,text[],text,text,text,text,timestamptz,jsonb) from public, authenticated;
grant execute on function public.master_hub_enqueue_publications(text,text[],text,text,text,text,timestamptz,jsonb) to anon, service_role;
notify pgrst, 'reload schema';
