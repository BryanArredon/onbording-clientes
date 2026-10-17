package com.onboarding.clientes.cliente.application.query;

import java.util.UUID;

/**
 * Consulta unificada de usuarios de acceso: un unico comando con filtros combinables.
 */
public record ConsultaUsuario(
        UUID id,
        String correo,
        UUID clienteId,
        Boolean activo,
        String cursor,
        Integer limite) {}
