package edu.usta.todos.exception;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import edu.usta.todos.dto.ErrorResponse;

/**
 * Traduce toda excepción al formato de error del contrato (regla 10).
 * Ningún mensaje de Java, Hibernate o Postgres llega al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Orden de los mensajes cuando hay varios errores de validación. */
    private static final List<String> ORDEN_CAMPOS = List.of("title", "description", "completed");

    @ExceptionHandler(TodoNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(TodoNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparingInt(GlobalExceptionHandler::posicion))
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Otra petición borró o cambió la tarea mientras esta la editaba (ej. PUT y DELETE a la vez).
     * Sin este manejador terminaba en 500.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> conflict(OptimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT,
                "La tarea cambió o fue eliminada por otra petición al mismo tiempo. Vuelve a cargarla e inténtalo de nuevo");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido");
    }

    /** Un id que no es número (ej. /api/todos/abc) o que no cabe en un long. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, "El id debe ser un número");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como application/json");
    }

    /** Cualquier otra cosa: el detalle va solo al log del servidor. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Error inesperado", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private static int posicion(FieldError error) {
        int i = ORDEN_CAMPOS.indexOf(error.getField());
        return i < 0 ? ORDEN_CAMPOS.size() : i;
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
