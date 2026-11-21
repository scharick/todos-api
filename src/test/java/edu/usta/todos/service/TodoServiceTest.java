package edu.usta.todos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.usta.todos.dto.TodoRequest;
import edu.usta.todos.dto.TodoResponse;
import edu.usta.todos.exception.TodoNotFoundException;
import edu.usta.todos.model.Todo;
import edu.usta.todos.repository.TodoRepository;

/** Reglas de SPECS §3 que viven en la capa Backend (modelo.md §7). */
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    TodoRepository repository;

    @InjectMocks
    TodoService service;

    private static Todo tarea(String title, boolean completed) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setCompleted(completed);
        return todo;
    }

    @Test
    void create_usa_completed_false_si_no_se_envia_y_descripcion_vacia_como_null() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        TodoResponse creada = service.create(new TodoRequest("  Estudiar  ", "   ", null));

        assertThat(creada.title()).isEqualTo("Estudiar");
        assertThat(creada.description()).isNull();
        assertThat(creada.completed()).isFalse();
    }

    @Test
    void update_reemplaza_campos_y_completed_por_defecto_es_false() {
        Todo existente = tarea("Viejo", true);
        existente.setDescription("Algo");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        TodoResponse editada = service.update(1L, new TodoRequest("Nuevo", null, null));

        assertThat(editada.title()).isEqualTo("Nuevo");
        assertThat(editada.description()).isNull();
        assertThat(editada.completed()).isFalse();
    }

    @Test
    void update_permite_editar_una_tarea_completada() {
        when(repository.findById(2L)).thenReturn(Optional.of(tarea("Hecha", true)));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.update(2L, new TodoRequest("Hecha y editada", null, true)).title())
                .isEqualTo("Hecha y editada");
    }

    @Test
    void update_de_tarea_inexistente_lanza_404_y_no_guarda() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new TodoRequest("X", null, null)))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessage("Todo con id 99 no existe");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void toggle_es_una_sola_sentencia_atomica_y_devuelve_el_estado_nuevo() {
        when(repository.alternarCompleted(1L)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(tarea("Estudiar", true)));

        assertThat(service.toggle(1L).completed()).isTrue();
        verify(repository).alternarCompleted(1L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void toggle_de_tarea_inexistente_lanza_404() {
        when(repository.alternarCompleted(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.toggle(99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessage("Todo con id 99 no existe");
    }

    @Test
    void delete_es_atomico_y_borra_una_sola_vez() {
        when(repository.borrarPorId(3L)).thenReturn(1);

        service.delete(3L);

        verify(repository).borrarPorId(3L);
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_que_no_borra_nada_lanza_404_aunque_sea_simultaneo() {
        // Con 10 DELETE a la vez, solo uno obtiene 1 fila; los demás obtienen 0
        when(repository.borrarPorId(3L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessage("Todo con id 3 no existe");
    }

    @Test
    void list_sin_filtro_y_con_filtro_usa_la_consulta_correcta() {
        service.list(null);
        service.list(true);

        verify(repository).findAllByOrderByIdAsc();
        verify(repository).findByCompletedOrderByIdAsc(true);
    }
}
