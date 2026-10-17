package com.onboarding.clientes.cliente.application.query;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consulta unificada de clientes: un unico comando con filtros combinables.
 *
 * <p>El servicio traduce los textos recibidos a value objects de dominio antes de consultar el
 * repositorio; si algun filtro tiene formato invalido el dominio rechaza la operacion.
 */
public record ConsultaCliente(
        UUID id,
        String curp,
        String rfc,
        String correo,
        String numeroCuenta,
        Boolean soloActivos,
        LocalDate desde,
        LocalDate hasta,
        String cursor,
        Integer limite) {

    /** Consulta sin filtros: el servicio la limita al cliente propio cuando el rol no es {@code ADMIN}. */
    public static ConsultaCliente soloPropio(Boolean soloActivos, String cursor, Integer limite) {
        return new ConsultaCliente(null, null, null, null, null, soloActivos, null, null, cursor, limite);
    }
}
