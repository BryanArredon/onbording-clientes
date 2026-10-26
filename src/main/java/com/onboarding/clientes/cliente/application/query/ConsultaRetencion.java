package com.onboarding.clientes.cliente.application.query;

import java.util.UUID;

/** Criterio de consulta de una retencion bancaria por folio o clave de idempotencia. */
public record ConsultaRetencion(
        UUID folioMovimiento,
        String numeroCuenta,
        String idempotencyKey) {}
