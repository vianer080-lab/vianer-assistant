-- Applied to the Master Hub Backend project on 2026-08-28.
-- Server-only OAuth retrieval and publication logging for YouTube uploads.

create or replace function private.master_hub_get_oauth_token_impl(
  p_device_token text,
  p_service text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare result jsonb;
begin
  if p_service <> 'youtube'
     or not private.master_hub_device_authorized(p_device_token) then
    return jsonb_build_object('authorized', false);
  end if;

  select (extensions.pgp_sym_decrypt(token_cipher, p_device_token)::text)::jsonb
    into result
    from private.oauth_connections
   where service = p_service;

  if result is null then
    return jsonb_build_object('authorized', false);
  end if;
  return jsonb_build_object('authorized', true, 'token', result);
end;
$$;

create or replace function public.master_hub_get_oauth_token(
  p_device_token text,
  p_service text
)
returns jsonb
language sql
set search_path = ''
as $$ select private.master_hub_get_oauth_token_impl(p_device_token, p_service); $$;

revoke all on function public.master_hub_get_oauth_token(text, text) from public, authenticated;
grant execute on function public.master_hub_get_oauth_token(text, text) to anon, service_role;

create or replace function private.master_hub_record_publication_impl(
  p_device_token text,
  p_platform text,
  p_external_id text,
  p_title text,
  p_url text,
  p_status text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare publication_id bigint;
begin
  if not private.master_hub_device_authorized(p_device_token)
     or p_platform not in ('youtube', 'telegram', 'facebook', 'instagram', 'pinterest', 'other')
     or p_status not in ('queued', 'published', 'failed') then
    return jsonb_build_object('ok', false, 'error', 'unauthorized_or_invalid');
  end if;

  insert into private.publications(platform, external_id, title, url, status, published_at)
  values (
    p_platform, nullif(p_external_id, ''), coalesce(p_title, ''), nullif(p_url, ''), p_status,
    case when p_status = 'published' then now() else null end
  )
  returning id into publication_id;
  return jsonb_build_object('ok', true, 'id', publication_id);
end;
$$;

create or replace function public.master_hub_record_publication(
  p_device_token text,
  p_platform text,
  p_external_id text,
  p_title text,
  p_url text,
  p_status text
)
returns jsonb
language sql
set search_path = ''
as $$
  select private.master_hub_record_publication_impl(
    p_device_token, p_platform, p_external_id, p_title, p_url, p_status
  );
$$;

revoke all on function public.master_hub_record_publication(text, text, text, text, text, text)
  from public, authenticated;
grant execute on function public.master_hub_record_publication(text, text, text, text, text, text)
  to anon, service_role;

notify pgrst, 'reload schema';
