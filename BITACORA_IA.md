# Bitácora de uso de IA

Asistente: Claude Code (Claude Opus 5.5), en VS Code.
Proyecto: API de Tareas (Todos) — Spring Boot 4.1.1, Java 21.

> **Cómo trabajamos:** en varios pasos le dijimos a la IA que aceptábamos lo que ella considerara mejor. Por eso la mayoría de las decisiones las propuso la IA y las aceptamos sin cambios. Las correcciones de este registro salieron de revisar el trabajo con el **checklist del interventor** y con el **prompt del profesor**, no de cambios manuales nuestros.

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

## Pendiente de revisar por nosotros

- Confirmar con el profesor que se acepta Spring Boot en lugar de Next.js.
- Validar el diagrama Mermaid en VS Code o GitHub.
