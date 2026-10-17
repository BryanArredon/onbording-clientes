package com.onboarding.clientes.cliente.application.query;

import java.util.UUID;

/**
 * Consulta unificada de cuentas bancarias: un unico comando con filtros combinables.
 */
public record ConsultaCuenta(
        String numeroCuenta,
        UUID clienteId,
        Boolean soloActivas,
        String cursor,
        Integer limite) {

    /** Consulta sin filtros: el servicio la limita al cliente propio cuando el rol no es {@code ADMIN}. */
    public static ConsultaCuenta soloPropias(String cursor, Integer limite) {
        return new ConsultaCuenta(null, null, Boolean.TRUE, cursor, limite);
    }
}
