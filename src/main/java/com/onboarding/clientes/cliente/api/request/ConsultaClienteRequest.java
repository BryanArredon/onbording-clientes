package com.onboarding.clientes.cliente.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Criterios de busqueda de clientes en un unico endpoint.
 *
 * <p>Todos los filtros son opcionales y pueden combinarse entre si: el servidor construye la consulta
 * dinamica unicamente con los criterios recibidos. Ningun campo es controlado por el servidor.
 */
@Schema(description = "Criterios combinables para la consulta de clientes")
public record ConsultaClienteRequest(
        @Schema(description = "Identificador unico del cliente")
        UUID id,

        @Pattern(regexp = "^[A-Za-z]{4}[0-9]{6}[A-Za-z]{6}[0-9A-Za-z]{2}$", message = "Formato de CURP inválido")
        @Schema(example = "MOMA920814HGTNRD04") String curp,

        @Pattern(regexp = "^[A-Za-zÑ&]{4}[0-9]{6}[0-9A-Za-z]{3}$", message = "Formato de RFC inválido")
        @Schema(example = "MOMA9208148X2") String rfc,

        @Email(message = "El correo electrónico debe tener un formato válido") @Size(max = 100)
        @Schema(example = "alejandro.morales@fintechmail.com") String correo,

        @Pattern(regexp = "^[0-9]{10}$", message = "El número de cuenta debe contener exactamente 10 dígitos")
        @Schema(example = "0000000018") String numeroCuenta,

        @Schema(description = "Si es true, excluye a los clientes dados de baja")
        Boolean soloActivos,

        @Schema(description = "Inicio del rango de alta (inclusive)", example = "2026-01-01")
        LocalDate desde,

        @Schema(description = "Fin del rango de alta (inclusive)", example = "2026-01-31")
        LocalDate hasta,

        @Schema(description = "Cursor opaco de paginación devuelto por la página anterior")
        String cursor,

        @Min(value = 1, message = "El límite debe ser al menos 1") @Max(value = 100, message = "El límite no puede exceder 100")
        @Schema(example = "20") Integer limite) {}
