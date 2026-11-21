package edu.usta.todos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.usta.todos.dto.TodoRequest;
import edu.usta.todos.dto.TodoResponse;
import edu.usta.todos.exception.TodoNotFoundException;
import edu.usta.todos.service.TodoService;

/** Criterios de aceptación del back (SPECS §12) en la capa API. */
@WebMvcTest(TodoController.class)
@TestPropertySource(properties = "app.cors.origins=https://todos-front.vercel.app")
class TodoControllerTest {

    private static final OffsetDateTime FECHA = OffsetDateTime.of(2026, 11, 20, 10, 30, 0, 0, ZoneOffset.ofHours(-5));
    private static final TodoResponse TAREA_1 = new TodoResponse(1L, "Estudiar Spring Boot", "Repasar controladores y JPA", false, FECHA);
    private static final TodoResponse TAREA_2 = new TodoResponse(2L, "Hacer el laboratorio", null, true, FECHA);
    private static final TodoResponse TAREA_3 = new TodoResponse(3L, "Desplegar en Render", null, false, FECHA);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TodoService service;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---------- Op. 1 ----------

    @Test
    void ca_1_1_lista_todas_las_tareas() throws Exception {
        when(service.list(null)).thenReturn(List.of(TAREA_1, TAREA_2, TAREA_3));

        mvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-20T10:30:00-05:00"));
    }

    @Test
    void ca_1_2_filtra_por_completed() throws Exception {
        when(service.list(false)).thenReturn(List.of(TAREA_1, TAREA_3));

        mvc.perform(get("/api/todos").param("completed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(1, 3)));
    }

    @Test
    void ca_1_3_lista_vacia() throws Exception {
        when(service.list(null)).thenReturn(List.of());

        mvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void ca_1_4_error_completed_invalido() throws Exception {
        mvc.perform(get("/api/todos").param("completed", "si"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("El parámetro completed debe ser true o false"))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(service);
    }

    @Test
    void completed_solo_acepta_true_o_false() throws Exception {
        mvc.perform(get("/api/todos").param("completed", "1"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Op. 2 ----------

    @Test
    void ca_2_1_ver_una_tarea() throws Exception {
        when(service.get(1L)).thenReturn(TAREA_1);

        mvc.perform(get("/api/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Estudiar Spring Boot"))
                .andExpect(jsonPath("$.description").value("Repasar controladores y JPA"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.createdAt").value("2026-11-20T10:30:00-05:00"));
    }

    @Test
    void ca_2_2_error_tarea_inexistente() throws Exception {
        when(service.get(99L)).thenThrow(new TodoNotFoundException(99));

        mvc.perform(get("/api/todos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo con id 99 no existe"));
    }

    @Test
    void ca_2_3_error_id_no_numerico() throws Exception {
        mvc.perform(get("/api/todos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El id debe ser un número"));
    }

    @Test
    void id_cero_o_negativo_es_invalido() throws Exception {
        mvc.perform(get("/api/todos/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El id debe ser un número"));
        verifyNoInteractions(service);
    }

    // ---------- Op. 3 ----------

    @Test
    void ca_3_1_crea_con_titulo_recortado_y_completed_false() throws Exception {
        when(service.create(any())).thenReturn(new TodoResponse(4L, "Estudiar", null, false, FECHA));

        mvc.perform(json(post("/api/todos"), "{\"title\": \"  Estudiar  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/todos/4"))
                .andExpect(jsonPath("$.id").value(4));

        ArgumentCaptor<TodoRequest> captor = ArgumentCaptor.forClass(TodoRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Estudiar");
        assertThat(captor.getValue().completedOrDefault()).isFalse();
    }

    @Test
    void ca_3_2_ignora_id_y_createdAt_del_cliente() throws Exception {
        when(service.create(any())).thenReturn(new TodoResponse(5L, "Tarea", null, false, FECHA));

        mvc.perform(json(post("/api/todos"),
                "{\"id\": 500, \"createdAt\": \"2030-01-01T00:00:00-05:00\", \"title\": \"Tarea\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void ca_3_3_error_titulo_solo_espacios() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"title\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El título es obligatorio"));
        verifyNoInteractions(service);
    }

    @Test
    void ca_3_4_error_titulo_de_101_caracteres() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"title\": \"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El título no puede superar 100 caracteres"));
        verifyNoInteractions(service);
    }

    @Test
    void titulo_de_100_caracteres_con_espacios_alrededor_es_valido() throws Exception {
        when(service.create(any())).thenReturn(TAREA_1);

        mvc.perform(json(post("/api/todos"), "{\"title\": \"  " + "a".repeat(100) + "  \"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void error_descripcion_de_501_caracteres() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"title\": \"Ok\", \"description\": \"" + "d".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La descripción no puede superar 500 caracteres"));
    }

    @Test
    void varios_errores_se_unen_con_punto_y_coma() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"description\": \"" + "d".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("El título es obligatorio; La descripción no puede superar 500 caracteres"));
    }

    @Test
    void ca_3_5_error_json_invalido_sin_detalles_internos() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
        verifyNoInteractions(service);
    }

    @Test
    void error_tipo_incorrecto_en_completed() throws Exception {
        mvc.perform(json(post("/api/todos"), "{\"title\": \"Ok\", \"completed\": \"si\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));
    }

    // ---------- Op. 4 ----------

    @Test
    void ca_4_1_edita_una_tarea() throws Exception {
        when(service.update(eq(1L), any()))
                .thenReturn(new TodoResponse(1L, "Nuevo", null, true, FECHA));

        mvc.perform(json(put("/api/todos/1"), "{\"title\": \"Nuevo\", \"completed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nuevo"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void ca_4_3_error_editar_tarea_inexistente() throws Exception {
        when(service.update(eq(99L), any())).thenThrow(new TodoNotFoundException(99));

        mvc.perform(json(put("/api/todos/99"), "{\"title\": \"Nuevo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo con id 99 no existe"));
    }

    @Test
    void ca_4_4_error_editar_con_titulo_vacio() throws Exception {
        mvc.perform(json(put("/api/todos/1"), "{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El título es obligatorio"));
        verify(service, never()).update(anyLong(), any());
    }

    // ---------- Op. 5 ----------

    @Test
    void ca_5_1_marca_como_completada() throws Exception {
        when(service.toggle(1L)).thenReturn(new TodoResponse(1L, "Estudiar Spring Boot", null, true, FECHA));

        mvc.perform(patch("/api/todos/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void ca_5_3_error_toggle_tarea_inexistente() throws Exception {
        when(service.toggle(99L)).thenThrow(new TodoNotFoundException(99));

        mvc.perform(patch("/api/todos/99/toggle"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo con id 99 no existe"));
    }

    // ---------- Op. 6 ----------

    @Test
    void ca_6_1_elimina_una_tarea() throws Exception {
        mvc.perform(delete("/api/todos/3"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).delete(3L);
    }

    @Test
    void ca_6_2_error_eliminar_dos_veces() throws Exception {
        doThrow(new TodoNotFoundException(3)).when(service).delete(3L);

        mvc.perform(delete("/api/todos/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo con id 3 no existe"));
    }

    @Test
    void ca_6_3_error_eliminar_id_no_numerico() throws Exception {
        mvc.perform(delete("/api/todos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El id debe ser un número"));
    }

    // ---------- Transversal ----------

    @Test
    void ca_t_1_error_inesperado_no_expone_detalles() throws Exception {
        when(service.list(null)).thenThrow(new IllegalStateException(
                "ERROR: relation \"todos\" violates check constraint todos_title_recortado; password=secreto"));

        mvc.perform(get("/api/todos"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("todos_title_recortado"))))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void ruta_inexistente_responde_con_formato_del_contrato() throws Exception {
        mvc.perform(get("/api/otra-cosa"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"));
    }

    @Test
    void metodo_no_soportado_responde_405() throws Exception {
        mvc.perform(post("/api/todos/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Método no permitido"));
    }

    // ---------- CORS (fuga F1) ----------

    @Test
    void cors_permite_el_origen_configurado() throws Exception {
        mvc.perform(options("/api/todos")
                        .header("Origin", "https://todos-front.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://todos-front.vercel.app"));
    }

    @Test
    void cors_rechaza_otros_origenes() throws Exception {
        mvc.perform(options("/api/todos")
                        .header("Origin", "https://sitio-malicioso.com")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
