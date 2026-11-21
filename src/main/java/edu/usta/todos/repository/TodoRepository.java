package edu.usta.todos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.usta.todos.model.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findAllByOrderByIdAsc();

    List<Todo> findByCompletedOrderByIdAsc(boolean completed);

    /**
     * Borra en UNA sentencia y devuelve cuántas filas borró (0 o 1).
     * Con peticiones simultáneas solo una obtiene 1; las demás obtienen 0 → 404.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Todo t where t.id = :id")
    int borrarPorId(@Param("id") long id);

    /**
     * Invierte completed en UNA sentencia: Postgres bloquea la fila, así que N toggles
     * simultáneos aplican los N cambios (no se pierde ninguno).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Todo t set t.completed = case when t.completed = true then false else true end where t.id = :id")
    int alternarCompleted(@Param("id") long id);
}
