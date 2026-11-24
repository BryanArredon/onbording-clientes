package com.onboarding.clientes.cliente.infrastructure.notificaciones;

import java.util.Map;

/** Notificacion que se envia al microservicio de pagos para la bandeja y el correo del cliente. */
public record NotificacionExterna(
        String email,
        String tipo,
        String titulo,
        String cuerpo,
        String dedupeKey,
        Map<String, Object> datos,
        boolean correo) {
}
