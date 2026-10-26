package com.onboarding.clientes.cliente.application.command;

import java.util.UUID;

/** Comando para proteger una retencion en estado REQUIERE_REVISION. */
public record ComandoRevisionRetencion(
        UUID folioMovimiento,
        String motivo) {}
