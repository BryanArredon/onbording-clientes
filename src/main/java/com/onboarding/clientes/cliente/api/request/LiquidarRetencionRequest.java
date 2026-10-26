package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Solicitud de liquidacion en firme de una retencion bancaria")
public record LiquidarRetencionRequest(
        @NotNull(message = "El folioMovimiento es obligatorio")
        @Schema(description = "Folio UUID de la retencion bancaria")
        UUID folioMovimiento,

        @Size(max = 80, message = "La referencia de autorizacion no puede exceder 80 caracteres")
        @Schema(example = "AUTH-998877")
        String referenciaAutorizacion) {}
