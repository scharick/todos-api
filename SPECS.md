# Especificación — API de Tareas (Todos)

## 1. Objetivo

Construir un backend REST sencillo para gestionar una lista de tareas (CRUD) y desplegarlo en internet con una URL pública.

## 2. Stack tecnológico

| Elemento        | Elección                                   |
|-----------------|--------------------------------------------|
| Lenguaje        | Java 21                                    |
| Framework       | Spring Boot 4.1.1 (Spring Web MVC)         |
| Persistencia    | Spring Data JPA + base de datos H2 (en memoria) |
| Validación      | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Utilidades      | Lombok                                     |
| Build           | Maven (wrapper `mvnw`)                     |
| Despliegue      | Docker + Render (plan gratuito)            |

> H2 en memoria hace que no haga falta instalar ni pagar una base de datos. Los datos se borran cada vez que el servidor se reinicia, lo cual es aceptable para este proyecto académico.

## 3. Modelo de datos

### Entidad `Todo`

| Campo         | Tipo            | Reglas                                      |
|---------------|-----------------|---------------------------------------------|
| `id`          | `Long`          | Autogenerado, clave primaria                |
| `title`       | `String`        | Obligatorio, 1–100 caracteres               |
| `description` | `String`        | Opcional, máx. 500 caracteres               |
| `completed`   | `boolean`       | Por defecto `false`                         |
| `createdAt`   | `OffsetDateTime` | Se asigna automáticamente al crear, con zona horaria |

Ejemplo JSON:

```json
{
  "id": 1,
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": false,
  "createdAt": "2026-11-20T10:30:00-05:00"
}
```

### Reglas (lo que nunca puede pasar)

1. Nunca existe una tarea sin título, o con un título que es solo espacios.
2. Nunca un título pasa de 100 caracteres ni una descripción de 500.
3. Nunca dos tareas tienen el mismo `id`.
4. Nunca el cliente elige el `id` ni la `createdAt`; los asigna el servidor.
5. Nunca cambia la `createdAt` después de crear la tarea, y nunca es una fecha futura.
6. Nunca `completed` queda vacío (`null`): siempre es `true` o `false`.
7. Nunca un `PUT` o `PATCH` cambia el `id` de una tarea.
8. Nunca se guarda nada a medias: si la respuesta es `400`, la tarea queda como estaba.
9. Nunca se responde "éxito" sobre una tarea que no existe: siempre es `404`.
10. Nunca un error muestra detalles internos del servidor (stack trace); siempre usa el formato de la sección 5.

### Decisiones tomadas

- Se permiten títulos repetidos.
- Se puede editar una tarea ya completada.
- Borrar una tarea que ya fue borrada devuelve `404` (regla 9).
- Se quitan los espacios al inicio y al final del título antes de validarlo (regla 1).

## 4. Endpoints

Ruta base: `/api/todos`

| Método   | Ruta                | Descripción                     | Respuesta OK        |
|----------|---------------------|---------------------------------|---------------------|
| `GET`    | `/api/todos`        | Listar todas las tareas         | `200` + lista       |
| `GET`    | `/api/todos/{id}`   | Obtener una tarea por id        | `200` + tarea       |
| `POST`   | `/api/todos`        | Crear una tarea                 | `201` + tarea creada|
| `PUT`    | `/api/todos/{id}`   | Actualizar una tarea completa   | `200` + tarea       |
| `PATCH`  | `/api/todos/{id}/toggle` | Marcar/desmarcar como completada | `200` + tarea |
| `DELETE` | `/api/todos/{id}`   | Eliminar una tarea              | `204` sin cuerpo    |

Filtro opcional en el listado: `GET /api/todos?completed=true` o `?completed=false`.

### Cuerpo para `POST` y `PUT`

```json
{
  "title": "Estudiar Spring Boot",
  "description": "Repasar controladores y JPA",
  "completed": false
}
```

`id` y `createdAt` no se envían; los asigna el servidor. Si el cliente los envía, se ignoran. Si `completed` no se envía, vale `false`.

### Acción del usuario → operación

El MVP no tiene pantallas propias (el frontend está fuera de alcance). Estas son las acciones que haría un usuario desde cualquier cliente (Postman, un frontend futuro) y la operación que las cumple:

| Acción del usuario                  | Operación                       |
|-------------------------------------|---------------------------------|
| Ver todas mis tareas                | `GET /api/todos`                |
| Ver solo pendientes o completadas   | `GET /api/todos?completed=...`  |
| Ver el detalle de una tarea         | `GET /api/todos/{id}`           |
| Crear una tarea                     | `POST /api/todos`               |
| Editar una tarea                    | `PUT /api/todos/{id}`           |
| Marcar o desmarcar como completada  | `PATCH /api/todos/{id}/toggle`  |
| Eliminar una tarea                  | `DELETE /api/todos/{id}`        |

### Contrato detallado

Las entradas, validaciones, respuestas de ejemplo y errores de cada operación, con su código HTTP y su mensaje, están en [docs/contratos/api.md](docs/contratos/api.md). Ese documento es la fuente oficial: si algo no coincide con estas specs, manda el contrato.

## 5. Manejo de errores

Todos los errores responden con el mismo formato:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Todo con id 99 no existe",
  "timestamp": "2026-11-20T10:30:00-05:00"
}
```

| Caso                                   | Código |
|----------------------------------------|--------|
| La tarea no existe                     | `404`  |
| Datos inválidos (ej. `title` vacío)    | `400`  |
| Error inesperado del servidor          | `500`  |

## 6. Estructura del código

```
src/main/java/edu/usta/todos/
├── TodosApplication.java
├── model/        Todo.java                 (entidad JPA)
├── dto/          TodoRequest.java          (datos de entrada + validaciones)
├── repository/   TodoRepository.java       (interfaz JpaRepository)
├── service/      TodoService.java          (lógica de negocio)
├── controller/   TodoController.java       (endpoints REST)
└── exception/    TodoNotFoundException.java
                  GlobalExceptionHandler.java (@RestControllerAdvice)
```

Flujo: `Controller → Service → Repository → H2`.

## 7. Configuración

`application.properties`:

```properties
spring.application.name=todos
server.port=${PORT:8080}
spring.datasource.url=jdbc:h2:mem:todosdb
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
```

`server.port=${PORT:8080}` permite que Render asigne el puerto en producción y que localmente se use el 8080.

## 8. Requisitos no funcionales

- **CORS** habilitado para cualquier origen, para que un frontend pueda consumir la API.
- **Datos de ejemplo**: al arrancar se cargan 2–3 tareas para que la API no esté vacía en la demo.
- **Pruebas**: al menos una prueba de controlador (`@WebMvcTest` o `MockMvc`) por endpoint principal.

## 9. Despliegue

1. Crear un `Dockerfile` multi-etapa (compilar con Maven, ejecutar con JRE 21).
2. Subir el proyecto a un repositorio de GitHub.
3. En Render: *New → Web Service* → conectar el repo → entorno **Docker** → plan **Free**.
4. Verificar con `GET https://<app>.onrender.com/api/todos`.

> En el plan gratuito de Render el servicio "se duerme" tras un rato sin uso; la primera petición puede tardar ~30–60 s.

## 10. Criterios de aceptación

- [ ] Los 6 endpoints funcionan localmente (probados con Postman, Thunder Client o curl).
- [ ] Las validaciones devuelven `400` con mensaje claro.
- [ ] Una tarea inexistente devuelve `404`.
- [ ] `./mvnw test` pasa sin errores.
- [ ] La API responde desde la URL pública de Render.

## 11. Fuera de alcance

Autenticación/usuarios, paginación, base de datos persistente (PostgreSQL) y frontend. Se pueden añadir después como mejoras.

## 12. Criterios de aceptación del back

Formato Dado / Cuando / Entonces. Cada operación tiene al menos un caso de error. Los mensajes son los de [docs/contratos/api.md](docs/contratos/api.md).

### Op. 1 — Listar tareas

- **CA-1.1** Dado que existen las tareas 1, 2 y 3, cuando hago `GET /api/todos`, entonces recibo `200` y una lista con las 3 tareas ordenadas por `id`.
- **CA-1.2** Dado que la tarea 2 está completada y la 1 y la 3 no, cuando hago `GET /api/todos?completed=false`, entonces recibo `200` y solo las tareas 1 y 3.
- **CA-1.3** Dado que no existe ninguna tarea, cuando hago `GET /api/todos`, entonces recibo `200` y `[]`.
- **CA-1.4 (error)** Dado cualquier estado, cuando hago `GET /api/todos?completed=si`, entonces recibo `400` con el mensaje `El parámetro completed debe ser true o false`.

### Op. 2 — Ver una tarea

- **CA-2.1** Dado que existe la tarea 1, cuando hago `GET /api/todos/1`, entonces recibo `200` con los 5 campos de la tarea 1.
- **CA-2.2 (error)** Dado que no existe la tarea 99, cuando hago `GET /api/todos/99`, entonces recibo `404` con el mensaje `Todo con id 99 no existe`.
- **CA-2.3 (error)** Dado cualquier estado, cuando hago `GET /api/todos/abc`, entonces recibo `400` con el mensaje `El id debe ser un número`.

### Op. 3 — Crear una tarea

- **CA-3.1** Dado un cuerpo `{"title": "  Estudiar  "}`, cuando hago `POST /api/todos`, entonces recibo `201`, la cabecera `Location` con la ruta de la nueva tarea, `title` igual a `"Estudiar"`, `completed` en `false`, un `id` nuevo y una `createdAt` con la hora actual.
- **CA-3.2** Dado un cuerpo que incluye `"id": 500` y `"createdAt": "2030-01-01T00:00:00-05:00"`, cuando hago `POST /api/todos`, entonces la tarea se crea con un `id` y una `createdAt` asignados por el servidor, no con los enviados.
- **CA-3.3 (error)** Dado un cuerpo `{"title": "   "}`, cuando hago `POST /api/todos`, entonces recibo `400` con el mensaje `El título es obligatorio` y no se crea ninguna tarea.
- **CA-3.4 (error)** Dado un cuerpo con un `title` de 101 caracteres, cuando hago `POST /api/todos`, entonces recibo `400` con el mensaje `El título no puede superar 100 caracteres`.
- **CA-3.5 (error)** Dado un cuerpo que no es JSON válido, cuando hago `POST /api/todos`, entonces recibo `400` con el mensaje `El cuerpo de la petición no es válido` y la respuesta no muestra detalles internos.

### Op. 4 — Editar una tarea

- **CA-4.1** Dado que existe la tarea 1 sin completar, cuando hago `PUT /api/todos/1` con `{"title": "Nuevo", "completed": true}`, entonces recibo `200`, el título es `"Nuevo"`, `completed` es `true`, `description` es `null`, y el `id` y la `createdAt` no cambiaron.
- **CA-4.2** Dado que la tarea 2 está completada, cuando hago `PUT /api/todos/2` con un cuerpo válido, entonces recibo `200` y la tarea se actualiza.
- **CA-4.3 (error)** Dado que no existe la tarea 99, cuando hago `PUT /api/todos/99` con un cuerpo válido, entonces recibo `404` con el mensaje `Todo con id 99 no existe`.
- **CA-4.4 (error)** Dado que existe la tarea 1, cuando hago `PUT /api/todos/1` con `{"title": ""}`, entonces recibo `400` con el mensaje `El título es obligatorio` y la tarea 1 queda como estaba.

### Op. 5 — Marcar/desmarcar como completada

- **CA-5.1** Dado que la tarea 1 tiene `completed` en `false`, cuando hago `PATCH /api/todos/1/toggle`, entonces recibo `200` con `completed` en `true` y los demás campos sin cambios.
- **CA-5.2** Dado que la tarea 1 tiene `completed` en `true`, cuando hago `PATCH /api/todos/1/toggle`, entonces recibo `200` con `completed` en `false`.
- **CA-5.3 (error)** Dado que no existe la tarea 99, cuando hago `PATCH /api/todos/99/toggle`, entonces recibo `404` con el mensaje `Todo con id 99 no existe`.

### Op. 6 — Eliminar una tarea

- **CA-6.1** Dado que existe la tarea 3, cuando hago `DELETE /api/todos/3`, entonces recibo `204` sin cuerpo y un `GET /api/todos/3` posterior devuelve `404`.
- **CA-6.2 (error)** Dado que la tarea 3 ya fue borrada, cuando hago `DELETE /api/todos/3` otra vez, entonces recibo `404` con el mensaje `Todo con id 3 no existe`.
- **CA-6.3 (error)** Dado cualquier estado, cuando hago `DELETE /api/todos/abc`, entonces recibo `400` con el mensaje `El id debe ser un número`.

### Transversal

- **CA-T.1 (error)** Dado un error inesperado en el servidor, cuando hago cualquier petición, entonces recibo `500` con el mensaje `Error interno del servidor` y la respuesta no incluye stack traces, nombres de clases ni SQL.
