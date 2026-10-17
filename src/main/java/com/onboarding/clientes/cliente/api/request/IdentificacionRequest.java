package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Identificacion del recurso sobre el que se ejecuta una operacion.
 *
 * <p>Permite que operaciones como la baja lógica reciban el identificador en el cuerpo de la
 * peticion, conforme a la restriccion de no usar {@code @PathVariable}.
 */
@Schema(description = "Identificador del recurso sobre el que se ejecuta la operacion")
public record IdentificacionRequest(
        @NotNull(message = "El identificador es obligatorio")
        @Schema(description = "Identificador unico del recurso") UUID id) {}
