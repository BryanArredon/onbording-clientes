package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import java.util.UUID;

/**
 * Criterios ya normalizados por el servicio para la busqueda dinamica de usuarios de acceso.
 *
 * <p>Cada campo es opcional; el adaptador de persistencia construye la consulta SQL unicamente con
 * los criterios presentes (consulta dinamica).
 */
public record CriteriosBusquedaUsuario(
        UUID id,
        Correo correo,
        UUID clienteId,
        Boolean activo) {}
