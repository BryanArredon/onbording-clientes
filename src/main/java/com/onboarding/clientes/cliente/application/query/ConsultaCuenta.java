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
        Integer limite) {}
