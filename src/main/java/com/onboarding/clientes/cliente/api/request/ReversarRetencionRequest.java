package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Solicitud de reverso compensatorio idempotente de una retencion bancaria")
public record ReversarRetencionRequest(
        @NotNull(message = "El folioMovimiento es obligatorio")
        @Schema(description = "Folio UUID de la retencion bancaria")
        UUID folioMovimiento,

        @NotBlank(message = "El motivo del reverso es obligatorio")
        @Size(max = 200, message = "El motivo del reverso no puede exceder 200 caracteres")
        @Schema(example = "Rechazo definitivo del proveedor de servicios")
        String motivo) {}
