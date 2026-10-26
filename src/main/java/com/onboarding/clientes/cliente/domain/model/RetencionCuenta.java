package com.onboarding.clientes.cliente.domain.model;

import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import com.onboarding.clientes.shared.id.UuidV7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Retención atómica de fondos (Hold) sobre una cuenta bancaria.
 *
 * <p>Representa el {@code FolioMovimiento} emitido en la Etapa 2 de la Saga de pagos. Mientras permanece en
 * {@link EstadoRetencion#RETENIDO}, los fondos ya fueron descontados del saldo disponible de la cuenta y
 * quedan protegidos hasta que el switch confirme la liquidación ({@link EstadoRetencion#LIQUIDADO}) o
 * solicite el reverso compensatorio ({@link EstadoRetencion#REVERSADO}).
 */
public final class RetencionCuenta {

    private final UUID id;
    private final UUID cuentaId;
    private final UUID clienteId;
    private final NumeroCuenta numeroCuenta;
    private final String idempotencyKey;
    private final String payloadHash;
    private final Dinero monto;
    private final Dinero comision;
    private final Dinero montoTotal;
    private final String concepto;
    private EstadoRetencion estado;
    private String referenciaExterna;
    private String motivoReverso;
    private final Instant expiraEn;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private RetencionCuenta(
            UUID id,
            UUID cuentaId,
            UUID clienteId,
            NumeroCuenta numeroCuenta,
            String idempotencyKey,
            String payloadHash,
            Dinero monto,
            Dinero comision,
            Dinero montoTotal,
            String concepto,
            EstadoRetencion estado,
            String referenciaExterna,
            String motivoReverso,
            Instant expiraEn,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId es obligatorio");
        this.clienteId = Objects.requireNonNull(clienteId, "clienteId es obligatorio");
        this.numeroCuenta = Objects.requireNonNull(numeroCuenta, "numeroCuenta es obligatorio");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey es obligatoria");
        this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash es obligatorio");
        this.monto = Objects.requireNonNull(monto, "monto es obligatorio");
        this.comision = Objects.requireNonNull(comision, "comision es obligatoria");
        this.montoTotal = Objects.requireNonNull(montoTotal, "montoTotal es obligatorio");
        this.concepto = Objects.requireNonNull(concepto, "concepto es obligatorio");
        this.estado = Objects.requireNonNull(estado, "estado es obligatorio");
        this.referenciaExterna = referenciaExterna;
        this.motivoReverso = motivoReverso;
        this.expiraEn = Objects.requireNonNull(expiraEn, "expiraEn es obligatorio");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt es obligatorio");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt es obligatorio");

        if (!monto.esPositivo()) {
            throw new ErrorNegocio(CodigoError.SALDO_INVALIDO, "El monto a retener debe ser mayor que cero");
        }
        if (!monto.sumar(comision).equals(montoTotal)) {
            throw new ErrorNegocio(
                    CodigoError.DATO_INVALIDO, "El monto total retenido debe ser exactamente monto + comision");
        }
    }

    public static RetencionCuenta crear(
            UUID cuentaId,
            UUID clienteId,
            NumeroCuenta numeroCuenta,
            String idempotencyKey,
            String payloadHash,
            Dinero monto,
            Dinero comision,
            String concepto,
            Instant expiraEn,
            Instant momento) {
        Dinero total = monto.sumar(comision);
        return new RetencionCuenta(
                UuidV7.generar(),
                cuentaId,
                clienteId,
                numeroCuenta,
                idempotencyKey.trim(),
                payloadHash,
                monto,
                comision,
                total,
                concepto.trim(),
                EstadoRetencion.RETENIDO,
                null,
                null,
                expiraEn,
                0L,
                momento,
                momento);
    }

    public static RetencionCuenta rehidratar(
            UUID id,
            UUID cuentaId,
            UUID clienteId,
            NumeroCuenta numeroCuenta,
            String idempotencyKey,
            String payloadHash,
            Dinero monto,
            Dinero comision,
            Dinero montoTotal,
            String concepto,
            EstadoRetencion estado,
            String referenciaExterna,
            String motivoReverso,
            Instant expiraEn,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new RetencionCuenta(
                id,
                cuentaId,
                clienteId,
                numeroCuenta,
                idempotencyKey,
                payloadHash,
                monto,
                comision,
                montoTotal,
                concepto,
                estado,
                referenciaExterna,
                motivoReverso,
                expiraEn,
                version,
                createdAt,
                updatedAt);
    }

    public void liquidar(String referenciaAutorizacion, Instant momento) {
        this.estado = this.estado.transicionarA(EstadoRetencion.LIQUIDADO);
        if (referenciaAutorizacion != null && !referenciaAutorizacion.isBlank()) {
            this.referenciaExterna = referenciaAutorizacion.trim();
        }
        this.updatedAt = momento;
    }

    public void iniciarReverso(String motivo, Instant momento) {
        this.estado = this.estado.transicionarA(EstadoRetencion.REVERSANDO);
        if (motivo != null && !motivo.isBlank()) {
            this.motivoReverso = motivo.trim();
        }
        this.updatedAt = momento;
    }

    public void completarReverso(String motivo, Instant momento) {
        this.estado = this.estado.transicionarA(EstadoRetencion.REVERSADO);
        if (motivo != null && !motivo.isBlank()) {
            this.motivoReverso = motivo.trim();
        }
        this.updatedAt = momento;
    }

    public void marcarRequiereRevision(String motivo, Instant momento) {
        this.estado = this.estado.transicionarA(EstadoRetencion.REQUIERE_REVISION);
        if (motivo != null && !motivo.isBlank()) {
            this.motivoReverso = motivo.trim();
        }
        this.updatedAt = momento;
    }

    public boolean estaExpirada(Instant momento) {
        return estado == EstadoRetencion.RETENIDO && momento.isAfter(expiraEn);
    }

    public UUID id() { return id; }
    public UUID cuentaId() { return cuentaId; }
    public UUID clienteId() { return clienteId; }
    public NumeroCuenta numeroCuenta() { return numeroCuenta; }
    public String idempotencyKey() { return idempotencyKey; }
    public String payloadHash() { return payloadHash; }
    public Dinero monto() { return monto; }
    public Dinero comision() { return comision; }
    public Dinero montoTotal() { return montoTotal; }
    public String concepto() { return concepto; }
    public EstadoRetencion estado() { return estado; }
    public String referenciaExterna() { return referenciaExterna; }
    public String motivoReverso() { return motivoReverso; }
    public Instant expiraEn() { return expiraEn; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
