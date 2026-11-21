# Contrato de la API — Tareas (Todos)

Basado en [SPECS.md](../../SPECS.md) y en el modelo aprobado [docs/datos/modelo.md](../datos/modelo.md).

> **Nota de implementación:** la plantilla del curso ubica la lógica en Route Handlers de Next.js (`src/app/api/...`). Este proyecto es **Spring Boot**, así que cada operación vivirá en un método de `TodoController` (`src/main/java/edu/usta/todos/controller/`). Las rutas, los JSON y los códigos HTTP de este contrato no cambian.

## 1. Convenciones generales

| Aspecto | Valor |
|---------|-------|
| URL base local | `http://localhost:8080` |
| URL base producción | `https://<app>.onrender.com` |
| Formato | JSON (`Content-Type: application/json`), UTF-8 |
| Fechas | ISO 8601 con zona horaria, en hora de Colombia. Ej.: `2026-11-20T10:30:00-05:00` |
| Autenticación | Ninguna. Todas las operaciones son **públicas** (SPECS §11) |
| Campos desconocidos en el cuerpo | Se ignoran |
| `id` y `createdAt` en el cuerpo | Se ignoran; los asigna el servidor (regla 4) |

### Objeto `Todo` (respuesta)

```json
{
  "id": 1,
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": false,
  "createdAt": "2026-11-20T10:30:00-05:00"
}
```

`description` puede ser `null`.

### Objeto de error (todas las operaciones)

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Todo con id 99 no existe",
  "timestamp": "2026-11-20T10:30:00-05:00"
}
```

- `message` es el texto que verá el usuario. **Nunca** incluye stack traces, nombres de clases, SQL ni rutas internas (regla 10).
- Si hay varios errores de validación a la vez, `message` los une con `"; "`. Ej.: `"El título es obligatorio; La descripción no puede superar 500 caracteres"`.

### Códigos de error que pide la plantilla y cuáles aplican

| Código | ¿Aplica? | Motivo |
|--------|----------|--------|
| `400` Bad Request | Sí | Datos de entrada inválidos |
| `401` Unauthorized | **No** | No hay login; todas las operaciones son públicas. Aparecerá cuando se agreguen usuarios |
| `404` Not Found | Sí | La tarea no existe o ya fue borrada |
| `409` Conflict | **No** | No hay reglas de unicidad aparte del `id` (se permiten títulos repetidos) ni estados que choquen: se puede editar una tarea completada |
| `500` Internal Server Error | Sí (todas) | Error inesperado. Mensaje: `Error interno del servidor` |

### Validaciones compartidas

**Parámetro `{id}` en la ruta:** debe ser un número entero positivo. Si no lo es → `400` `El id debe ser un número`.

**Cuerpo de tarea (`POST` y `PUT`):**

| Campo | Obligatorio | Validación | Mensaje si falla (`400`) |
|-------|-------------|------------|--------------------------|
| `title` | Sí | Se quitan espacios al inicio y al final; luego debe tener 1–100 caracteres | `El título es obligatorio` / `El título no puede superar 100 caracteres` |
| `description` | No | Máx. 500 caracteres. Vacía o solo espacios se guarda como `null` | `La descripción no puede superar 500 caracteres` |
| `completed` | No | `true` o `false`. Si no se envía, vale `false` | `El cuerpo de la petición no es válido` |
| (cuerpo completo) | Sí | Debe ser JSON válido y con los tipos correctos | `El cuerpo de la petición no es válido` |

## 2. Acciones → operaciones

| Acción del usuario | Operación |
|--------------------|-----------|
| Ver todas mis tareas | [Op. 1](#op-1--listar-tareas) `GET /api/todos` |
| Ver solo pendientes o completadas | [Op. 1](#op-1--listar-tareas) `GET /api/todos?completed=...` |
| Ver el detalle de una tarea | [Op. 2](#op-2--ver-una-tarea) `GET /api/todos/{id}` |
| Crear una tarea | [Op. 3](#op-3--crear-una-tarea) `POST /api/todos` |
| Editar una tarea | [Op. 4](#op-4--editar-una-tarea) `PUT /api/todos/{id}` |
| Marcar o desmarcar como completada | [Op. 5](#op-5--marcardesmarcar-como-completada) `PATCH /api/todos/{id}/toggle` |
| Eliminar una tarea | [Op. 6](#op-6--eliminar-una-tarea) `DELETE /api/todos/{id}` |

---

## Op. 1 — Listar tareas

**`GET /api/todos`** · Quién: cualquiera (pública)

**Entrada**

| Parámetro | Dónde | Obligatorio | Validación |
|-----------|-------|-------------|------------|
| `completed` | query | No | `true` o `false`. Sin él, se listan todas |

**Respuesta exitosa: `200 OK`.** Lista ordenada por `id` ascendente; puede estar vacía (`[]`).

```json
[
  {
    "id": 1,
    "title": "Estudiar Spring Boot",
    "description": "Repasar controladores y JPA",
    "completed": false,
    "createdAt": "2026-11-20T10:30:00-05:00"
  },
  {
    "id": 3,
    "title": "Desplegar en Render",
    "description": null,
    "completed": false,
    "createdAt": "2026-11-20T10:30:05-05:00"
  }
]
```

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | `completed` no es `true` ni `false` (ej. `?completed=si`) | `El parámetro completed debe ser true o false` |
| `500` | Error inesperado | `Error interno del servidor` |

---

## Op. 2 — Ver una tarea

**`GET /api/todos/{id}`** · Quién: cualquiera (pública)

**Entrada**

| Parámetro | Dónde | Obligatorio | Validación |
|-----------|-------|-------------|------------|
| `id` | ruta | Sí | Entero positivo |

**Respuesta exitosa: `200 OK`**

```json
{
  "id": 1,
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": false,
  "createdAt": "2026-11-20T10:30:00-05:00"
}
```

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | El id no es un número (ej. `/api/todos/abc`) | `El id debe ser un número` |
| `404` | No existe una tarea con ese id | `Todo con id {id} no existe` |
| `500` | Error inesperado | `Error interno del servidor` |

---

## Op. 3 — Crear una tarea

**`POST /api/todos`** · Quién: cualquiera (pública)

**Entrada (cuerpo)**: ver *Cuerpo de tarea* en §1.

```json
{
  "title": "  Estudiar Spring Boot  ",
  "description": "Repasar controladores y JPA"
}
```

**Respuesta exitosa: `201 Created`**, con cabecera `Location: /api/todos/{id}`. El título llega recortado, `completed` vale `false` por defecto y el servidor asigna `id` y `createdAt`.

```json
{
  "id": 4,
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": false,
  "createdAt": "2026-11-20T11:02:17-05:00"
}
```

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | El cuerpo no es JSON válido o un campo tiene un tipo incorrecto (ej. `"completed": "si"`) | `El cuerpo de la petición no es válido` |
| `400` | `title` falta, es `null`, está vacío o solo tiene espacios | `El título es obligatorio` |
| `400` | `title` tiene más de 100 caracteres después del recorte | `El título no puede superar 100 caracteres` |
| `400` | `description` tiene más de 500 caracteres | `La descripción no puede superar 500 caracteres` |
| `500` | Error inesperado | `Error interno del servidor` |

Si hay un `400`, no se crea nada (regla 8).

---

## Op. 4 — Editar una tarea

**`PUT /api/todos/{id}`** · Quién: cualquiera (pública)

Reemplazo completo: los campos que no se envían toman su valor por defecto (`description` → `null`, `completed` → `false`). `id` y `createdAt` no cambian nunca (reglas 5 y 7).

**Entrada**

| Parámetro | Dónde | Obligatorio | Validación |
|-----------|-------|-------------|------------|
| `id` | ruta | Sí | Entero positivo |
| cuerpo | body | Sí | Ver *Cuerpo de tarea* en §1 |

```json
{
  "title": "Estudiar Spring Boot y JPA",
  "description": "Repasar controladores, servicios y repositorios",
  "completed": true
}
```

**Respuesta exitosa: `200 OK`**

```json
{
  "id": 1,
  "title": "Estudiar Spring Boot y JPA",
  "description": "Repasar controladores, servicios y repositorios",
  "completed": true,
  "createdAt": "2026-11-20T10:30:00-05:00"
}
```

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | El id no es un número | `El id debe ser un número` |
| `400` | El cuerpo no es JSON válido o un campo tiene un tipo incorrecto | `El cuerpo de la petición no es válido` |
| `400` | `title` falta, es `null`, está vacío o solo tiene espacios | `El título es obligatorio` |
| `400` | `title` tiene más de 100 caracteres después del recorte | `El título no puede superar 100 caracteres` |
| `400` | `description` tiene más de 500 caracteres | `La descripción no puede superar 500 caracteres` |
| `404` | No existe una tarea con ese id | `Todo con id {id} no existe` |
| `500` | Error inesperado | `Error interno del servidor` |

Orden de las comprobaciones: primero el id (`400`), luego que exista (`404`) y al final el cuerpo (`400`). Si hay un `400`, la tarea queda como estaba (regla 8).

---

## Op. 5 — Marcar/desmarcar como completada

**`PATCH /api/todos/{id}/toggle`** · Quién: cualquiera (pública)

Invierte `completed`: `false` → `true` y `true` → `false`. No cambia ningún otro campo.

**Entrada**

| Parámetro | Dónde | Obligatorio | Validación |
|-----------|-------|-------------|------------|
| `id` | ruta | Sí | Entero positivo |
| cuerpo | — | No | No lleva cuerpo; si se envía, se ignora |

**Respuesta exitosa: `200 OK`**

```json
{
  "id": 1,
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": true,
  "createdAt": "2026-11-20T10:30:00-05:00"
}
```

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | El id no es un número | `El id debe ser un número` |
| `404` | No existe una tarea con ese id | `Todo con id {id} no existe` |
| `500` | Error inesperado | `Error interno del servidor` |

---

## Op. 6 — Eliminar una tarea

**`DELETE /api/todos/{id}`** · Quién: cualquiera (pública)

Borrado físico: la tarea deja de existir (modelo, pregunta 6).

**Entrada**

| Parámetro | Dónde | Obligatorio | Validación |
|-----------|-------|-------------|------------|
| `id` | ruta | Sí | Entero positivo |

**Respuesta exitosa: `204 No Content`**, sin cuerpo.

**Errores**

| Código | Cuándo | Mensaje |
|--------|--------|---------|
| `400` | El id no es un número | `El id debe ser un número` |
| `404` | No existe una tarea con ese id, o ya fue borrada | `Todo con id {id} no existe` |
| `500` | Error inesperado | `Error interno del servidor` |
