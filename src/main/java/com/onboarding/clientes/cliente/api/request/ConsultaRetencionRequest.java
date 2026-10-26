package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Solicitud de consulta de una retencion bancaria por folio o clave de idempotencia")
public record ConsultaRetencionRequest(
        @Schema(description = "Folio UUID de la retencion")
        UUID folioMovimiento,

        @Size(min = 10, max = 10, message = "El numero de cuenta debe tener 10 digitos")
        @Schema(example = "0000000018")
        String numeroCuenta,

        @Size(min = 3, max = 64, message = "La clave de idempotencia debe tener entre 3 y 64 caracteres")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        String idempotencyKey) {}
