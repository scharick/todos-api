-- Migración: tabla todos
-- Fuente: docs/datos/modelo.md y SPECS.md §3 (reglas 1 a 7)

-- 1. Tabla -----------------------------------------------------------------

create table public.todos (
  -- Reglas 3, 4 y 7: lo asigna la BD, es único y no se puede cambiar
  -- (GENERATED ALWAYS rechaza un id enviado por el cliente y un UPDATE del id).
  id          bigint generated always as identity primary key,

  -- Reglas 1 y 2: obligatorio, máximo 100, ya recortado y no vacío.
  title       varchar(100) not null,

  -- Regla 2: opcional, máximo 500; vacío se guarda como NULL (modelo, pregunta 4).
  description varchar(500),

  -- Regla 6: nunca NULL.
  completed   boolean not null default false,

  -- Reglas 4 y 5: lo asigna la app (modelo, pregunta 10); el trigger de abajo
  -- impide fechas futuras y que cambie después de crearse.
  created_at  timestamptz not null,

  constraint todos_title_no_vacio
    check (char_length(title) >= 1),
  constraint todos_title_recortado
    check (title = btrim(title)),
  constraint todos_description_no_vacia
    check (description is null or btrim(description) <> '')
);

comment on table public.todos is
  'Tareas del MVP. Dueño de la fila: la aplicación (backend Spring Boot). Sin usuarios.';

-- 2. Protección de created_at (reglas 4 y 5) -------------------------------
-- No se usa un CHECK con now() porque los CHECK deben ser inmutables.
-- Se tolera 1 minuto de diferencia entre el reloj de la app y el de la BD.

create function public.todos_proteger_created_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' and new.created_at > now() + interval '1 minute' then
    raise exception 'created_at no puede ser una fecha futura'
      using errcode = 'check_violation';
  end if;

  if tg_op = 'UPDATE' and new.created_at is distinct from old.created_at then
    raise exception 'created_at no se puede modificar'
      using errcode = 'check_violation';
  end if;

  return new;
end;
$$;

revoke execute on function public.todos_proteger_created_at() from public, anon, authenticated;

create trigger todos_proteger_created_at
  before insert or update on public.todos
  for each row execute function public.todos_proteger_created_at();

-- 3. Seguridad: RLS y permisos ----------------------------------------------
-- Dueño de la fila: la aplicación (modelo, pregunta 11).
-- El backend Spring Boot se conecta directo a Postgres como dueño de la tabla,
-- y el dueño no pasa por RLS. Los roles de la Data API de Supabase
-- (anon y authenticated) NO son dueños de ninguna fila: por eso no se crea
-- ninguna política para ellos (con RLS activo, sin política = sin acceso)
-- y además se les quitan los permisos.

alter table public.todos enable row level security;

revoke all on table public.todos from anon, authenticated;
