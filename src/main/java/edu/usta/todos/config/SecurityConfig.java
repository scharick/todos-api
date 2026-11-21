package edu.usta.todos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import edu.usta.todos.dto.TodoResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security (contrato §1, "Autenticación").
 * Leer es público; crear, editar, marcar y eliminar exigen HTTP Basic.
 * Todo lo que no está en el contrato exige autenticación (cerrado por defecto).
 */
@Configuration
public class SecurityConfig {

    static final String LOGIN_REQUERIDO = "Debes iniciar sesión para hacer esto";
    static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";
    static final String ACCESO_DENEGADO = "No tienes permiso para hacer esto";
    static final int LONGITUD_MINIMA_PASSWORD = 12;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API sin cookies ni sesión: la credencial viaja en cada petición (Authorization),
                // así que no hay CSRF que proteger.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/todos", "/api/todos/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    /**
     * 401 con el formato del contrato. No envía "WWW-Authenticate: Basic" para que el
     * navegador no abra su ventana de login: el front tiene su propio formulario.
     */
    private static AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            boolean envioCredenciales = request.getHeader("Authorization") != null;
            escribirError(response, HttpStatus.UNAUTHORIZED,
                    envioCredenciales ? CREDENCIALES_INVALIDAS : LOGIN_REQUERIDO);
        };
    }

    private static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> escribirError(response, HttpStatus.FORBIDDEN, ACCESO_DENEGADO);
    }

    /** Los mensajes son constantes de esta clase (nunca texto del usuario), así que no hace falta escaparlos. */
    private static void escribirError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(status.value(), status.getReasonPhrase(), message,
                        OffsetDateTime.now(TodoResponse.ZONA)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Un solo usuario, tomado de APP_USER y APP_PASSWORD. Sin ellos la app no arranca. */
    @Bean
    UserDetailsService userDetailsService(
            @Value("${app.security.user}") String usuario,
            @Value("${app.security.password}") String password,
            PasswordEncoder encoder) {
        validarCredenciales(usuario, password);
        return new InMemoryUserDetailsManager(User.withUsername(usuario.strip())
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    static void validarCredenciales(String usuario, String password) {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Faltan las variables APP_USER y APP_PASSWORD: la app no arranca sin usuario");
        }
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new IllegalStateException(
                    "APP_PASSWORD debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres");
        }
    }

    /** CORS solo para los orígenes de CORS_ORIGINS (SPECS §8, fugas F1 y F11). Nunca "*". */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(","))
                .map(String::strip)
                .filter(o -> !o.isEmpty() && !o.equals("*"))
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
