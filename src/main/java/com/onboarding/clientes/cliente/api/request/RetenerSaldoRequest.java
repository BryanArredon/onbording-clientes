package com.onboarding.clientes.cliente.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Solicitud de retencion atomica de fondos (Hold) con validacion de PIN de 4 digitos")
public record RetenerSaldoRequest(
        @NotBlank(message = "El numero de cuenta es obligatorio")
        @Pattern(regexp = "^[0-9]{10}$", message = "El numero de cuenta debe tener exactamente 10 digitos")
        @Schema(example = "0000000018")
        String numeroCuenta,

        @NotBlank(message = "El PIN de autorizacion es obligatorio")
        @Pattern(regexp = "^[0-9]{4}$", message = "El PIN de autorizacion debe tener exactamente 4 digitos")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(example = "4829")
        String pinAutorizacion,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @Schema(example = "100.00")
        BigDecimal monto,

        @PositiveOrZero(message = "La comision no puede ser negativa")
        @Schema(example = "0.00")
        BigDecimal comision,

        @Size(min = 3, max = 3, message = "La moneda debe ser un codigo ISO 4217 de 3 letras")
        @Schema(example = "MXN")
        String moneda,

        @NotBlank(message = "El concepto de la retencion es obligatorio")
        @Size(max = 160, message = "El concepto no puede exceder 160 caracteres")
        @Schema(example = "Pago de servicio TELCEL - Recarga Telcel $100")
        String concepto,

        @NotBlank(message = "La clave de idempotencia es obligatoria")
        @Size(min = 3, max = 64, message = "La clave de idempotencia debe tener entre 3 y 64 caracteres")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        String idempotencyKey) {

    @Override
    public String toString() {
        return "RetenerSaldoRequest[numeroCuenta=******"
                + (numeroCuenta != null && numeroCuenta.length() >= 4
                        ? numeroCuenta.substring(numeroCuenta.length() - 4)
                        : "****")
                + ", pinAutorizacion=****, monto=" + monto
                + ", comision=" + comision
                + ", idempotencyKey=" + idempotencyKey + "]";
    }
}
