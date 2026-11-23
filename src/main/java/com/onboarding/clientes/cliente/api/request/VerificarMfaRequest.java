package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Segundo factor: código de 6 dígitos de la app autenticadora")
public record VerificarMfaRequest(
        @NotBlank(message = "El desafío es obligatorio")
        @Size(max = 2048)
        @Schema(description = "tokenDesafio devuelto por /auth/login")
        String tokenDesafio,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener 6 dígitos")
        @Schema(example = "123456")
        String codigo,

        @Schema(description = "true para cerrar la sesión abierta en otro canal y continuar", example = "false")
        Boolean cerrarSesionAnterior) {

    @Override
    public String toString() {
        return "VerificarMfaRequest[codigo=******, cerrarSesionAnterior=" + cerrarSesionAnterior + "]";
    }
}
