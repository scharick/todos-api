package edu.usta.todos.exception;

/** Error 400 con un mensaje del contrato que se puede mostrar al usuario. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
