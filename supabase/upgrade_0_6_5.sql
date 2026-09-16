-- Tropimon Casino 0.6.5: supprime l'ambiguïté PL/pgSQL entre une variable et une colonne.
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
