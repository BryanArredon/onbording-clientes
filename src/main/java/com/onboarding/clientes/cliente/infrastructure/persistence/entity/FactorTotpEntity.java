package com.onboarding.clientes.cliente.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA que mapea la tabla factor_totp. */
@Entity
@Table(name = "factor_totp")
public class FactorTotpEntity {

    @Id
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "secreto_cifrado", length = 200, nullable = false)
    private String secretoCifrado;

    @Column(name = "confirmado", nullable = false)
    private boolean confirmado;

    @Column(name = "ultimo_paso", nullable = false)
    private long ultimoPaso;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FactorTotpEntity() {}

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getSecretoCifrado() { return secretoCifrado; }
    public void setSecretoCifrado(String secretoCifrado) { this.secretoCifrado = secretoCifrado; }
    public boolean isConfirmado() { return confirmado; }
    public void setConfirmado(boolean confirmado) { this.confirmado = confirmado; }
    public long getUltimoPaso() { return ultimoPaso; }
    public void setUltimoPaso(long ultimoPaso) { this.ultimoPaso = ultimoPaso; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
