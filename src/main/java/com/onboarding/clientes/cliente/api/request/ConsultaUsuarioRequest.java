package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Criterios de busqueda de usuarios de acceso en un unico endpoint.
 *
 * <p>Todos los filtros son opcionales y pueden combinarse entre si.
 */
@Schema(description = "Criterios combinables para la consulta de usuarios")
public record ConsultaUsuarioRequest(
        @Schema(description = "Identificador unico del usuario")
        UUID id,

        @Email(message = "El correo electrónico debe tener un formato válido") @Size(max = 100)
        @Schema(example = "alejandro.morales@fintechmail.com") String correo,

        @Schema(description = "Identificador del cliente dueño de la cuenta de acceso")
        UUID clienteId,

        @Schema(description = "Filtra por estatus activo/inactivo del usuario")
        Boolean activo,

        @Schema(description = "Cursor opaco de paginación devuelto por la página anterior")
        String cursor,

        @Min(value = 1, message = "El límite debe ser al menos 1") @Max(value = 100, message = "El límite no puede exceder 100")
        @Schema(example = "20") Integer limite) {}
