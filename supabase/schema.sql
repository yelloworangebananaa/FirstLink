-- FirstLink schema for Supabase. Paste this whole file into the SQL editor and Run.
-- Before running: Authentication -> Sign In / Providers -> enable "Allow anonymous sign-ins".
-- Field meanings: see GLOSSARY.md. Columns are snake_case versions of the app's fields.

create table if not exists events (
  id uuid primary key default gen_random_uuid(),
  organizer_uid uuid not null default auth.uid(),
  name text not null default '',
  venue_name text not null default '',
  organizer_name text not null default '',
  require_911_approval boolean not null default false,
  attendee_code text unique,
  staff_code text unique,
  locations text[] not null default '{}',
  active boolean not null default true,
  aed_locations text[] not null default '{}',
  first_aid_locations text[] not null default '{}',
  responder_posts jsonb not null default '{}'
);

-- One row per phone: id is the anonymous auth uid, so a phone is in one event at a time.
create table if not exists users (
  id uuid primary key default auth.uid(),
  event_id uuid references events on delete cascade,
  role text not null default 'attendee',
  name text,
  qualifications text[] not null default '{}',
  qualified_responder boolean not null default false,
  contact_for text
);

create table if not exists emergencies (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references events on delete cascade,
  reporter_uid uuid default auth.uid(),
  reporter_role text,
  reporter_name text,
  call_approval_status text not null default 'Not Requested',
  location text not null default '',
  description text not null default '',
  consciousness text not null default '',
  breathing text not null default '',
  photo text,
  status text not null default 'Active',
  accepted_by text[] not null default '{}',
  accepted_uids uuid[] not null default '{}',
  created_at timestamptz not null default now()
);

create table if not exists help_requests (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references events on delete cascade,
  requester_uid uuid default auth.uid(),
  location text not null default '',
  description text not null default '',
  problem text,
  photo text,
  status text not null default 'Open',
  accepted_by text[] not null default '{}',
  accepted_uids uuid[] not null default '{}',
  created_at timestamptz not null default now()
);

create table if not exists contacts (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references events on delete cascade,
  problem text not null,
  person text not null,
  code text not null unique
);

-- Live updates (replaces Firestore snapshot listeners).
alter publication supabase_realtime add table events, users, emergencies, help_requests, contacts;

-- ------------------------------------------------------------------ helpers

-- True when the caller organizes or has joined the event. Security definer so the
-- policies below can check membership without recursing through users' own policy.
create or replace function is_member(eid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from events where id = eid and organizer_uid = auth.uid())
      or exists (select 1 from users where id = auth.uid() and event_id = eid);
$$;

create or replace function is_organizer(eid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from events where id = eid and organizer_uid = auth.uid());
$$;

-- Join by code. Runs as definer, so nobody needs read access to other events' codes
-- (closes the code-enumeration hole in FIREBASE_SETUP.md). Same precedence as the
-- apps: staff code, then attendee code, then an Important People contact code.
create or replace function redeem_code(join_code text)
returns table (event_id uuid, role text, contact_for text)
language sql stable security definer set search_path = public as $$
  select r.event_id, r.role, r.contact_for from (
    select e.id as event_id, 'staff' as role, null::text as contact_for, 1 as rank
      from events e where e.staff_code = upper(trim(join_code)) and e.active
    union all
    select e.id, 'attendee', null, 2
      from events e where e.attendee_code = upper(trim(join_code)) and e.active
    union all
    select c.event_id, 'staff', c.problem, 3
      from contacts c join events e on e.id = c.event_id
      where c.code = upper(trim(join_code)) and e.active
  ) r
  order by r.rank
  limit 1;
$$;

-- Accept is additive and idempotent per uid, in one atomic statement.
create or replace function accept_emergency(report_id uuid, who text) returns void
language sql security invoker as $$
  update emergencies
     set status = 'Responder Assigned',
         accepted_by = accepted_by || who,
         accepted_uids = accepted_uids || auth.uid()
   where id = report_id and not (auth.uid() = any (accepted_uids));
$$;

create or replace function accept_help_request(report_id uuid, who text) returns void
language sql security invoker as $$
  update help_requests
     set status = 'In Progress',
         accepted_by = accepted_by || who,
         accepted_uids = accepted_uids || auth.uid()
   where id = report_id and not (auth.uid() = any (accepted_uids));
$$;

-- ------------------------------------------------------- row level security
-- The anon key ships inside the apps, so without these anyone could read everything.

alter table events enable row level security;
alter table users enable row level security;
alter table emergencies enable row level security;
alter table help_requests enable row level security;
alter table contacts enable row level security;

-- The organizer check is on the row itself, not via is_member(): an insert that returns
-- the new row is checked before is_member()'s own query can see that row.
create policy "members read event" on events
  for select using (organizer_uid = auth.uid() or is_member(id));
create policy "organizer creates event" on events
  for insert with check (organizer_uid = auth.uid());
create policy "organizer edits event" on events
  for update using (organizer_uid = auth.uid());

create policy "members read people" on users
  for select using (id = auth.uid() or is_member(event_id));
create policy "own profile insert" on users
  for insert with check (id = auth.uid());
create policy "own profile update" on users
  for update using (id = auth.uid());

create policy "members read emergencies" on emergencies
  for select using (is_member(event_id));
create policy "members report emergencies" on emergencies
  for insert with check (is_member(event_id) and reporter_uid = auth.uid()
    and exists (select 1 from events e where e.id = event_id and e.active));
create policy "members update emergencies" on emergencies
  for update using (is_member(event_id));

create policy "members read help" on help_requests
  for select using (is_member(event_id));
create policy "members request help" on help_requests
  for insert with check (is_member(event_id) and requester_uid = auth.uid()
    and exists (select 1 from events e where e.id = event_id and e.active));
create policy "members update help" on help_requests
  for update using (is_member(event_id));

create policy "members read contacts" on contacts
  for select using (is_member(event_id));
create policy "organizer manages contacts" on contacts
  for all using (is_organizer(event_id)) with check (is_organizer(event_id));

-- ReadyMap edits touch one entry each, so two quick taps can't overwrite each other.
-- Security invoker: the "organizer edits event" policy still decides who may change it.
create or replace function set_equipment(eid uuid, kind text, place text, present boolean)
returns void language plpgsql security invoker as $$
begin
  if kind = 'aed' then
    update events
       set aed_locations = array_remove(aed_locations, place)
                           || case when present then array[place] else '{}'::text[] end
     where id = eid;
  elsif kind = 'first_aid' then
    update events
       set first_aid_locations = array_remove(first_aid_locations, place)
                                 || case when present then array[place] else '{}'::text[] end
     where id = eid;
  else
    raise exception 'unknown equipment kind: %', kind;
  end if;
end;
$$;

-- Posts a responder at a place, or clears their post when place is null.
create or replace function set_post(eid uuid, responder text, place text)
returns void language sql security invoker as $$
  update events
     set responder_posts = case when place is null then responder_posts - responder
                                else jsonb_set(responder_posts, array[responder], to_jsonb(place)) end
   where id = eid;
$$;
