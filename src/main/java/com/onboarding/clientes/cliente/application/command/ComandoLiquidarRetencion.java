package com.onboarding.clientes.cliente.application.command;

import java.util.UUID;

/** Comando para liquidar (capturar en firme) una retencion bancaria previa. */
public record ComandoLiquidarRetencion(
        UUID folioMovimiento,
        String referenciaAutorizacion) {}
