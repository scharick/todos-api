package edu.usta.todos.model;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabla public.todos (docs/datos/modelo.md). */
@Entity
@Table(name = "todos")
@Getter
@Setter
@NoArgsConstructor
public class Todo {

    /** Reglas 3, 4 y 7: lo asigna la BD (GENERATED ALWAYS) y no tiene setter. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    /** Reglas 4 y 5: se asigna una sola vez al crear y nunca se actualiza. */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(lombok.AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @PrePersist
    void asignarFechaDeCreacion() {
        // Postgres guarda microsegundos: se recorta para que POST y GET devuelvan la misma fecha
        this.createdAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
