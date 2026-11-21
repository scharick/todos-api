package edu.usta.todos.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.usta.todos.dto.TodoRequest;
import edu.usta.todos.dto.TodoResponse;
import edu.usta.todos.exception.BadRequestException;
import edu.usta.todos.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Operaciones 1 a 6 de docs/contratos/api.md. */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    static final String ID_INVALIDO = "El id debe ser un número";

    private final TodoService service;

    /** Op. 1. Solo acepta "true" o "false" (no "yes", "1", "on"). */
    @GetMapping
    public List<TodoResponse> list(@RequestParam(required = false) String completed) {
        Boolean filtro = null;
        if (completed != null) {
            if (completed.equalsIgnoreCase("true")) {
                filtro = true;
            } else if (completed.equalsIgnoreCase("false")) {
                filtro = false;
            } else {
                throw new BadRequestException("El parámetro completed debe ser true o false");
            }
        }
        return service.list(filtro);
    }

    /** Op. 2 */
    @GetMapping("/{id}")
    public TodoResponse get(@PathVariable long id) {
        return service.get(validId(id));
    }

    /** Op. 3 */
    @PostMapping
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody TodoRequest request) {
        TodoResponse creada = service.create(request);
        return ResponseEntity.created(URI.create("/api/todos/" + creada.id())).body(creada);
    }

    /** Op. 4. Orden del contrato: id (400), cuerpo (400) y luego existencia (404). */
    @PutMapping("/{id}")
    public TodoResponse update(@PathVariable long id, @Valid @RequestBody TodoRequest request) {
        return service.update(validId(id), request);
    }

    /** Op. 5 */
    @PatchMapping("/{id}/toggle")
    public TodoResponse toggle(@PathVariable long id) {
        return service.toggle(validId(id));
    }

    /** Op. 6 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(validId(id));
        return ResponseEntity.noContent().build();
    }

    private static long validId(long id) {
        if (id <= 0) {
            throw new BadRequestException(ID_INVALIDO);
        }
        return id;
    }
}
