package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Solicitud para escalar una retencion bancaria a REQUIERE_REVISION")
public record RevisionRetencionRequest(
        @NotNull(message = "El folioMovimiento es obligatorio")
        @Schema(description = "Folio UUID de la retencion bancaria")
        UUID folioMovimiento,

        @NotBlank(message = "El motivo de revision es obligatorio")
        @Size(max = 200, message = "El motivo no puede exceder 200 caracteres")
        @Schema(example = "Confirmacion indeterminada tras agotar ventana escalonada")
        String motivo) {}
