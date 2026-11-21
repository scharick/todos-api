# CLAUDE.md — API de Tareas (Todos)

MVP académico: API REST de tareas (CRUD) con un front pequeño que la consume.

## Arquitectura

| Parte | Tecnología | Dónde se despliega |
|-------|-----------|--------------------|
| Backend (este repo, raíz) | Spring Boot 4.1.1, Java 21, Maven | Render (Docker): https://todos-api-hoal.onrender.com |
| Base de datos | PostgreSQL en Supabase | Supabase |
| Front (`frontend/`) | HTML, CSS y JS | Vercel: https://todos-api-frontend.vercel.app |

Después de desplegar, corre `bash scripts/prueba-de-fuego.sh <url-render> <url-vercel>` y `bash scripts/prueba-de-fuego-cruzada.sh <url-render>` (con `APP_USER`/`APP_PASSWORD` en el entorno); todas deben pasar. Toda escritura nueva debe ser atómica o manejar la concurrencia (ver F19).

## Fuentes de verdad (léelas antes de cambiar algo)

1. [docs/contratos/api.md](docs/contratos/api.md): rutas, entradas, respuestas, errores y mensajes. **Si algo no coincide, manda el contrato.**
2. [docs/datos/modelo.md](docs/datos/modelo.md): tabla `todos` y qué capa cumple cada regla (§7).
3. [SPECS.md](SPECS.md): reglas (§3), configuración (§7) y criterios de aceptación del back (§12).
4. [docs/auditoria/reporte.md](docs/auditoria/reporte.md): auditoría de seguridad; un hallazgo nuevo se agrega ahí como corregido o justificado.
5. [BITACORA_IA.md](BITACORA_IA.md): registra ahí los prompts clave y cualquier fuga de seguridad encontrada y cómo se cerró.

## Comandos

```powershell
./mvnw spring-boot:run   # backend en http://localhost:8080 (lee .env)
./mvnw test              # pruebas del backend (no usan la BD)
python -m http.server 5173 -d frontend   # front en http://localhost:5173
```

El front (`frontend/`) es HTML, CSS y JS sin compilación. La URL del backend está en `frontend/config.js`. Los datos del servidor se pintan solo con `textContent`, nunca con `innerHTML`.

## Backend

Usa la skill [backend-seguro](.claude/skills/backend-seguro/SKILL.md) siempre que escribas o revises código del backend, migraciones o configuración.

**Capas** (paquete `edu.usta.todos`): `controller → service → repository`, más `dto`, `model` y `exception`. El controller no habla con el repository; la lógica de negocio vive en el service.

**Reglas obligatorias**
- Implementa exactamente el contrato: mismas rutas, códigos HTTP y mensajes. No inventes endpoints.
- Toda regla de SPECS §3 se cumple en la capa indicada en modelo.md §7; la BD es solo el respaldo.
- Los errores pasan por `GlobalExceptionHandler`. Nunca se devuelven stack traces, nombres de clases, SQL ni mensajes de Postgres.
- Los secretos (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_PASSWORD`) solo van en variables de entorno. Nunca en `application.properties`, en el código ni en git.
- El esquema solo cambia con una migración nueva en `supabase/migrations/` (`ddl-auto=validate`). Toda tabla nueva lleva RLS activado.
- Spring Security (`config/SecurityConfig.java`): `GET` de tareas es público; todo lo demás exige HTTP Basic. Toda ruta nueva queda protegida por defecto; si debe ser pública, agrégala al contrato primero.
- CORS solo para los orígenes de `CORS_ORIGINS` (la URL de Vercel y `http://localhost:*`), nunca `*`.
- Cada criterio de SPECS §12 tiene su prueba. Una tarea no está terminada si `./mvnw test` falla.

**Base de datos y MCP de Supabase**
- Antes de un cambio de esquema, revisa las tablas actuales con `list_tables`.
- Muestra el SQL de toda migración y espera aprobación antes de aplicarla.
- Después de cada cambio de esquema, ejecuta los advisors de seguridad y reporta los hallazgos.
- El conector va en `read_only=true` salvo cuando se aplica una migración aprobada.

## Git

- Un commit por tarea, con prefijos `feat:`, `fix:`, `docs:`, `test:`, `chore:`.
- No subas `.env`, `target/` ni credenciales.
