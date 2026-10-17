package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Criterios de busqueda de cuentas bancarias en un unico endpoint.
 *
 * <p>Todos los filtros son opcionales y pueden combinarse entre si. La respuesta incluye el saldo
 * disponible, por lo que no existe un endpoint separado para consultar saldos.
 */
@Schema(description = "Criterios combinables para la consulta de cuentas bancarias")
public record ConsultaCuentaRequest(
        @Pattern(regexp = "^[0-9]{10}$", message = "El número de cuenta debe contener exactamente 10 dígitos")
        @Schema(example = "0000000018") String numeroCuenta,

        @Schema(description = "Identificador del cliente titular de la cuenta")
        UUID clienteId,

        @Schema(description = "Si es true, únicamente cuentas con estatus ACTIVA")
        Boolean soloActivas,

        @Schema(description = "Cursor opaco de paginación devuelto por la página anterior")
        String cursor,

        @Min(value = 1, message = "El límite debe ser al menos 1") @Max(value = 100, message = "El límite no puede exceder 100")
        @Schema(example = "20") Integer limite) {}
