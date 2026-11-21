package edu.usta.todos.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS solo para los orígenes de CORS_ORIGINS (SPECS §8, fuga F1). Nunca "*". */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origins;

    public CorsConfig(@Value("${app.cors.origins}") String origins) {
        this.origins = Arrays.stream(origins.split(","))
                .map(String::strip)
                .filter(o -> !o.isEmpty() && !o.equals("*"))
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("Content-Type")
                .exposedHeaders("Location");
    }
}
