package com.onboarding.clientes.cliente.application.dto;

import com.onboarding.clientes.cliente.domain.model.EstadoRetencion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proyeccion de salida de una retencion bancaria (Hold / FolioMovimiento). */
public record RetencionCuentaDto(
        UUID folioMovimiento,
        UUID cuentaId,
        UUID clienteId,
        String numeroCuenta,
        String idempotencyKey,
        BigDecimal monto,
        BigDecimal comision,
        BigDecimal montoTotal,
        String moneda,
        String concepto,
        EstadoRetencion estado,
        String referenciaExterna,
        String motivoReverso,
        Instant expiraEn,
        Instant createdAt,
        Instant updatedAt) {}
