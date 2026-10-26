package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

@Schema(description = "Solicitud para configurar o actualizar el PIN de autorizacion de 4 digitos")
public record CambiarPinRequest(
        @NotNull(message = "El identificador del usuario es obligatorio")
        @Schema(description = "Identificador unico del usuario")
        UUID usuarioId,

        @NotBlank(message = "La contrasena actual es obligatoria")
        @Schema(example = "Segura123!")
        String contraseniaActual,

        @NotBlank(message = "El PIN de autorizacion es obligatorio")
        @Pattern(regexp = "^[0-9]{4}$", message = "El PIN debe tener exactamente 4 digitos numericos")
        @Schema(example = "4829")
        String nuevoPin) {

    @Override
    public String toString() {
        return "CambiarPinRequest[usuarioId=" + usuarioId + ", contraseniaActual=***, nuevoPin=****]";
    }
}
