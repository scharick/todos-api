package edu.usta.todos.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.usta.todos.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reemplaza la página de error por defecto de Spring Boot (/error), que respondía con otro
 * formato e incluía la ruta. Aquí llegan los errores que ocurren antes del controlador,
 * por ejemplo una URL rechazada por el firewall de Spring Security (/api/todos//toggle).
 */
@RestController
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer c ? HttpStatus.resolve(c) : null;
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = switch (status) {
            case BAD_REQUEST -> "La petición no es válida";
            case UNAUTHORIZED -> "Debes iniciar sesión para hacer esto";
            case FORBIDDEN -> "No tienes permiso para hacer esto";
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Método no permitido";
            default -> status.is4xxClientError() ? "La petición no es válida" : "Error interno del servidor";
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
