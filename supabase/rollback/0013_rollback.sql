-- =====================================================================================================
-- NightWatch Protocol - REVERSA de 0013_batallas_ingles.sql
--
-- NO es una migración: no se aplica a un proyecto nuevo. Se pega a mano en el SQL Editor SOLO si 0013 se aplicó y hay que
-- deshacerla. Deja el servidor como estaba tras 0012:
--   * restaura sx_battle_state(uuid, uuid) con el cuerpo de 0011, battle_sync(uuid) y battle_play(uuid, text, integer, text)
--     con el cuerpo de 0008, y sus permisos;
--   * elimina las versiones con p_lang y las dos tablas de traducciones (con sus datos).
-- Las apps v2.3.0 y anteriores no notan nada. La app v2.4.0 detecta que el servidor no acepta p_lang y vuelve a llamar
-- sin él (las preguntas salen en español).
-- Se puede ejecutar más de una vez. Todo va en una transacción: si algo falla, no cambia nada.
-- =====================================================================================================

begin;

-- 1. Quitar las funciones nuevas (firmas con p_lang)
drop function if exists public.battle_sync(uuid, text);
drop function if exists public.battle_play(uuid, text, integer, text, text);
drop function if exists public.sx_battle_state(uuid, uuid, text);

-- 2. sx_battle_state de 0011 (interna)
create or replace function public.sx_battle_state(p_battle uuid, p_user uuid)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_b     public.battles%rowtype;
  v_now   timestamptz := clock_timestamp();
  v_seat  integer;
  v_rival uuid;
  v_out   jsonb;
  v_q     public.battle_questions%rowtype;
  v_u     public.battle_ultis%rowtype;
  v_title text;
  v_is_att boolean;
  v_last  public.battle_turns%rowtype;
  v_res   public.battle_results%rowtype;
  v_ulti  public.battle_ultis%rowtype;
  v_msp   public.social_profile%rowtype;  -- 0010: perfil social de quien mira
  v_rsp   public.social_profile%rowtype;  -- 0010: perfil social del rival
begin
  select * into v_b from public.battles where id = p_battle;
  v_seat  := case when v_b.player1_id = p_user then 1 else 2 end;
  v_rival := case when v_seat = 1 then v_b.player2_id else v_b.player1_id end;
  select * into v_msp from public.social_profile where user_id = p_user;   -- 0010
  select * into v_rsp from public.social_profile where user_id = v_rival;  -- 0010

  v_out := jsonb_build_object(
    'battle_id', v_b.id,
    'mode', v_b.mode,
    'status', v_b.status,
    'version', v_b.version,
    'server_now', v_now,
    'my_seat', v_seat,
    'turn_number', v_b.turn_number,
    'me', jsonb_build_object(
      'hp', case when v_seat = 1 then v_b.hp1 else v_b.hp2 end,
      'correct_streak', case when v_seat = 1 then v_b.correct_streak1 else v_b.correct_streak2 end,
      'ulti_charged', case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end,
      'avatar_key', case when v_msp.avatar_hidden then null else v_msp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_msp.avatar_hidden then null else v_msp.avatar_version end       -- 0010, 0011
    ),
    'rival', jsonb_build_object(
      'username', public.sx_username(v_rival),
      'hp', case when v_seat = 1 then v_b.hp2 else v_b.hp1 end,
      'seconds_since_seen', round(extract(epoch from (v_now - case when v_seat = 1 then v_b.last_seen2 else v_b.last_seen1 end))::numeric, 1),
      'avatar_key', case when v_rsp.avatar_hidden then null else v_rsp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_rsp.avatar_hidden then null else v_rsp.avatar_version end       -- 0010, 0011
    ),
    'end_reason', v_b.end_reason
  );

  if v_b.status = 'active' then
    v_out := v_out || jsonb_build_object(
      'turn', jsonb_build_object(
        'is_mine', v_b.turn_seat = v_seat,
        'phase', v_b.turn_phase,
        'seconds_left', greatest(0, round(extract(epoch from (v_b.turn_deadline - v_now))::numeric, 1))
      )
    );

    if v_b.turn_phase = 'question' and v_b.turn_seat = v_seat then
      select * into v_q from public.battle_questions where id = v_b.current_question_id;
      v_out := v_out || jsonb_build_object(
        'question', jsonb_build_object(
          'id', v_q.id,
          'text', v_q.question,
          'options', jsonb_build_object('A', v_q.option_a, 'B', v_q.option_b, 'C', v_q.option_c, 'D', v_q.option_d)
        )
      );
    end if;

    if v_b.turn_phase = 'ulti' then
      select * into v_u from public.battle_ultis
       where battle_id = p_battle and resolved_at is null
       order by id desc limit 1;
      if found then
        v_is_att := v_u.attacker_seat = v_seat;
        select c.objective_title into v_title
          from public.ulti_challenges c
         where c.id = case when v_is_att then v_u.attack_challenge_id else v_u.defense_challenge_id end;
        v_out := v_out || jsonb_build_object(
          'ulti', jsonb_build_object(
            'role', case when v_is_att then 'attacker' else 'defender' end,
            'objective', v_title,
            'attempts_left', 3 - case when v_is_att then v_u.attacker_attempts else v_u.defender_attempts end,
            'my_result', case when v_is_att then v_u.attacker_result else v_u.defender_result end
          )
        );
      end if;
    end if;
  end if;

  select * into v_last from public.battle_turns where battle_id = p_battle order by turn_number desc limit 1;
  if found then
    v_out := v_out || jsonb_build_object(
      'last_turn', jsonb_build_object(
        'number', v_last.turn_number,
        'by_me', v_last.seat = v_seat,
        'outcome', v_last.outcome,
        'damage', v_last.damage
      )
    );
    if v_last.outcome = 'ulti' then
      select * into v_ulti from public.battle_ultis
       where battle_id = p_battle and turn_number = v_last.turn_number;
      if found then
        v_out := v_out || jsonb_build_object(
          'last_ulti', jsonb_build_object(
            'shield', v_ulti.shield,
            'attacker_result', v_ulti.attacker_result,
            'defender_result', v_ulti.defender_result
          )
        );
      end if;
    end if;
  end if;

  if v_b.status <> 'active' then
    select * into v_res from public.battle_results where battle_id = p_battle and user_id = p_user;
    if found then
      v_out := v_out || jsonb_build_object(
        'result', jsonb_build_object(
          'outcome', v_res.result,
          'xp', v_res.xp_awarded,
          'hp_left', v_res.hp_left,
          'xp_total', public.sx_xp(p_user),
          'level', public.sx_user_level(p_user),
          'weak_topics', coalesce((
            select jsonb_agg(w.topic order by w.misses desc, w.topic)
            from (
              select q.topic, count(*) as misses
              from public.battle_turns t
              join public.battle_questions q on q.id = t.question_id
              where t.battle_id = p_battle and t.seat = v_seat and t.outcome in ('incorrect', 'timeout')
              group by q.topic
              order by count(*) desc, q.topic
              limit 2
            ) w
          ), '[]'::jsonb)
        )
      );
    end if;
  end if;

  return v_out;
end;
$$;

-- 3. battle_sync de 0008
create or replace function public.battle_sync(p_battle uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := (select auth.uid());
  v_b  public.battles%rowtype;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;

  if v_b.status = 'active' then
    update public.battles
       set last_seen1 = case when player1_id = v_me then clock_timestamp() else last_seen1 end,
           last_seen2 = case when player2_id = v_me then clock_timestamp() else last_seen2 end
     where id = p_battle;
    perform public.sx_battle_resolve(p_battle);
  end if;

  return public.sx_battle_state(p_battle, v_me);
end;
$$;

-- 4. battle_play de 0008
create or replace function public.battle_play(p_battle uuid, p_action text, p_turn integer default null, p_arg text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me     uuid := (select auth.uid());
  v_b      public.battles%rowtype;
  v_seat   integer;
  v_q      public.battle_questions%rowtype;
  v_correct boolean;
  v_damage integer;
  v_u      public.battle_ultis%rowtype;
  v_ch     public.ulti_challenges%rowtype;
  v_att    integer;
  v_def    integer;
  v_t      timestamptz;
  v_is_att boolean;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('answer', 'use_ulti', 'ulti_submit', 'abandon') then
    raise exception 'bad_action';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;
  if v_b.status <> 'active' then
    raise exception 'battle_over';
  end if;
  v_seat := case when v_b.player1_id = v_me then 1 else 2 end;

  update public.battles
     set last_seen1 = case when v_seat = 1 then clock_timestamp() else last_seen1 end,
         last_seen2 = case when v_seat = 2 then clock_timestamp() else last_seen2 end
   where id = p_battle;
  perform public.sx_battle_resolve(p_battle);

  select * into v_b from public.battles where id = p_battle;
  if v_b.status <> 'active' then
    return public.sx_battle_state(p_battle, v_me);
  end if;

  if p_action = 'abandon' then
    perform public.sx_battle_finish(p_battle, case when v_seat = 1 then 2 else 1 end, 'abandon');

  elsif p_action = 'answer' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if p_turn is distinct from v_b.turn_number then
      raise exception 'stale_turn';
    end if;
    if p_arg is null or upper(p_arg) not in ('A', 'B', 'C', 'D') then
      raise exception 'bad_option';
    end if;

    select * into v_q from public.battle_questions where id = v_b.current_question_id;
    v_correct := v_q.correct = upper(p_arg);
    v_damage  := case when v_correct then 20 else 0 end;

    insert into public.battle_turns (battle_id, turn_number, seat, question_id, outcome, chosen_option, damage)
    values (p_battle, v_b.turn_number, v_seat, v_q.id, case when v_correct then 'correct' else 'incorrect' end, upper(p_arg), v_damage);

    update public.battles
       set hp1             = case when v_seat = 2 then greatest(0, hp1 - v_damage) else hp1 end,
           hp2             = case when v_seat = 1 then greatest(0, hp2 - v_damage) else hp2 end,
           correct_streak1 = case when v_seat = 1 then (case when v_correct then least(3, correct_streak1 + 1) else 0 end) else correct_streak1 end,
           correct_streak2 = case when v_seat = 2 then (case when v_correct then least(3, correct_streak2 + 1) else 0 end) else correct_streak2 end,
           ulti_charged1   = case when v_seat = 1 and v_correct and correct_streak1 + 1 >= 3 then true else ulti_charged1 end,
           ulti_charged2   = case when v_seat = 2 and v_correct and correct_streak2 + 1 >= 3 then true else ulti_charged2 end,
           version         = version + 1
     where id = p_battle;

    select * into v_b from public.battles where id = p_battle;
    if (v_seat = 1 and v_b.hp2 = 0) or (v_seat = 2 and v_b.hp1 = 0) then
      perform public.sx_battle_finish(p_battle, v_seat, 'hp_zero');
    else
      perform public.sx_advance_turn(p_battle);
    end if;

  elsif p_action = 'use_ulti' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if not (case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end) then
      raise exception 'ulti_not_charged';
    end if;

    select c.id into v_att from public.ulti_challenges c where c.kind = 'attack' and c.active order by random() limit 1;
    select c.id into v_def from public.ulti_challenges c where c.kind = 'defense' and c.active order by random() limit 1;
    if v_att is null or v_def is null then
      raise exception 'bank_empty';
    end if;

    v_t := clock_timestamp();
    insert into public.battle_ultis (battle_id, turn_number, attacker_seat, attack_challenge_id, defense_challenge_id, started_at, deadline)
    values (p_battle, v_b.turn_number, v_seat, v_att, v_def, v_t, v_t + interval '2 minutes');

    update public.battles
       set turn_phase = 'ulti', turn_deadline = v_t + interval '2 minutes', current_question_id = null, version = version + 1
     where id = p_battle;

  elsif p_action = 'ulti_submit' then
    if v_b.turn_phase <> 'ulti' then
      raise exception 'no_ulti';
    end if;
    select * into v_u from public.battle_ultis
     where battle_id = p_battle and resolved_at is null
     order by id desc limit 1 for update;
    if not found then
      raise exception 'no_ulti';
    end if;
    if p_arg is null or char_length(p_arg) > 200 then
      raise exception 'bad_command';
    end if;

    v_t := clock_timestamp();
    v_is_att := v_u.attacker_seat = v_seat;

    if v_is_att then
      if v_u.attacker_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.attack_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'solved', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      elsif v_u.attacker_attempts + 1 >= 3 then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'failed', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      else
        update public.battle_ultis set attacker_attempts = attacker_attempts + 1 where id = v_u.id;
      end if;
    else
      if v_u.defender_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.defense_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'solved', defender_at = v_t
         where id = v_u.id;
      elsif v_u.defender_attempts + 1 >= 3 then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'failed', defender_at = v_t
         where id = v_u.id;
      else
        update public.battle_ultis set defender_attempts = defender_attempts + 1 where id = v_u.id;
      end if;
    end if;
  end if;

  return public.sx_battle_state(p_battle, v_me);
end;
$$;

-- 5. Permisos, como en 0008
revoke all on function public.battle_sync(uuid) from public, anon;
grant execute on function public.battle_sync(uuid) to authenticated;
revoke all on function public.battle_play(uuid, text, integer, text) from public, anon;
grant execute on function public.battle_play(uuid, text, integer, text) to authenticated;
revoke all on function public.sx_battle_state(uuid, uuid) from public, anon, authenticated;

-- 6. Quitar las tablas de traducciones (con sus datos)
drop table if exists public.battle_question_translations;
drop table if exists public.ulti_challenge_translations;

commit;
