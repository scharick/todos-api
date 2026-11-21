package edu.usta.todos.exception;

/** Regla 9: la tarea no existe → 404. */
public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(long id) {
        super("Todo con id " + id + " no existe");
    }
}
