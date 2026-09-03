-- Private temporary video storage for scheduled Master Hub publications.

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values('master-hub-publications','master-hub-publications',false,26214400,array['video/mp4'])
on conflict (id) do update set public=false, file_size_limit=26214400,
  allowed_mime_types=array['video/mp4'];

create table if not exists private.publication_upload_grants (
  token_hash text primary key,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
alter table private.publication_upload_grants enable row level security;
revoke all on private.publication_upload_grants from public, anon, authenticated, service_role;

create or replace function private.master_hub_storage_grant_valid(p_object_name text)
returns boolean language sql stable security definer set search_path=''
as $$
  select exists(
    select 1 from private.publication_upload_grants
    where token_hash=encode(extensions.digest(split_part(p_object_name,'/',1),'sha256'),'hex')
      and expires_at > now()
  );
$$;

create or replace function private.master_hub_create_upload_grant_impl(
  p_device_token text, p_expires_at timestamptz
) returns jsonb language plpgsql security definer set search_path=''
as $$
declare raw_token text := encode(extensions.gen_random_bytes(24),'hex');
declare safe_expiry timestamptz;
begin
  if not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('ok',false,'error','unauthorized');
  end if;
  safe_expiry := least(greatest(coalesce(p_expires_at,now()+interval '1 day'),now()+interval '1 hour'),now()+interval '31 days');
  delete from private.publication_upload_grants where expires_at < now();
  insert into private.publication_upload_grants(token_hash,expires_at)
  values(encode(extensions.digest(raw_token,'sha256'),'hex'),safe_expiry);
  return jsonb_build_object('ok',true,'uploadToken',raw_token,'expiresAt',safe_expiry);
end;
$$;

create or replace function public.master_hub_create_upload_grant(
  p_device_token text, p_expires_at timestamptz
) returns jsonb language sql set search_path=''
as $$ select private.master_hub_create_upload_grant_impl($1,$2); $$;
revoke all on function public.master_hub_create_upload_grant(text,timestamptz) from public, authenticated;
grant execute on function public.master_hub_create_upload_grant(text,timestamptz) to anon, service_role;

grant usage on schema private to anon;
grant execute on function private.master_hub_storage_grant_valid(text) to anon;

drop policy if exists "master_hub_granted_video_insert" on storage.objects;
create policy "master_hub_granted_video_insert" on storage.objects
for insert to anon with check(
  bucket_id='master-hub-publications'
  and lower(storage.extension(name))='mp4'
  and private.master_hub_storage_grant_valid(name)
);

drop policy if exists "master_hub_granted_video_read" on storage.objects;
create policy "master_hub_granted_video_read" on storage.objects
for select to anon using(
  bucket_id='master-hub-publications'
  and private.master_hub_storage_grant_valid(name)
  and storage.allow_any_operation(array['object.get_authenticated_info','object.get_authenticated'])
);

notify pgrst, 'reload schema';
