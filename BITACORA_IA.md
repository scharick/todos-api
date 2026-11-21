# Bitácora de uso de IA

Asistente: Claude Code (Claude Opus 5.5), en VS Code.
Proyecto: API de Tareas (Todos). Backend Spring Boot 4.1.1 (Java 21) en Render, BD PostgreSQL en Supabase y un front pequeño en Vercel.

> **Cómo trabajamos:** en varios pasos le dijimos a la IA que aceptábamos lo que ella considerara mejor. Por eso la mayoría de las decisiones las propuso la IA y las aceptamos sin cambios. Las correcciones de este registro salieron de revisar el trabajo con el **checklist del interventor** y con el **prompt del profesor**, no de cambios manuales nuestros.

## Prompts clave

| # | Prompt (resumido) | Origen | Resultado |
|---|-------------------|--------|-----------|
| 1 | "El profe me mandó hacer un backend y desplegarlo… primero vamos a hacer las specs" | Nosotros | [SPECS.md](SPECS.md) |
| 2 | "Releer las specs. Subrayen los sustantivos (entidades) y escriban al margen lo que nunca puede pasar" | Lab | Entidad `Todo` y las 10 reglas de SPECS §3 |
| 3 | "Entrevista de datos: la IA pregunta, ustedes deciden. Resultado: `docs/datos/modelo.md` con ER en Mermaid" | Lab | [docs/datos/modelo.md](docs/datos/modelo.md) |
| 4 | "Pasen el checklist del interventor. Lo que no pase se corrige antes de seguir" | Lab | 4 puntos corregidos (Lab 4) |
| 5 | "Con el modelo aprobado y las specs, escribe `docs/contratos/api.md`… errores 400, 401, 404, 409… Luego agrega 'Criterios de aceptación del back' en Dado/Cuando/Entonces" | Profesor | [docs/contratos/api.md](docs/contratos/api.md) y SPECS §12 |
| 6 | "Usa el MCP de Supabase… crea UNA migración con tablas, FK, UNIQUE, CHECK, RLS… muéstrame el SQL y ESPERA mi aprobación" | Profesor (prompt 3) | [Migración](supabase/migrations/20261120000000_crear_tabla_todos.sql) y [seed](supabase/seed.sql) |
| 7 | "Consultas de comprobación, incluida la doble reserva que debe fallar, y los advisors de seguridad" | Lab | [supabase/verificacion.sql](supabase/verificacion.sql) |
| 8 | "Entregables de la unidad…" y "mantener Spring Boot y agregar un front pequeño en Vercel" | Lab + nosotros | Lab 9: [CLAUDE.md](CLAUDE.md), skill `backend-seguro`, reglas por capa |

## Fugas que dejó la IA y cómo las cerramos

"Fuga" = un hueco de seguridad que quedó en una propuesta o en la configuración.

| ID | Fuga | Origen | Riesgo | Cómo se cerró | Estado |
|----|------|--------|--------|---------------|--------|
| F1 | CORS para **cualquier origen** (SPECS §8) | IA, Lab 1 | Cualquier sitio web podía llamar a la API desde el navegador de un usuario | Solo los orígenes de `CORS_ORIGINS` (Vercel y localhost), nunca `*` | Cerrada en specs; se verifica en la auditoría |
| F2 | Consola H2 habilitada (`spring.h2.console.enabled=true`) | IA, Lab 1 | En producción habría expuesto una consola web de la BD | Se eliminó al pasar a PostgreSQL de Supabase | Cerrada |
| F3 | `ddl-auto=update` | IA, Lab 1 | Hibernate podía cambiar el esquema en producción y saltarse RLS y los `CHECK` de la migración | `ddl-auto=validate`; el esquema solo cambia con migraciones | Cerrada |
| F4 | Conector MCP con permiso de escritura | Proceso, Lab 7: quitamos `read_only` para aplicar la migración y no lo restauramos | La IA podía modificar o borrar datos de Supabase sin necesidad | Se restauró `read_only=true` en `.mcp.json` | Cerrada |
| F5 | `.env` no estaba en `.gitignore` | IA, Lab 7: pasó a variables de entorno sin protegerlas | Un `.env` con la contraseña de la BD podía subirse a GitHub | Se agregaron `.env` y `.env.*` a `.gitignore` | Cerrada |
| F6 | Los errores de Postgres revelan nombres de restricciones, columnas y tipos (visto en las pruebas del Lab 7) | Comportamiento por defecto | Si el backend los reenvía, el usuario ve detalles internos | Regla 10, `GlobalExceptionHandler` (modelo §7) y skill `backend-seguro` §4 | Cerrada en el diseño; falta comprobarla en el código y en la auditoría |
| — | Advisor `rls_enabled_no_policy` (INFO) | Advisor de Supabase | Ninguno: es intencional | Justificada: el dueño de las filas es la aplicación y `anon` no tiene acceso (verificado en el Lab 8) | Justificada |
| — | `.mcp.json` en git con el `project_ref` | IA, Lab 6 | Bajo: el `project_ref` no es un secreto y el acceso exige iniciar sesión | Justificada: no tiene llaves ni tokens | Justificada |

## Lab 1 — Specs del proyecto

**Qué propuso la IA**
- Una API REST de tareas con 6 endpoints en `/api/todos` (listar, ver, crear, editar, marcar como completada, eliminar).
- H2 en memoria para no instalar ninguna base de datos, y despliegue con Docker en Render (plan gratuito).
- Un formato único de error (`status`, `error`, `message`, `timestamp`).
- Dejar fuera de alcance el login, PostgreSQL y el frontend.

**Qué corregimos:** nada; aceptamos las specs tal cual ([SPECS.md](SPECS.md)).

## Lab 2 — Entidades y reglas

**Qué propuso la IA**
- Una sola entidad: `Todo`. Título, descripción, estado y fecha son atributos, no entidades.
- 10 reglas de "lo que nunca puede pasar" (título obligatorio, longitudes máximas, el cliente no elige `id` ni `createdAt`, `404` para tareas inexistentes, errores sin detalles internos…).
- 4 preguntas abiertas, cada una con su propuesta: se permiten títulos repetidos, se puede editar una tarea completada, borrar dos veces devuelve `404`, se recortan los espacios del título.

**Qué corregimos:** nada; aceptamos las 4 propuestas y la IA agregó las reglas a SPECS §3.

## Lab 3 — Entrevista de datos y modelo

**Qué propuso la IA:** [docs/datos/modelo.md](docs/datos/modelo.md) con el diagrama ER en Mermaid, el diccionario de datos y 10 decisiones (id `BIGINT` autoincremental, borrado físico, sin `updated_at`, sin índices extra, descripción vacía = `NULL`…).

**Qué corregimos** (primero con un checklist genérico que propuso la IA, porque aún no teníamos el del interventor):
- `created_at` aparecía con valor por defecto en la BD y, al mismo tiempo, como asignado por la app. Quedó solo la app.
- Faltaba explicar cómo se cumplen las reglas 8 y 10.
- Faltaba la equivalencia de nombres `created_at` (BD) ↔ `createdAt` (API).
- `varchar(100)` podía romper el diagrama Mermaid; las longitudes pasaron a los comentarios.
- Faltaban los datos de ejemplo para la demo.

## Lab 4 — Revisión con el checklist del interventor

**Qué falló y cómo se corrigió**

| Punto del checklist | Problema en la propuesta de la IA | Corrección |
|---------------------|-----------------------------------|------------|
| Fechas como `date`/`timestamptz` | `created_at` era `TIMESTAMP` sin zona (`LocalDateTime`) | Ahora es `timestamptz` / `OffsetDateTime`; los ejemplos usan `-05:00` |
| Cada tabla dice quién es el dueño de la fila | No se mencionaba | Se documentó: la aplicación (lista única compartida, sin usuarios) |
| Cada acción tiene su operación en el contrato | No existía el mapeo | Tabla "Acción → operación" |
| Cada operación lista sus errores con código y mensaje | Solo había una tabla de errores general | Tabla de errores por operación |

Los otros 4 puntos (`id` y `created_at` en cada tabla, FK en las relaciones, `UNIQUE` en las reglas "no puede haber dos…" y ningún campo sin uso) ya pasaban.

## Lab 5 — Contrato de la API y criterios del back

**Qué propuso la IA:** [docs/contratos/api.md](docs/contratos/api.md) con 6 operaciones (entrada, validaciones, respuesta con JSON de ejemplo y errores con código y mensaje) y la sección 12 de las specs con 23 criterios Dado / Cuando / Entonces.

**Qué corregimos o adaptamos**
- **Stack:** el prompt del profesor pedía Route Handlers de Next.js, pero el proyecto es Spring Boot. Se mantuvo Spring Boot y se dejó una nota en el contrato; el contrato HTTP es el mismo.
- **Códigos `401` y `409`:** el prompt los pedía, pero no aplican (no hay login ni conflictos de unicidad o de estado). Se documentó por qué en lugar de inventar casos.
- **Mensaje de error:** `El cuerpo de la petición no es un JSON válido` cambió a `El cuerpo de la petición no es válido`, porque también cubre los tipos incorrectos (ej. `"completed": "si"`).
- **Duplicación:** la tabla de errores de SPECS §4 se reemplazó por un enlace al contrato, que quedó como fuente oficial.

## Lab 6 — Conector MCP de Supabase

**Qué nos dieron:** `claude mcp add --transport http supabase "https://supabase.com/dashboard/project/avinketmglmqzsaehzpt"`.

**Qué corrigió la IA**
- La URL era la del dashboard, no la del servidor MCP. Se cambió a `https://mcp.supabase.com/mcp?project_ref=avinketmglmqzsaehzpt`, limitada a un solo proyecto.
- Para dar el mínimo permiso se agregó `read_only=true`. Lo quitamos nosotros a mano cuando aprobamos la migración.
- El comando `claude` no estaba instalado (solo la extensión de VS Code), así que el conector se configuró en `.mcp.json`.

## Lab 7 — Migración en Supabase

**Qué propuso la IA**
- Una migración ([supabase/migrations/20261120000000_crear_tabla_todos.sql](supabase/migrations/20261120000000_crear_tabla_todos.sql)) con la tabla `todos`, 3 `CHECK`, `id` `GENERATED ALWAYS`, un trigger que protege `created_at` y RLS activado.
- RLS **sin políticas** y sin permisos para `anon` y `authenticated`, porque el dueño de las filas es la aplicación (el backend se conecta como dueño de la tabla).
- Datos semilla con las 3 tareas del modelo ([supabase/seed.sql](supabase/seed.sql)).

**Qué adaptamos o corregimos**
- El prompt del profesor hablaba de canchas y reservas; se adaptó a nuestra única tabla `todos`. No hay FK ni `UNIQUE` extra porque no hay relaciones y se permiten títulos repetidos.
- La skill `backend-seguro` no estaba instalada; se siguieron las reglas de seguridad de Supabase.
- La IA no usó un `CHECK` con `now()` para la regla "nunca futura" (Postgres no lo recomienda) y lo hizo con un trigger con 1 minuto de tolerancia.
- Leímos el SQL con el checklist del interventor y lo aprobamos sin cambios.

**Resultados**
- Los 5 intentos que violaban reglas fallaron: título vacío, título de 101 caracteres, `id` enviado por el cliente, fecha futura y cambio de `created_at`.
- RLS activo en `todos`; `anon` y `authenticated` no pueden leer.
- Advisor de seguridad: un solo aviso INFO, `rls_enabled_no_policy`. Es intencional (ver arriba).

**Cambios derivados:** las specs y el modelo pasaron de H2 a PostgreSQL de Supabase. La conexión se configura con variables de entorno, `ddl-auto=validate`, y las tareas de ejemplo salen de `seed.sql` en lugar de cargarse al arrancar.

## Lab 8 — Datos semilla y verificación

**Datos semilla:** el prompt pedía "3 canchas y algunas reservas". Como nuestro MVP solo tiene la tabla `todos`, el equivalente son las 3 tareas que ya se cargaron en el Lab 7: 1 completada, 2 pendientes y 1 sin descripción, que alcanzan para probar el filtro y el `null`.

**Qué propuso la IA:** como "doble reserva" usar la única regla "no puede haber dos" que tenemos, dos tareas con el mismo `id`. La prueba fuerza el `id` repetido con `OVERRIDING SYSTEM VALUE`, para comprobar la clave primaria y no solo `GENERATED ALWAYS`. Las consultas quedaron en [supabase/verificacion.sql](supabase/verificacion.sql).

**Resultados**

| Comprobación | Resultado |
|--------------|-----------|
| `id` repetido (la "doble reserva") | Falló: `23505 duplicate key ... "todos_pkey"` |
| Estado de los datos | 3 tareas; 0 títulos inválidos, 0 fechas futuras, 0 ids repetidos |
| Restricciones activas | `todos_pkey` y los 3 `CHECK` de la migración |
| Lectura con la llave pública (`anon`) | Falló: `42501 permission denied for table todos` |
| Advisor de seguridad | Solo `rls_enabled_no_policy` (INFO), intencional; ningún WARN ni ERROR |

**Qué corregimos:** nada; los resultados coinciden con lo esperado.

## Lab 9 — Revisión de entregables

**Qué propuso la IA:** comparar la lista de entregables con el repo. Faltaban la tabla "regla → capa", `CLAUDE.md`, la skill `backend-seguro` y las secciones de prompts y fugas de esta bitácora. Además, la unidad asume Next.js en Vercel. La IA propuso dos caminos: (A) mantener Spring Boot en Render y agregar un front pequeño en Vercel, o (B) rehacer todo en Next.js.

**Qué decidimos:** opción A.

**Qué se hizo**
- `docs/datos/modelo.md` §7: capa principal y capa de respaldo de cada regla.
- `CLAUDE.md` con la arquitectura, las fuentes de verdad y la sección **Backend**.
- Skill `.claude/skills/backend-seguro/SKILL.md`: checklist de seguridad en 8 grupos. La IA la escribió porque no teníamos una del curso.
- Esta bitácora: secciones "Prompts clave" y "Fugas".
- SPECS: el front en Vercel entra al alcance y CORS se limita a `CORS_ORIGINS` (F1).
- Fugas F4 y F5 cerradas.

## Pendiente de revisar por nosotros

- Confirmar con el profesor la arquitectura: Spring Boot en Render y un front pequeño en Vercel, en lugar de Next.js.
- Validar el diagrama Mermaid en VS Code o GitHub.
