package edu.usta.todos.dto;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;

/** Objeto de error del contrato §1. */
public record ErrorResponse(int status, String error, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message,
                OffsetDateTime.now(TodoResponse.ZONA));
    }
}
