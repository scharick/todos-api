package edu.usta.todos.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import edu.usta.todos.model.Todo;

/** Objeto Todo del contrato §1. Fechas en hora de Colombia. */
public record TodoResponse(
        Long id,
        String title,
        String description,
        boolean completed,
        OffsetDateTime createdAt) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public static TodoResponse from(Todo todo) {
        OffsetDateTime createdAt = todo.getCreatedAt() == null
                ? null
                : todo.getCreatedAt().atZoneSameInstant(ZONA).toOffsetDateTime();
        return new TodoResponse(todo.getId(), todo.getTitle(), todo.getDescription(),
                todo.isCompleted(), createdAt);
    }
}
