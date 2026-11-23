package com.onboarding.clientes.cliente.application.dto;

import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import java.time.Instant;
import java.util.UUID;

/** Estado de la sesion vigente del usuario autenticado. */
public record SesionDto(
        UUID id,
        UUID usuarioId,
        CanalSesion canal,
        Instant creadaEn,
        Instant ultimaActividad,
        Instant expiraEn,
        Instant expiraPorInactividadEn) {}
