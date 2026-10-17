package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.cliente.domain.valueobject.Curp;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.Rfc;
import java.time.Instant;
import java.util.UUID;

/**
 * Criterios ya normalizados por el servicio para la busqueda dinamica de clientes.
 *
 * <p>Cada campo es opcional; el adaptador de persistencia construye la consulta SQL unicamente con
 * los criterios presentes (consulta dinamica).
 */
public record CriteriosBusquedaCliente(
        UUID id,
        Curp curp,
        Rfc rfc,
        Correo correo,
        NumeroCuenta numeroCuenta,
        Boolean soloActivos,
        Instant desde,
        Instant hasta) {}
