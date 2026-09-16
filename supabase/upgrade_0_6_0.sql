-- Mise à niveau sans perte de données depuis Tropimon Casino 0.5.x.

create or replace function tropimon_casino.register_profile(p_minecraft_uuid uuid,p_minecraft_name text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare p tropimon_casino.profiles;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_minecraft_name !~ '^[A-Za-z0-9_]{1,16}$' then raise exception 'INVALID_MINECRAFT_NAME'; end if;
  if exists(select 1 from tropimon_casino.profiles
      where minecraft_uuid=p_minecraft_uuid and user_id<>auth.uid()) then raise exception 'IDENTITY_LOCKED'; end if;
  select * into p from tropimon_casino.profiles where user_id=auth.uid();
  if found and p.minecraft_uuid<>p_minecraft_uuid then raise exception 'IDENTITY_LOCKED'; end if;
  insert into tropimon_casino.profiles(user_id,minecraft_uuid,minecraft_name)
    values(auth.uid(),p_minecraft_uuid,p_minecraft_name)
    on conflict(user_id) do update set minecraft_name=excluded.minecraft_name,updated_at=now()
    returning * into p;
  return jsonb_build_object('user_id',p.user_id,'minecraft_uuid',p.minecraft_uuid,
    'minecraft_name',p.minecraft_name,'role',p.role,'chips',p.chips);
end $$;

create or replace function tropimon_casino.draw_symbol() returns text
language plpgsql volatile security definer set search_path='' as $$
declare roll bigint; total bigint; raw bytea; value bigint; rejection_limit bigint; result text;
begin
  select sum(weight) into total from tropimon_casino.symbols;
  if total is null or total<=0 then raise exception 'INVALID_SYMBOL_WEIGHTS'; end if;
  rejection_limit:=4294967296-(4294967296%total);
  loop
    raw:=extensions.gen_random_bytes(4);
    value:=(get_byte(raw,0)::bigint*16777216)+(get_byte(raw,1)::bigint*65536)
      +(get_byte(raw,2)::bigint*256)+get_byte(raw,3)::bigint;
    exit when value<rejection_limit;
  end loop;
  roll:=value%total;
  select code into result from (
    select code,sort_order,sum(weight) over(order by sort_order) as cumulative_weight
    from tropimon_casino.symbols
  ) weighted where roll<weighted.cumulative_weight order by sort_order limit 1;
  if result is null then raise exception 'INVALID_SYMBOL_WEIGHTS'; end if;
  return result;
end $$;

create or replace function tropimon_casino.request_deposit(
  p_poke_dollars bigint,p_request_id uuid,p_note text default '')
returns jsonb language plpgsql security definer set search_path='' as $$
declare tx tropimon_casino.transactions;chip_amount bigint;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if not exists(select 1 from tropimon_casino.profiles where user_id=auth.uid()) then
    raise exception 'PROFILE_REQUIRED';
  end if;
  if exists(select 1 from tropimon_casino.profiles where user_id=auth.uid() and role='super_admin') then
    raise exception 'BANK_CANNOT_TRANSACT';
  end if;
  if not exists(select 1 from tropimon_casino.settings where singleton=true and bank_player_name<>'') then
    raise exception 'BANK_UNCONFIGURED';
  end if;
  select * into tx from tropimon_casino.transactions where user_id=auth.uid() and request_id=p_request_id;
  if found then return to_jsonb(tx); end if;
  if char_length(coalesce(p_note,''))>160 then raise exception 'NOTE_TOO_LONG'; end if;
  if p_poke_dollars<=0 then raise exception 'INVALID_DEPOSIT_AMOUNT'; end if;
  chip_amount:=p_poke_dollars;
  insert into tropimon_casino.transactions(user_id,request_id,kind,poke_dollars,chips,player_note)
    values(auth.uid(),p_request_id,'deposit',p_poke_dollars,chip_amount,coalesce(p_note,'')) returning * into tx;
  return to_jsonb(tx);
end $$;

create or replace function tropimon_casino.request_withdrawal(
  p_chips bigint,p_request_id uuid,p_note text default '')
returns jsonb language plpgsql security definer set search_path='' as $$
declare tx tropimon_casino.transactions;available bigint;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if not exists(select 1 from tropimon_casino.profiles where user_id=auth.uid()) then
    raise exception 'PROFILE_REQUIRED';
  end if;
  if exists(select 1 from tropimon_casino.profiles where user_id=auth.uid() and role='super_admin') then
    raise exception 'BANK_CANNOT_TRANSACT';
  end if;
  select * into tx from tropimon_casino.transactions where user_id=auth.uid() and request_id=p_request_id;
  if found then return to_jsonb(tx); end if;
  if p_chips<=0 or char_length(coalesce(p_note,''))>160 then raise exception 'INVALID_WITHDRAWAL'; end if;
  select chips into available from tropimon_casino.profiles where user_id=auth.uid() for update;
  if available<p_chips then raise exception 'INSUFFICIENT_CHIPS'; end if;
  update tropimon_casino.profiles set chips=chips-p_chips,updated_at=now() where user_id=auth.uid();
  insert into tropimon_casino.transactions(user_id,request_id,kind,poke_dollars,chips,player_note)
    values(auth.uid(),p_request_id,'withdrawal',p_chips,p_chips,coalesce(p_note,'')) returning * into tx;
  return to_jsonb(tx);
end $$;

create or replace function tropimon_casino.update_settings(
  p_casino_open boolean,p_bank_player_name text,p_rate integer,p_minimum_bet integer,
  p_maximum_bet integer,p_maximum_payout integer,p_minimum_bank_reserve bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare cfg tropimon_casino.settings;
begin
  if not tropimon_casino.is_super_admin() then raise exception 'SUPER_ADMIN_REQUIRED'; end if;
  if p_rate<>1 or p_bank_player_name!~'^[A-Za-z0-9_]{0,16}$' or p_minimum_bet<1
     or p_maximum_bet<p_minimum_bet or p_maximum_payout<1 or p_minimum_bank_reserve<0 then
    raise exception 'INVALID_SETTINGS';
  end if;
  update tropimon_casino.settings set casino_open=p_casino_open,bank_player_name=p_bank_player_name,
    poke_dollars_per_chip=1,minimum_bet=p_minimum_bet,maximum_bet=p_maximum_bet,
    maximum_payout=p_maximum_payout,minimum_bank_reserve=0,updated_at=now()
    where singleton=true returning * into cfg;
  insert into tropimon_casino.audit_log(actor_user_id,action,details)
    values(auth.uid(),'update_settings',jsonb_build_object('casino_open',p_casino_open,'currency','poke_dollars',
      'minimum_bet',p_minimum_bet,'maximum_bet',p_maximum_bet,'maximum_payout',p_maximum_payout));
  return to_jsonb(cfg)-'bootstrap_hash';
end $$;

create or replace function tropimon_casino.admin_snapshot() returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
  if not tropimon_casino.is_admin() then raise exception 'ADMIN_REQUIRED'; end if;
  return jsonb_build_object(
    'profiles',(select coalesce(jsonb_agg(to_jsonb(p)-'chips' order by minecraft_name),'[]'::jsonb) from tropimon_casino.profiles p),
    'pending',(select coalesce(jsonb_agg(to_jsonb(t) order by created_at),'[]'::jsonb)
      from tropimon_casino.transactions t
      where status='pending' or (kind='withdrawal' and status='approved')),
    'overdue',(select count(*) from tropimon_casino.transactions
      where kind='withdrawal' and status in ('pending','approved') and created_at<=now()-interval '72 hours'),
    'audit',(select coalesce(jsonb_agg(to_jsonb(a) order by created_at desc),'[]'::jsonb)
      from (select * from tropimon_casino.audit_log order by created_at desc limit 100)a)
  );
end $$;

notify pgrst,'reload schema';
