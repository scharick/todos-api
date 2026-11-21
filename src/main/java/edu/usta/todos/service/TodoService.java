package edu.usta.todos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.usta.todos.dto.TodoRequest;
import edu.usta.todos.dto.TodoResponse;
import edu.usta.todos.exception.TodoNotFoundException;
import edu.usta.todos.model.Todo;
import edu.usta.todos.repository.TodoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;

    @Transactional(readOnly = true)
    public List<TodoResponse> list(Boolean completed) {
        List<Todo> todos = completed == null
                ? repository.findAllByOrderByIdAsc()
                : repository.findByCompletedOrderByIdAsc(completed);
        return todos.stream().map(TodoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TodoResponse get(long id) {
        return TodoResponse.from(find(id));
    }

    @Transactional
    public TodoResponse create(TodoRequest request) {
        Todo todo = new Todo();
        apply(todo, request);
        return TodoResponse.from(repository.saveAndFlush(todo));
    }

    /** Reemplazo completo; el id sale de la ruta y createdAt no cambia (reglas 5 y 7). */
    @Transactional
    public TodoResponse update(long id, TodoRequest request) {
        Todo todo = find(id);
        apply(todo, request);
        return TodoResponse.from(repository.saveAndFlush(todo));
    }

    /** Atómico: no lee y luego escribe, así que toggles simultáneos no se pisan. */
    @Transactional
    public TodoResponse toggle(long id) {
        if (repository.alternarCompleted(id) == 0) {
            throw new TodoNotFoundException(id);
        }
        return TodoResponse.from(find(id));
    }

    /** Borrado físico y atómico; borrar dos veces (o a la vez) da 404 (regla 9). */
    @Transactional
    public void delete(long id) {
        if (repository.borrarPorId(id) == 0) {
            throw new TodoNotFoundException(id);
        }
    }

    private Todo find(long id) {
        return repository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    }

    private static void apply(Todo todo, TodoRequest request) {
        todo.setTitle(request.title());
        todo.setDescription(request.description());
        todo.setCompleted(request.completedOrDefault());
    }
}
