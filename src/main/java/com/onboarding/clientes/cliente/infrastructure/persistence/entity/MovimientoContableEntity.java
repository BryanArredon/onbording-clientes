package com.onboarding.clientes.cliente.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA inmutable (append-only) que mapea la tabla movimiento_contable. */
@Entity
@Table(name = "movimiento_contable")
public class MovimientoContableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "retencion_id", nullable = false, updatable = false)
    private UUID retencionId;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    @Column(name = "tipo_movimiento", length = 40, nullable = false, updatable = false)
    private String tipoMovimiento;

    @Column(name = "monto", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal monto;

    @Column(name = "moneda", length = 3, nullable = false, updatable = false)
    private String moneda;

    @Column(name = "saldo_posterior", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal saldoPosterior;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MovimientoContableEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRetencionId() { return retencionId; }
    public void setRetencionId(UUID retencionId) { this.retencionId = retencionId; }
    public UUID getCuentaId() { return cuentaId; }
    public void setCuentaId(UUID cuentaId) { this.cuentaId = cuentaId; }
    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public BigDecimal getSaldoPosterior() { return saldoPosterior; }
    public void setSaldoPosterior(BigDecimal saldoPosterior) { this.saldoPosterior = saldoPosterior; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
