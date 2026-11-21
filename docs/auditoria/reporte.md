# Reporte de auditoría de seguridad — API de Tareas (Todos)

| | |
|---|---|
| **Fecha** | 21 de noviembre de 2026 |
| **Versión auditada** | commit `9009c61` en `main` ([repositorio](https://github.com/scharick/todos-api)) |
| **Alcance** | Backend Spring Boot (Render), front (Vercel), base de datos (Supabase), historial de git, configuración de despliegue y herramientas de IA |
| **Producción** | Front https://todos-api-frontend.vercel.app · API https://todos-api-hoal.onrender.com |
| **Checklist** | Skill [`backend-seguro`](../../.claude/skills/backend-seguro/SKILL.md) (8 grupos) |

## 1. Resumen

| Resultado | Cantidad |
|-----------|----------|
| Hallazgos totales | 28 |
| Corregidos | 18 |
| Justificados (riesgo aceptado y explicado) | 10 |
| Abiertos sin decisión | **0** |

No quedan hallazgos de severidad **Alta** abiertos. Los 7 hallazgos de severidad Alta se corrigieron durante el desarrollo, y cada corrección tiene una prueba o una verificación en producción.

El hallazgo más importante entre los que no se corrigieron es **H-19**: el backend se conecta a la BD con el rol `postgres`, que se salta RLS. Se acepta para el MVP y tiene un plan de corrección (§6).

## 2. Cómo se auditó

1. **Revisión del código y la configuración** contra cada punto de la skill `backend-seguro` (§4).
2. **Historial de git** completo (todos los commits): búsqueda de `.env`, contraseñas, URLs del pooler, llaves `service_role`, JWT y `sb_secret`.
3. **Pruebas automáticas:** `./mvnw test`, **50 de 50** pasan (11 de seguridad, sin tocar la BD).
4. **Pruebas de fuego** contra producción ([scripts/prueba-de-fuego.sh](../../scripts/prueba-de-fuego.sh)): **21 de 21**.
5. **Navegador simulado** (jsdom) sobre la página de Vercel: solo lectura sin sesión, contraseña incorrecta, login, CRUD, XSS y cerrar sesión. Todo pasa.
6. **Entradas hostiles en producción:** inyección SQL, ids fuera de rango, `;`, `..%2f`, `TRACE`, tipo de contenido incorrecto, rutas de `actuator` y de la consola H2, y un cuerpo de 1 MB.
7. **Supabase:** advisors de seguridad y rendimiento, roles y permisos, y un intento de leer, insertar y borrar por la Data API (PostgREST) con la llave pública.

## 3. Hallazgos

Severidad: **Alta** = permite leer o modificar datos sin permiso, o exponer secretos · **Media** = debilita una defensa · **Baja** = filtra información menor o rompe el formato · **Info** = buena práctica.

### 3.1 Corregidos

| ID | Severidad | Hallazgo | Corrección | Evidencia |
|----|-----------|----------|------------|-----------|
| H-01 | Alta | CORS abierto a cualquier origen (propuesta inicial de las specs) | Solo los orígenes de `CORS_ORIGINS` | Producción: otro origen → `403`; Vercel → permitido |
| H-02 | Alta | Consola H2 habilitada en la configuración inicial | Se eliminó al pasar a Supabase | `/h2-console` → `401`; H2 no está en el `pom.xml` |
| H-03 | Alta | `.env` no estaba en `.gitignore` | `.env` y `.env.*` ignorados | 0 apariciones de `.env` en todo el historial |
| H-04 | Alta | XSS posible si el front pintara datos con `innerHTML` | Solo `textContent` + Content-Security-Policy | 0 usos de `innerHTML`/`eval`; prueba con `<img onerror>` no se ejecuta |
| H-05 | Alta | Spring Security por defecto imprime una contraseña generada en el log | `UserDetailsService` propio | Log de arranque sin "generated security password" |
| H-06 | Alta | Riesgo de usuario o contraseña por defecto | Sin valores por defecto; la app no arranca sin `APP_USER`/`APP_PASSWORD` o con menos de 12 caracteres | `SecurityConfigTest` (3 pruebas) |
| H-07 | Alta | Escrituras sin autenticación (antes de Spring Security) | HTTP Basic en `POST`/`PUT`/`PATCH`/`DELETE`; todo lo que no está en el contrato queda cerrado | Producción: escribir sin login → `401` |
| H-08 | Media | `ddl-auto=update` permitía que Hibernate cambiara el esquema | `ddl-auto=validate` | `application.properties` línea 15 |
| H-09 | Media | Conector MCP de Supabase con permiso de escritura | `read_only=true` y limitado a un `project_ref` | `.mcp.json` |
| H-10 | Media | Los errores de Postgres revelan restricciones y columnas | `GlobalExceptionHandler` traduce todo al contrato | Prueba `ca_t_1`; en producción un JSON roto → mensaje genérico |
| H-11 | Media | La prueba de la plantilla se conectaba a la BD de producción | Se eliminó; las pruebas usan *mocks* | 50 pruebas sin conexión a la BD |
| H-12 | Media | `CORS_ORIGINS=*` mal configurado reabriría H-01 | `SecurityConfig` descarta `*` | Código de `corsConfigurationSource` |
| H-13 | Media | El contenedor corría como root | Usuario `app` sin privilegios | `Dockerfile` |
| H-14 | Media | Credencial en `localStorage` (robable con XSS) | Solo en memoria | 0 usos de `localStorage`/`sessionStorage` |
| H-15 | Baja | Rutas inexistentes o métodos no permitidos daban `500` | Manejadores `404`/`405`/`415` | Pruebas `ruta_inexistente…`, `metodo_no_soportado…` |
| H-16 | Baja | `?completed=yes/1/on` se aceptaba como `true` | Solo `true` o `false` | Prueba `completed_solo_acepta_true_o_false` |
| H-17 | Baja | CSP del front permitía cualquier `*.onrender.com` | `connect-src` solo para el backend propio | `frontend/index.html` |
| H-18 | Baja | `/error` respondía con otro formato e incluía `path`; `WWW-Authenticate` abría la ventana del navegador | `ErrorPageController` y `AuthenticationEntryPoint` propios | Producción: `//toggle` → JSON del contrato; `401` sin `WWW-Authenticate` |

### 3.2 Justificados

| ID | Severidad | Hallazgo | Justificación | Mitigación actual |
|----|-----------|----------|---------------|-------------------|
| H-19 | Media | El backend usa el rol `postgres` (`rolbypassrls = true`, dueño de la tabla). Si alguien robara `DB_PASSWORD`, tendría acceso amplio a la BD | Aceptado para el MVP: un rol dedicado exige crearlo con contraseña desde el dashboard y cambiar `DB_USER`/`DB_PASSWORD` en Render. Ver plan en §6 | La contraseña solo está en Render y en `.env` (fuera de git); `postgres` no es superusuario (`rolsuper = false`) |
| H-20 | Media | HTTP Basic no limita los intentos de login (fuerza bruta) | Aceptado para el MVP: hay un solo usuario y no hay datos sensibles | Contraseña de 12+ caracteres, BCrypt (lento), HTTPS obligatorio y WAF de Render delante |
| H-21 | Baja | Una ruta con `%2f` (ej. `..%2f..%2f`) recibe la página HTML de error de Tomcat, no el JSON del contrato | Tomcat la rechaza antes de llegar a Spring; reemplazarla exige un *valve* propio de Tomcat | La página **no muestra la versión** del servidor ni datos; la petición nunca llega a la app |
| H-22 | Baja | La Data API de Supabase responde `permission denied for table todos` y sugiere un `GRANT`: confirma que la tabla existe | Es el comportamiento estándar de Supabase | Leer, insertar y borrar con la llave pública fallan (`401`/`42501`), verificado |
| H-23 | Baja | Sin límite de tamaño del cuerpo para el usuario autenticado | Solo el único usuario puede enviar cuerpos; sin login se rechaza antes de leer el cuerpo | 1 MB sin login → `401` en 1,7 s |
| H-24 | Info | Advisor `rls_enabled_no_policy` en `public.todos` | Intencional: el dueño de las filas es la app; `anon` y `authenticated` no deben tener acceso | `anon`/`authenticated` sin permisos (verificado en SQL y por REST) |
| H-25 | Info | CSRF desactivado | No hay cookies ni sesión (`STATELESS`); la credencial va en `Authorization`, que un sitio ajeno no puede enviar | CORS limitado a Vercel |
| H-26 | Info | El rechazo de CORS responde `403 Invalid CORS request` en texto plano | Lo genera Spring antes del controlador; no expone detalles | Solo lo ven orígenes no permitidos |
| H-27 | Info | Patrones de inyección SQL y `TRACE` reciben páginas HTML del WAF de Render/Cloudflare | Es una capa de defensa extra, fuera de la app | Además, la app solo usa consultas con parámetros (0 SQL concatenado) |
| H-28 | Info | `spring-boot-devtools` está en el `pom.xml` | Es `optional` y Spring Boot no lo empaqueta | 0 archivos de devtools en el `.jar` de producción |

## 4. Checklist `backend-seguro`

| # | Punto | Resultado | Evidencia |
|---|-------|-----------|-----------|
| 1.1 | Sin secretos en código, `application.properties` ni git | Cumple | Búsqueda en todo el historial: 0 resultados (solo la plantilla de `mvnw` y una clave falsa de prueba) |
| 1.2 | `.env` en `.gitignore` | Cumple | `.gitignore` líneas 36-37 |
| 1.3 | `service_role` nunca se usa | Cumple | 0 apariciones en el repo |
| 1.4 | Sin consolas de depuración en producción | Cumple | Sin Actuator ni H2; devtools fuera del `.jar` (H-28) |
| 1.5 | `ddl-auto=validate` | Cumple | `application.properties` |
| 2.1 | Escrituras con login; cerrado por defecto | Cumple | `anyRequest().authenticated()`; `/actuator/health` → `401` |
| 2.2 | Sin usuario ni contraseña por defecto | Cumple | `SecurityConfigTest` |
| 2.3 | Contraseña con BCrypt | Cumple | `BCryptPasswordEncoder` en `SecurityConfig` |
| 2.4 | `401` con el formato del contrato, sin `WWW-Authenticate` | Cumple | Prueba de fuego "401 sin ventana del navegador" |
| 2.5 | CSRF desactivado solo por ser `STATELESS` | Cumple | H-25 |
| 2.6 | Credencial del front solo en memoria | Cumple | H-14 |
| 2b.1 | DTO con Bean Validation | Cumple | `TodoRequest` (`@NotBlank`, `@Size`) |
| 2b.2 | DTO sin `id` ni `createdAt` | Cumple | Prueba `ca_3_2`; `GENERATED ALWAYS` como respaldo |
| 2b.3 | id inválido → `400` | Cumple | Producción: `abc`, `-1` y un id de 23 dígitos → `400` |
| 2b.4 | `trim` antes de validar | Cumple | Constructor de `TodoRequest`; prueba `ca_3_1` |
| 3.1 | Sin SQL concatenado | Cumple | 0 `@Query`, `createQuery` ni SQL concatenado; solo Spring Data JPA |
| 3.2 | Escrituras `@Transactional` | Cumple | 6 anotaciones en `TodoService` |
| 3.3 | Tablas por migración con RLS | Cumple | Migración `20261120000000`; RLS activo |
| 3.4 | Advisors después de la migración | Cumple | Seguridad: solo H-24 (INFO). Rendimiento: 0 hallazgos |
| 4.1 | Excepciones por `@RestControllerAdvice` | Cumple | `GlobalExceptionHandler` + `ErrorPageController` |
| 4.2 | Sin detalles internos en los mensajes | Cumple | Prueba `ca_t_1`; `getMessage()` solo en excepciones propias con mensajes del contrato |
| 4.3 | Lo inesperado → `500` genérico y detalle al log | Cumple | `log.error` en el manejador de `Exception` |
| 4.4 | `include-stacktrace=never`, `include-message=never` | Cumple | `application.properties` líneas 31-34 |
| 4.5 | Solo campos del contrato | Cumple | `TodoResponse`, `ErrorResponse` y `SesionResponse` son *records* |
| 5.1 | CORS sin `*` | Cumple | H-01, H-12 |
| 5.2 | Solo endpoints del contrato | Cumple | 7 rutas, iguales a las Op. 1-7, más `/error` |
| 5.3 | Logs sin datos sensibles | Cumple | Un solo `log.error`, sin cuerpos ni credenciales |
| 6.1 | Contenedor sin root | Cumple | `USER app` en el `Dockerfile` |
| 6.2 | Sin dependencias sin uso | Cumple | Todas se usan; devtools solo en desarrollo |
| 6.3 | HTTPS en producción | Cumple | HTTP → `301` a HTTPS; HSTS en Render y Vercel |
| 7.1 | Cada error de SPECS §12 con prueba de código y mensaje | Cumple | `TodoControllerTest` (40 pruebas) |
| 7.2 | Prueba de que un error no expone detalles | Cumple | `ca_t_1_error_inesperado_no_expone_detalles` |
| 8.1 | MCP limitado y en `read_only` | Cumple | `.mcp.json` |
| 8.2 | Sin SQL destructivo sin aprobación | Cumple | La única migración se aplicó después de la aprobación escrita (bitácora, Lab 7) |

**34 de 34 puntos cumplen.**

## 5. Producción: cabeceras y comportamiento

| Comprobación | Backend (Render) | Front (Vercel) |
|--------------|------------------|----------------|
| HTTPS obligatorio | HTTP → `301` a HTTPS | Sí |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | `max-age=63072000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` | `nosniff` |
| `X-Frame-Options` | `DENY` | `DENY` |
| `Cache-Control` | `no-store` | — |
| `Referrer-Policy` / `Permissions-Policy` | — | Sí / Sí |
| Revela la versión del servidor | No (`Server: cloudflare`) | No (`Server: Vercel`) |

**Versiones en el `.jar` de producción:** Spring Boot 4.1.1, Spring MVC 7.0.9, Tomcat 11.0.24, Hibernate 7.4.5, Jackson 3.1.5, PostgreSQL JDBC 42.7.13, HikariCP 7.0.2, Logback 1.5.38. **No se corrió un escáner automático de vulnerabilidades** de dependencias (ver §6).

## 6. Recomendaciones (después del MVP)

1. **Rol de BD dedicado (H-19).** Crear un rol `todos_app` que solo tenga `SELECT`, `INSERT`, `UPDATE` y `DELETE` sobre `public.todos` y no se salte RLS; ponerle contraseña desde el dashboard y usarlo en `DB_USER`/`DB_PASSWORD` de Render.
2. **Límite de intentos de login (H-20).** Bloquear la IP unos minutos después de 5 intentos fallidos.
3. **Escáner de dependencias.** Agregar OWASP Dependency-Check o Dependabot al repositorio de GitHub.
4. **Data API de Supabase (H-22).** Si nunca se va a usar, quitar `public` de los esquemas expuestos (*Settings → Data API*).
5. **Rotar `APP_PASSWORD`** si se compartió por un canal inseguro.
