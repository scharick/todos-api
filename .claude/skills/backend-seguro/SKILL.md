---
name: backend-seguro
description: Checklist de seguridad para el backend Spring Boot + Supabase de este proyecto. Úsala al escribir o revisar endpoints, servicios, manejo de errores, configuración, migraciones SQL o despliegue, y al hacer la auditoría de seguridad.
---

# Backend seguro

Aplica cada punto que toque el cambio. Si un punto no se cumple, corrígelo o justifícalo por escrito en `BITACORA_IA.md` (sección "Fugas").

## 1. Secretos y configuración

- [ ] No hay contraseñas, URLs de BD, llaves de Supabase ni tokens en el código, en `application.properties` ni en git. Solo variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGINS`).
- [ ] `.env` y archivos con credenciales están en `.gitignore`.
- [ ] La llave `service_role` de Supabase nunca se usa ni se envía al front.
- [ ] No hay consolas ni herramientas de depuración abiertas en producción (consola H2, `devtools`, Actuator con endpoints sensibles).
- [ ] `spring.jpa.hibernate.ddl-auto=validate`: Hibernate nunca cambia el esquema.

## 2. Entrada

- [ ] Todo dato del cliente pasa por un DTO con Bean Validation (`@NotBlank`, `@Size`…). Nunca se enlaza el JSON directo a la entidad JPA.
- [ ] El DTO no tiene `id` ni `createdAt`: el cliente no puede asignarlos (protección contra *mass assignment*).
- [ ] El id de la ruta se valida como número; un id inválido da `400`, no `500`.
- [ ] Se recorta el texto antes de validar (`trim`).

## 3. Acceso a datos

- [ ] Solo Spring Data JPA o consultas con parámetros. Nunca se concatena texto del usuario en SQL o JPQL.
- [ ] Cada operación de escritura es `@Transactional`.
- [ ] Toda tabla nueva se crea con una migración en `supabase/migrations/`, con RLS activado y políticas según el dueño de la fila.
- [ ] Después de cada migración se ejecutan los advisors de seguridad; todo hallazgo se corrige o se justifica.

## 4. Errores y respuestas

- [ ] Todas las excepciones pasan por `@RestControllerAdvice` y usan el formato de error del contrato.
- [ ] El mensaje nunca muestra stack traces, nombres de clases, SQL, nombres de restricciones ni mensajes de Postgres.
- [ ] Lo inesperado responde `500 Error interno del servidor`, y el detalle va solo al log del servidor.
- [ ] `server.error.include-stacktrace=never` y `server.error.include-message=never`.
- [ ] Las respuestas devuelven solo los campos del contrato (DTO o entidad sin campos extra).

## 5. Superficie expuesta

- [ ] CORS solo para los orígenes de `CORS_ORIGINS`; nunca `*`.
- [ ] Solo existen los endpoints del contrato.
- [ ] Los logs no guardan datos sensibles ni cuerpos completos de las peticiones.

## 6. Dependencias y despliegue

- [ ] El `Dockerfile` ejecuta la app con un usuario que no es `root` y solo copia el `.jar`.
- [ ] No hay dependencias sin uso en `pom.xml`.
- [ ] HTTPS en producción (Render y Vercel ya lo dan).

## 7. Pruebas

- [ ] Cada caso de error de SPECS §12 tiene una prueba que verifica el código **y** el mensaje.
- [ ] Hay una prueba que comprueba que un error no expone detalles internos.

## 8. Herramientas de la IA

- [ ] El MCP de Supabase está limitado a un proyecto (`project_ref`) y en `read_only=true`, salvo al aplicar una migración aprobada.
- [ ] Ningún SQL destructivo se ejecuta sin aprobación.
