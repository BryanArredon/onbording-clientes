package com.onboarding.clientes.cliente.application.command;

import java.util.UUID;

/** Comando para reversar de forma compensatoria e idempotente una retencion bancaria. */
public record ComandoReversarRetencion(
        UUID folioMovimiento,
        String motivo) {}
