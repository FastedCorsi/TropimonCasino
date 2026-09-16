create extension if not exists pgcrypto with schema extensions;

create schema if not exists tropimon_casino;
grant usage on schema tropimon_casino to authenticated;

create table if not exists tropimon_casino.settings (
  singleton boolean primary key default true check (singleton),
  casino_open boolean not null default true,
  bank_player_name text not null default '' check (char_length(bank_player_name) <= 16),
  poke_dollars_per_chip integer not null default 1 check (poke_dollars_per_chip between 1 and 1000000),
  minimum_bet integer not null default 1 check (minimum_bet > 0),
  maximum_bet integer not null default 25 check (maximum_bet >= minimum_bet),
  maximum_payout integer not null default 100000 check (maximum_payout > 0),
  minimum_bank_reserve bigint not null default 0 check (minimum_bank_reserve >= 0),
  bootstrap_hash bytea,
  updated_at timestamptz not null default now()
);
-- Le hash d'activation est injecté manuellement lors d'un nouveau déploiement.
-- Il n'est jamais versionné et une réapplication de la migration ne rouvre pas l'activation.
insert into tropimon_casino.settings(singleton) values (true) on conflict(singleton) do nothing;

create table if not exists tropimon_casino.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  minecraft_uuid uuid not null unique,
  minecraft_name text not null check (minecraft_name ~ '^[A-Za-z0-9_]{1,16}$'),
  role text not null default 'player' check (role in ('player','admin','super_admin')),
  chips bigint not null default 0 check (chips >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists casino_single_super_admin
  on tropimon_casino.profiles ((role)) where role = 'super_admin';

create table if not exists tropimon_casino.symbols (
  code text primary key,
  sort_order integer not null unique,
  weight integer not null check (weight > 0),
  pair_multiplier integer not null check (pair_multiplier >= 0),
  triple_multiplier integer not null check (triple_multiplier > 0)
);
insert into tropimon_casino.symbols(code,sort_order,weight,pair_multiplier,triple_multiplier) values
  ('oran',1,30,1,2),('poke_ball',2,25,1,3),('psyduck',3,18,2,5),
  ('slowpoke',4,12,3,10),('meowth',5,8,5,20),('gengar',6,5,8,50),
  ('tropimon',7,2,15,150)
on conflict(code) do update set sort_order=excluded.sort_order,weight=excluded.weight,
  pair_multiplier=excluded.pair_multiplier,triple_multiplier=excluded.triple_multiplier;

create table if not exists tropimon_casino.spins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references tropimon_casino.profiles(user_id) on delete restrict,
  request_id uuid not null,
  bet integer not null check (bet > 0),
  reel_1 text not null references tropimon_casino.symbols(code),
  reel_2 text not null references tropimon_casino.symbols(code),
  reel_3 text not null references tropimon_casino.symbols(code),
  payout bigint not null check (payout >= 0),
  balance_after bigint not null check (balance_after >= 0),
  created_at timestamptz not null default now(),
  unique(user_id, request_id)
);
create index if not exists casino_spins_user_created_idx on tropimon_casino.spins(user_id,created_at desc);

create table if not exists tropimon_casino.transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references tropimon_casino.profiles(user_id) on delete restrict,
  request_id uuid not null,
  kind text not null check (kind in ('deposit','withdrawal')),
  poke_dollars bigint not null check (poke_dollars > 0),
  chips bigint not null check (chips > 0),
  status text not null default 'pending' check (status in ('pending','approved','paid','rejected')),
  player_note text not null default '' check (char_length(player_note) <= 160),
  admin_note text not null default '' check (char_length(admin_note) <= 160),
  reviewed_by uuid references tropimon_casino.profiles(user_id),
  reviewed_at timestamptz,
  paid_by uuid references tropimon_casino.profiles(user_id),
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  unique(user_id, request_id)
);
create index if not exists casino_transactions_status_created_idx
  on tropimon_casino.transactions(status,created_at);

-- Migration unique vers une économie directe en Poké$. Les colonnes "chips"
-- restent en place pour préserver la compatibilité des données et de l'API,
-- mais stockent désormais des Poké$ sans conversion.
do $$
declare old_rate integer;
begin
  select poke_dollars_per_chip into old_rate from tropimon_casino.settings where singleton=true for update;
  if old_rate is not null and old_rate<>1 then
    update tropimon_casino.profiles set chips=chips*old_rate;
    update tropimon_casino.spins set bet=(bet::bigint*old_rate)::integer,
      payout=payout*old_rate,balance_after=balance_after*old_rate;
    update tropimon_casino.transactions set chips=poke_dollars;
    update tropimon_casino.settings set
      minimum_bet=(minimum_bet::bigint*old_rate)::integer,
      maximum_bet=(maximum_bet::bigint*old_rate)::integer,
      maximum_payout=(maximum_payout::bigint*old_rate)::integer,
      poke_dollars_per_chip=1,updated_at=now()
      where singleton=true;
  end if;
end $$;
alter table tropimon_casino.settings alter column poke_dollars_per_chip set default 1;

create table if not exists tropimon_casino.audit_log (
  id bigint generated always as identity primary key,
  actor_user_id uuid references auth.users(id) on delete set null,
  action text not null,
  target_id text not null default '',
  details jsonb not null default '{}',
  created_at timestamptz not null default now()
);

alter table tropimon_casino.settings enable row level security;
alter table tropimon_casino.profiles enable row level security;
alter table tropimon_casino.symbols enable row level security;
alter table tropimon_casino.spins enable row level security;
alter table tropimon_casino.transactions enable row level security;
alter table tropimon_casino.audit_log enable row level security;

create or replace function tropimon_casino.is_admin(p_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from tropimon_casino.profiles where user_id=p_user and role in ('admin','super_admin'))
$$;
create or replace function tropimon_casino.is_super_admin(p_user uuid default auth.uid()) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from tropimon_casino.profiles where user_id=p_user and role='super_admin')
$$;

drop policy if exists casino_settings_read on tropimon_casino.settings;
create policy casino_settings_read on tropimon_casino.settings for select to authenticated using(true);
drop policy if exists casino_symbols_read on tropimon_casino.symbols;
create policy casino_symbols_read on tropimon_casino.symbols for select to authenticated using(true);
drop policy if exists casino_profiles_read on tropimon_casino.profiles;
create policy casino_profiles_read on tropimon_casino.profiles for select to authenticated
  using(user_id=auth.uid() or tropimon_casino.is_admin());
drop policy if exists casino_spins_read on tropimon_casino.spins;
create policy casino_spins_read on tropimon_casino.spins for select to authenticated
  using(user_id=auth.uid() or tropimon_casino.is_admin());
drop policy if exists casino_transactions_read on tropimon_casino.transactions;
create policy casino_transactions_read on tropimon_casino.transactions for select to authenticated
  using(user_id=auth.uid() or tropimon_casino.is_admin());
drop policy if exists casino_audit_admin_read on tropimon_casino.audit_log;
create policy casino_audit_admin_read on tropimon_casino.audit_log for select to authenticated
  using(tropimon_casino.is_admin());

grant select on tropimon_casino.settings,tropimon_casino.symbols,tropimon_casino.profiles,
  tropimon_casino.spins,tropimon_casino.transactions,tropimon_casino.audit_log to authenticated;

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

create or replace function tropimon_casino.bootstrap_super_admin(
  p_secret text,p_minecraft_uuid uuid,p_minecraft_name text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare expected bytea; result jsonb;
begin
  select bootstrap_hash into expected from tropimon_casino.settings where singleton=true for update;
  if expected is null then raise exception 'BOOTSTRAP_CLOSED'; end if;
  if extensions.digest(convert_to(p_secret,'UTF8'),'sha256')<>expected then raise exception 'BOOTSTRAP_DENIED'; end if;
  result:=tropimon_casino.register_profile(p_minecraft_uuid,p_minecraft_name);
  update tropimon_casino.profiles set role='super_admin',updated_at=now() where user_id=auth.uid();
  update tropimon_casino.settings set bootstrap_hash=null,bank_player_name=p_minecraft_name,updated_at=now()
    where singleton=true;
  insert into tropimon_casino.audit_log(actor_user_id,action,target_id) values(auth.uid(),'bootstrap_super_admin',auth.uid()::text);
  return result || jsonb_build_object('role','super_admin');
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
revoke execute on function tropimon_casino.draw_symbol() from public,anon,authenticated;

create or replace function tropimon_casino.spin(p_bet integer,p_request_id uuid)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare profile tropimon_casino.profiles; cfg tropimon_casino.settings; previous tropimon_casino.spins;
declare a text;b text;c text;multiplier integer:=0;won bigint;new_balance bigint;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  select * into previous from tropimon_casino.spins where user_id=auth.uid() and request_id=p_request_id;
  if found then return to_jsonb(previous); end if;
  select * into cfg from tropimon_casino.settings where singleton=true;
  if not cfg.casino_open then raise exception 'CASINO_CLOSED'; end if;
  if p_bet<cfg.minimum_bet or p_bet>cfg.maximum_bet then raise exception 'INVALID_BET'; end if;
  select * into profile from tropimon_casino.profiles where user_id=auth.uid() for update;
  if not found then raise exception 'PROFILE_REQUIRED'; end if;
  if profile.role<>'super_admin' and profile.chips<p_bet then raise exception 'INSUFFICIENT_CHIPS'; end if;
  a:=tropimon_casino.draw_symbol();b:=tropimon_casino.draw_symbol();c:=tropimon_casino.draw_symbol();
  if a=b and b=c then select triple_multiplier into multiplier from tropimon_casino.symbols where code=a;
  elsif a=b or a=c then select pair_multiplier into multiplier from tropimon_casino.symbols where code=a;
  elsif b=c then select pair_multiplier into multiplier from tropimon_casino.symbols where code=b;
  end if;
  won:=least(p_bet::bigint*multiplier,cfg.maximum_payout::bigint);
  if profile.role='super_admin' then
    new_balance:=profile.chips;
  else
    update tropimon_casino.profiles set chips=chips-p_bet+won,updated_at=now()
      where user_id=auth.uid() returning chips into new_balance;
  end if;
  insert into tropimon_casino.spins(user_id,request_id,bet,reel_1,reel_2,reel_3,payout,balance_after)
    values(auth.uid(),p_request_id,p_bet,a,b,c,won,new_balance) returning * into previous;
  return to_jsonb(previous);
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

create or replace function tropimon_casino.review_transaction(
  p_transaction_id uuid,p_approved boolean,p_admin_note text default '')
returns jsonb language plpgsql security definer set search_path='' as $$
declare tx tropimon_casino.transactions;
begin
  if not tropimon_casino.is_admin() then raise exception 'ADMIN_REQUIRED'; end if;
  if char_length(coalesce(p_admin_note,''))>160 then raise exception 'NOTE_TOO_LONG'; end if;
  select * into tx from tropimon_casino.transactions where id=p_transaction_id for update;
  if not found or tx.status<>'pending' then raise exception 'TRANSACTION_NOT_PENDING'; end if;
  if tx.user_id=auth.uid() then raise exception 'SELF_REVIEW_FORBIDDEN'; end if;
  if p_approved and tx.kind='deposit' then
    update tropimon_casino.profiles set chips=chips+tx.chips,updated_at=now() where user_id=tx.user_id;
  elsif not p_approved and tx.kind='withdrawal' then
    update tropimon_casino.profiles set chips=chips+tx.chips,updated_at=now() where user_id=tx.user_id;
  end if;
  update tropimon_casino.transactions set status=case when p_approved then 'approved' else 'rejected' end,
    admin_note=coalesce(p_admin_note,''),reviewed_by=auth.uid(),reviewed_at=now()
    where id=tx.id returning * into tx;
  insert into tropimon_casino.audit_log(actor_user_id,action,target_id,details)
    values(auth.uid(),'review_transaction',tx.id::text,jsonb_build_object('approved',p_approved,'kind',tx.kind));
  return to_jsonb(tx);
end $$;

create or replace function tropimon_casino.mark_withdrawal_paid(p_transaction_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare tx tropimon_casino.transactions;
begin
  if not tropimon_casino.is_admin() then raise exception 'ADMIN_REQUIRED'; end if;
  select * into tx from tropimon_casino.transactions where id=p_transaction_id for update;
  if not found or tx.kind<>'withdrawal' or tx.status<>'approved' then raise exception 'WITHDRAWAL_NOT_PAYABLE'; end if;
  if tx.user_id=auth.uid() then raise exception 'SELF_PAYMENT_FORBIDDEN'; end if;
  update tropimon_casino.transactions set status='paid',paid_by=auth.uid(),paid_at=now()
    where id=tx.id returning * into tx;
  insert into tropimon_casino.audit_log(actor_user_id,action,target_id) values(auth.uid(),'mark_withdrawal_paid',tx.id::text);
  return to_jsonb(tx);
end $$;

create or replace function tropimon_casino.set_admin(p_profile_id uuid,p_enabled boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
declare profile tropimon_casino.profiles;
begin
  if not tropimon_casino.is_super_admin() then raise exception 'SUPER_ADMIN_REQUIRED'; end if;
  select * into profile from tropimon_casino.profiles where user_id=p_profile_id for update;
  if not found or profile.role='super_admin' then raise exception 'INVALID_ADMIN_TARGET'; end if;
  update tropimon_casino.profiles set role=case when p_enabled then 'admin' else 'player' end,updated_at=now()
    where user_id=p_profile_id returning * into profile;
  insert into tropimon_casino.audit_log(actor_user_id,action,target_id,details)
    values(auth.uid(),'set_admin',p_profile_id::text,jsonb_build_object('enabled',p_enabled));
  return to_jsonb(profile)-'chips';
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
      'minimum_bet',p_minimum_bet,'maximum_bet',p_maximum_bet,'maximum_payout',p_maximum_payout,
      'minimum_bank_reserve',0));
  return to_jsonb(cfg)-'bootstrap_hash';
end $$;

create or replace function tropimon_casino.snapshot() returns jsonb
language sql stable security definer set search_path='' as $$
  select jsonb_build_object(
    'profile',(select to_jsonb(p) from tropimon_casino.profiles p where p.user_id=auth.uid()),
    'settings',(select to_jsonb(s)-'bootstrap_hash' from tropimon_casino.settings s where singleton=true),
    'transactions',coalesce((select jsonb_agg(to_jsonb(t) order by created_at desc)
      from (select * from tropimon_casino.transactions where user_id=auth.uid() order by created_at desc limit 30)t),'[]'::jsonb),
    'spins',coalesce((select jsonb_agg(to_jsonb(s) order by created_at desc)
      from (select * from tropimon_casino.spins where user_id=auth.uid() order by created_at desc limit 30)s),'[]'::jsonb)
  )
$$;

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

revoke execute on all functions in schema tropimon_casino from public,anon;
alter default privileges in schema tropimon_casino revoke execute on functions from public;
grant execute on function tropimon_casino.is_admin(uuid),tropimon_casino.is_super_admin(uuid),
  tropimon_casino.register_profile(uuid,text),
  tropimon_casino.bootstrap_super_admin(text,uuid,text),tropimon_casino.spin(integer,uuid),
  tropimon_casino.request_deposit(bigint,uuid,text),tropimon_casino.request_withdrawal(bigint,uuid,text),
  tropimon_casino.review_transaction(uuid,boolean,text),tropimon_casino.mark_withdrawal_paid(uuid),
  tropimon_casino.set_admin(uuid,boolean),tropimon_casino.update_settings(boolean,text,integer,integer,integer,integer,bigint),
  tropimon_casino.snapshot(),tropimon_casino.admin_snapshot() to authenticated;

alter role authenticator set pgrst.db_schemas='public,storage,graphql_public,tropimon_casino';
notify pgrst,'reload config';
notify pgrst,'reload schema';
