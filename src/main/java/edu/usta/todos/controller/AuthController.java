package edu.usta.todos.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Op. 7 del contrato: el front la usa para comprobar el usuario y la contraseña. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SesionResponse(String username) {
    }

    @GetMapping("/me")
    public SesionResponse me(Principal principal) {
        return new SesionResponse(principal.getName());
    }
}
