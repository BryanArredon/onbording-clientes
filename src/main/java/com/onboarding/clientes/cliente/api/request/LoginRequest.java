package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales para inicio de sesión (primer factor)")
public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato de correo es inválido")
        @Size(max = 100)
        @Schema(example = "juan.manzano@ejemplo.com")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128)
        @Schema(example = "Segura123!")
        String contrasenia,

        @Pattern(regexp = "^(WEB|MOVIL)$", message = "El canal debe ser WEB o MOVIL")
        @Schema(description = "Canal desde el que se inicia sesión (por defecto WEB)", example = "WEB")
        String canal) {

    @Override
    public String toString() {
        return "LoginRequest[correo=" + correo + ", contrasenia=***, canal=" + canal + "]";
    }
}
