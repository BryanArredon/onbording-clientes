package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import java.util.UUID;

/**
 * Criterios ya normalizados por el servicio para la busqueda dinamica de cuentas bancarias.
 *
 * <p>Cada campo es opcional; el adaptador de persistencia construye la consulta SQL unicamente con
 * los criterios presentes (consulta dinamica).
 */
public record CriteriosBusquedaCuenta(
        NumeroCuenta numero,
        UUID clienteId,
        Boolean soloActivas) {}
