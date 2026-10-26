package com.onboarding.clientes.cliente.domain.model;

import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.id.UuidV7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Asiento inmutable en el libro mayor (ledger) de una cuenta bancaria.
 */
public record MovimientoContable(
        UUID id,
        UUID retencionId,
        UUID cuentaId,
        TipoMovimientoContable tipo,
        Dinero importe,
        Dinero saldoPosterior,
        String correlationId,
        Instant createdAt) {

    public MovimientoContable {
        Objects.requireNonNull(id, "El id del movimiento es obligatorio");
        Objects.requireNonNull(retencionId, "El id de la retencion es obligatorio");
        Objects.requireNonNull(cuentaId, "El id de la cuenta es obligatorio");
        Objects.requireNonNull(tipo, "El tipo de movimiento es obligatorio");
        Objects.requireNonNull(importe, "El importe del movimiento es obligatorio");
        Objects.requireNonNull(saldoPosterior, "El saldo posterior es obligatorio");
        Objects.requireNonNull(createdAt, "La marca de tiempo es obligatoria");
    }

    public static MovimientoContable registrar(
            UUID retencionId,
            UUID cuentaId,
            TipoMovimientoContable tipo,
            Dinero importe,
            Dinero saldoPosterior,
            String correlationId,
            Instant momento) {
        return new MovimientoContable(
                UuidV7.generar(), retencionId, cuentaId, tipo, importe, saldoPosterior, correlationId, momento);
    }
}
