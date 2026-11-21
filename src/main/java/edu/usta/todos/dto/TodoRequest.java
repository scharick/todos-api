package edu.usta.todos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST y PUT (contrato §1, "Cuerpo de tarea").
 * No tiene id ni createdAt: si el cliente los envía, se ignoran (regla 4).
 */
public record TodoRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 100, message = "El título no puede superar 100 caracteres")
        String title,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description,

        Boolean completed) {

    /** Se normaliza antes de validar: título recortado, descripción vacía = null. */
    public TodoRequest {
        title = title == null ? null : title.strip();
        description = description == null || description.isBlank() ? null : description;
    }

    public boolean completedOrDefault() {
        return completed != null && completed;
    }
}
