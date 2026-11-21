-- Consultas de verificación de la tabla todos
-- Ejecutar CADA BLOQUE POR SEPARADO (SQL Editor de Supabase o MCP):
-- las pruebas de la sección B deben fallar, y un error detiene el resto del script.
-- Ninguna consulta deja datos nuevos: las que deben fallar no insertan nada.

-- =========================================================================
-- A. Consultas que deben funcionar
-- =========================================================================

-- A1. Datos semilla
select id, title, description, completed, created_at
from public.todos
order by id;
-- Esperado: 3 filas (ids 1, 2 y 3).

-- A2. Estado de los datos: todo lo "inválido" debe dar 0
select
  (select count(*) from public.todos)                                        as total,
  (select count(*) from public.todos where completed)                        as completadas,
  (select count(*) from public.todos where not completed)                    as pendientes,
  (select count(*) from public.todos where description is null)              as sin_descripcion,
  (select count(*) from public.todos where title <> btrim(title) or title = '') as titulos_invalidos,
  (select count(*) from public.todos where created_at > now())               as fechas_futuras,
  (select count(*) - count(distinct id) from public.todos)                   as ids_repetidos;
-- Esperado: 3 | 1 | 2 | 1 | 0 | 0 | 0

-- A3. Restricciones activas
select conname as restriccion,
  case contype when 'p' then 'PRIMARY KEY' when 'u' then 'UNIQUE'
               when 'c' then 'CHECK' when 'f' then 'FOREIGN KEY' end as tipo,
  pg_get_constraintdef(oid) as definicion
from pg_constraint
where conrelid = 'public.todos'::regclass
order by contype, conname;
-- Esperado: todos_pkey y los 3 CHECK de la migración.

-- A4. RLS y permisos de los roles públicos
select c.relname as tabla,
  c.relrowsecurity as rls_activo,
  (select count(*) from pg_policies p
    where p.schemaname = 'public' and p.tablename = c.relname) as politicas,
  has_table_privilege('anon', c.oid, 'select')          as anon_puede_leer,
  has_table_privilege('authenticated', c.oid, 'select') as authenticated_puede_leer
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r';
-- Esperado: todos | true | 0 | false | false

-- =========================================================================
-- B. Pruebas que DEBEN FALLAR (cada una viola una regla de SPECS §3)
-- =========================================================================

-- B1. Id repetido (regla 3), el equivalente a la "doble reserva".
--     OVERRIDING SYSTEM VALUE se salta GENERATED ALWAYS para probar la PK.
insert into public.todos (id, title, created_at)
overriding system value values (1, 'Tarea duplicada', now());
-- Esperado: ERROR 23505 duplicate key value violates unique constraint "todos_pkey"

-- B2. Título solo con espacios (regla 1)
insert into public.todos (title, created_at) values ('   ', now());
-- Esperado: ERROR 23514 violates check constraint "todos_title_recortado"

-- B3. Título de 101 caracteres (regla 2)
insert into public.todos (title, created_at) values (repeat('a', 101), now());
-- Esperado: ERROR 22001 value too long for type character varying(100)

-- B4. El cliente envía el id (regla 4)
insert into public.todos (id, title, created_at) values (500, 'Con id del cliente', now());
-- Esperado: ERROR 428C9 cannot insert a non-DEFAULT value into column "id"

-- B5. Fecha de creación futura (regla 5)
insert into public.todos (title, created_at) values ('Tarea del futuro', now() + interval '1 day');
-- Esperado: ERROR 23514 created_at no puede ser una fecha futura

-- B6. Modificar created_at (regla 5)
update public.todos set created_at = now() - interval '10 days' where id = 1;
-- Esperado: ERROR 23514 created_at no se puede modificar

-- B7. Lectura con la llave pública (dueño de la fila = la aplicación)
set role anon;
select * from public.todos;
-- Esperado: ERROR 42501 permission denied for table todos
reset role;
-- Si se ejecuta en el SQL Editor, correr también "reset role;" aunque B7 falle.

-- =========================================================================
-- C. Advisors de seguridad
-- =========================================================================
-- No es SQL: se ejecutan desde el dashboard (Advisors → Security Advisor)
-- o con la herramienta get_advisors del MCP.
-- Esperado: solo "rls_enabled_no_policy" (INFO) en public.todos, intencional.
