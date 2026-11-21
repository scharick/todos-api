package edu.usta.todos.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** La app nunca arranca sin usuario o con una contraseña débil (no hay contraseña por defecto). */
class SecurityConfigTest {

    @Test
    void sin_usuario_o_sin_contrasena_no_arranca() {
        assertThatThrownBy(() -> SecurityConfig.validarCredenciales("", "una-clave-larga-123"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.validarCredenciales("admin", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.validarCredenciales(null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contrasena_corta_no_arranca() {
        assertThatThrownBy(() -> SecurityConfig.validarCredenciales("admin", "corta123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");
    }

    @Test
    void credenciales_validas_arrancan() {
        assertThatCode(() -> SecurityConfig.validarCredenciales("admin", "una-clave-larga-123"))
                .doesNotThrowAnyException();
    }
}
