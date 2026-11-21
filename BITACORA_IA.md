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

## Lab 10 — Backend y front conectados a Supabase

**Prompt:** "Los entregables de hoy son todo el backend, conectado con la base de datos y el frontend".

**Qué hizo la IA**
- Backend Spring Boot según el contrato: entidad, DTOs, servicio, controlador, `GlobalExceptionHandler` y CORS. Lo hizo aplicando la skill `backend-seguro`.
- 38 pruebas (controlador y servicio) que cubren los criterios de SPECS §12, CORS y la prueba de que un error no muestra detalles internos.
- Prueba contra Supabase real: CRUD completo, todos los errores del contrato, filtro y CORS.
- Front en `frontend/` (HTML, CSS y JS), probado en un navegador simulado (jsdom) contra el backend real: crear, error de validación, toggle, filtro, editar, eliminar y XSS.

**Qué corregimos**
- **Contrato imposible de cumplir:** decía que en `PUT` se comprueba primero la existencia (`404`) y después el cuerpo (`400`). Spring lee el JSON antes, así que se cambió el contrato a: id → cuerpo → existencia.
- **Conexión fallida:** en `.env` pusimos el host de la conexión directa (`db.<ref>.supabase.co`, solo IPv6). Se cambió al del Session pooler.
- **Fecha distinta entre `POST` y `GET`:** Java generaba 7 decimales y Postgres guarda 6. Se recorta a microsegundos.
- **Front:** `form.title` choca con la propiedad `title` de los elementos HTML. Se cambió por `form.elements.namedItem("title")`.

**Fugas nuevas y cómo se cerraron**

| ID | Fuga | Riesgo | Cómo se cerró | Estado |
|----|------|--------|---------------|--------|
| F7 | La prueba `TodosApplicationTests` de la plantilla arrancaba la app completa | `./mvnw test` se conectaba a la BD de producción | Se eliminó; las pruebas usan *mocks* y no tocan la BD | Cerrada |
| F8 | Un `@ExceptionHandler(Exception.class)` sin más manejadores convierte rutas inexistentes y métodos no permitidos en `500` | Respuestas `500` engañosas y ruido en los logs | Manejadores explícitos para `404 Recurso no encontrado`, `405` y `415` | Cerrada |
| F9 | Spring convierte `"yes"`, `"1"` y `"on"` en `true` para `?completed=` | Se aceptaban valores fuera del contrato | El parámetro se lee como texto y solo se aceptan `true`/`false` | Cerrada |
| F10 | XSS en el front si se pintan títulos con `innerHTML` | Un título con HTML podía ejecutar código en el navegador de otro usuario | Solo `textContent` + Content-Security-Policy; probado con `<img onerror>` | Cerrada |
| F11 | `CORS_ORIGINS=*` mal configurado en Render | Volvería a abrir F1 | `CorsConfig` descarta `*` aunque esté configurado | Cerrada |
| — | `spring-boot-devtools` en el `pom.xml` | Herramienta de desarrollo | Justificada: es `optional` y Spring Boot no la incluye en el `.jar` de producción | Justificada |

## Lab 11 — Despliegue en Render y Vercel

**Prompts:** "sube al repositorio" y "vamos a desplegarlo con Vercel".

**Qué corrigió la IA del pedido:** Vercel no ejecuta Java, así que el backend no puede ir ahí. Se mantuvo el plan A: el backend Spring Boot en Render (Docker) y el front en Vercel.

**Qué hizo la IA**
- Antes del primer `push`, revisó todo el historial de git en busca de secretos: no había ninguno.
- `Dockerfile` en dos etapas (la app no corre como root), `.dockerignore` (deja fuera `.env` y `.git`), `render.yaml` con los secretos marcados `sync: false`, `frontend/vercel.json` con cabeceras de seguridad y `scripts/prueba-de-fuego.sh`.
- Comprobó que el `.jar` de producción no incluye `devtools` ni el `.env`.

**URLs:** front https://todos-api-frontend.vercel.app · backend https://todos-api-hoal.onrender.com · repo https://github.com/scharick/todos-api

**Resultados**
- Primera corrida: 12 de 16 pruebas de fuego pasaron. Las 4 fallas eran porque `CORS_ORIGINS` en Render aún no tenía la URL de Vercel. Esto también prueba que CORS bloquea en producción.
- Después de corregir `CORS_ORIGINS`: **16 de 16**.
- Navegador simulado sobre la página de Vercel (crear, validar, toggle, filtro, editar, XSS, eliminar): todo pasa. La tabla quedó con sus 3 tareas.

**Fugas**

| ID | Fuga | Riesgo | Cómo se cerró | Estado |
|----|------|--------|---------------|--------|
| F12 | La CSP del front permitía conectarse a cualquier `*.onrender.com` | Un script inyectado podía enviar datos a otro servicio de Render | `connect-src` solo permite `https://todos-api-hoal.onrender.com` | Cerrada |
| F13 | Contenedor como root (lo que trae por defecto la imagen base) | Si la app es vulnerada, el atacante tiene root en el contenedor | Usuario `app` sin privilegios en el `Dockerfile` | Cerrada |
| — | El rechazo de CORS responde `403 Invalid CORS request` en texto plano, no en el formato JSON del contrato | Ninguno: no expone detalles internos | Justificada: lo responde Spring antes del controlador y solo lo ven orígenes no permitidos | Justificada |
| — | Render gratis se duerme y la primera petición tarda ~1 min | Disponibilidad, no seguridad | Justificada: el front muestra "Cargando…"; es el plan gratuito | Justificada |

## Lab 12 — Spring Security

**Prompt:** "necesito que tenga un Spring Security".

**Qué preguntó la IA y qué decidimos:** había tres opciones: (1) login con un solo usuario, (2) Spring Security sin login (solo endurecer) o (3) usuarios reales con Supabase Auth. Elegimos la **(1)**: leer es público y escribir exige HTTP Basic, con un usuario que sale de `APP_USER`/`APP_PASSWORD`.

**Qué hizo la IA**
- `SecurityConfig`: `GET` de tareas es público y todo lo demás exige login (cerrado por defecto). Sin sesión ni cookies (`STATELESS`), contraseña con BCrypt, `401` en el formato del contrato y sin `WWW-Authenticate`, y CORS dentro de Spring Security, con la cabecera `Authorization` permitida.
- La app **no arranca** si faltan `APP_USER`/`APP_PASSWORD` o si la contraseña tiene menos de 12 caracteres.
- Op. 7 `GET /api/auth/me` para que el front compruebe el login. Se actualizaron el contrato (401 en las operaciones 3 a 7), SPECS (criterios CA-S y CA-7) y la skill.
- Front: formulario de login. Sin sesión, solo lectura. La credencial vive solo en memoria.
- Pruebas: **50 pasan** (11 nuevas de seguridad). Navegador simulado: solo lectura sin login, contraseña incorrecta, login, CRUD, XSS y cerrar sesión. Pruebas de fuego locales con 5 comprobaciones nuevas de seguridad.

**Resultados en producción** (Render + Vercel): pruebas de fuego **21 de 21** y navegador simulado sobre la página de Vercel con login: todo pasa.

**Qué corregimos**
- **Contraseña con un espacio al final en `.env`:** la de Render no lo tenía, así que el login con la del `.env` fallaba (7 pruebas de fuego en rojo). Se quitó el espacio. Lección: un espacio invisible cambia la contraseña.
- La prueba del navegador suponía que siempre había 3 tareas y se trababa con una tarea real nuestra ("Presentacion Construccion"). Ahora cuenta las que hay al empezar.
- En Spring Boot 4, `@WebMvcTest` no cargaba la seguridad con `spring-security-test`; hizo falta `spring-boot-starter-security-test`.
- `jsdom` no trae `TextEncoder` (los navegadores sí); se agregó en la prueba, no en el front.

**Fugas**

| ID | Fuga | Riesgo | Cómo se cerró | Estado |
|----|------|--------|---------------|--------|
| F14 | Spring Security sin configurar crea un usuario `user` con una contraseña aleatoria que imprime en el log | La contraseña de producción quedaría en los logs de Render | Bean `UserDetailsService` propio: Spring no genera contraseña | Cerrada |
| F15 | HTTP Basic por defecto envía `WWW-Authenticate: Basic` | El navegador abre su ventana de login nativa, saltándose el front | `AuthenticationEntryPoint` propio sin esa cabecera | Cerrada |
| F16 | Un usuario o contraseña por defecto (`admin`/`admin`) "para que arranque" | Cualquiera podría escribir en producción | Sin valor por defecto; sin variables o con menos de 12 caracteres, la app no arranca (`SecurityConfigTest`) | Cerrada |
| F17 | `/error` de Spring respondía con otro formato e incluía `path` (visto con `PATCH /api/todos//toggle`) | Rompía la regla 10 y el formato del contrato | `ErrorPageController` con el formato del contrato y sin `path` | Cerrada |
| F18 | Guardar la credencial en `localStorage` | Cualquier XSS la robaría para siempre | Solo en memoria; se pierde al recargar | Cerrada |
| — | CSRF desactivado | Ninguno mientras no haya cookies | Justificada: la credencial viaja en la cabecera `Authorization`, que un sitio ajeno no puede enviar | Justificada |
| — | HTTP Basic no limita los intentos de login | Fuerza bruta contra la contraseña | Justificada para el MVP: contraseña de 12+ caracteres, BCrypt (lento) y HTTPS. Mejora futura: limitar intentos | Justificada |

## Lab 13 — Auditoría de seguridad

**Prompt:** "hagamos el reporte de auditoría".

**Qué hizo la IA:** auditó el commit `9009c61` con la skill `backend-seguro`. Revisó el historial de git, corrió las pruebas automáticas (50/50), las pruebas de fuego (21/21) y el navegador simulado en producción, mandó entradas hostiles a producción y revisó Supabase (advisors, roles e intento de acceso por la Data API con la llave pública). Resultado: [docs/auditoria/reporte.md](docs/auditoria/reporte.md).

**Resultado:** 28 hallazgos: 18 corregidos (incluye F1 a F18), 10 justificados y **0 abiertos**. Checklist `backend-seguro`: 34 de 34.

**Hallazgos nuevos de la auditoría**
- **H-19 (Media):** el backend usa el rol `postgres`, que se salta RLS. Se acepta para el MVP y queda un plan: rol `todos_app` con permisos mínimos.
- **H-21 (Baja):** una ruta con `%2f` recibe la página HTML de Tomcat. Justificado: no muestra la versión y no llega a la app.
- **H-22 (Baja):** la Data API de Supabase confirma que la tabla existe, pero niega leer, insertar y borrar (verificado).
- La auditoría **no** corrió un escáner automático de dependencias; queda como recomendación.

## Pendiente de revisar por nosotros

- Confirmar con el profesor la arquitectura: Spring Boot en Render y un front pequeño en Vercel, en lugar de Next.js.
- Validar el diagrama Mermaid en VS Code o GitHub.
