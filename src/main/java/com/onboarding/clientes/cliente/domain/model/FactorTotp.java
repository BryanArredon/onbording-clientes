package com.onboarding.clientes.cliente.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Segundo factor TOTP del usuario. El secreto viaja siempre cifrado: este agregado nunca conoce la semilla
 * en claro, solo la custodia.
 *
 * <p>Un factor sin confirmar es un enrolamiento en curso: se confirma al validar el primer codigo.
 */
public class FactorTotp {

    private final UUID usuarioId;
    private String secretoCifrado;
    private boolean confirmado;
    private long ultimoPaso;
    private final Instant createdAt;

    private FactorTotp(UUID usuarioId, String secretoCifrado, boolean confirmado, long ultimoPaso, Instant createdAt) {
        this.usuarioId = usuarioId;
        this.secretoCifrado = secretoCifrado;
        this.confirmado = confirmado;
        this.ultimoPaso = ultimoPaso;
        this.createdAt = createdAt;
    }

    public static FactorTotp pendiente(UUID usuarioId, String secretoCifrado, Instant momento) {
        return new FactorTotp(usuarioId, secretoCifrado, false, 0, momento);
    }

    public static FactorTotp rehidratar(UUID usuarioId, String secretoCifrado, boolean confirmado, long ultimoPaso,
            Instant createdAt) {
        return new FactorTotp(usuarioId, secretoCifrado, confirmado, ultimoPaso, createdAt);
    }

    /** Reinicia el enrolamiento con una semilla nueva (solo mientras no este confirmado). */
    public void reemplazarSecreto(String nuevoSecretoCifrado) {
        if (confirmado) {
            throw new IllegalStateException("No se puede reemplazar el secreto de un factor confirmado");
        }
        this.secretoCifrado = nuevoSecretoCifrado;
        this.ultimoPaso = 0;
    }

    /** Un paso solo puede usarse una vez: un codigo interceptado no sirve para un segundo acceso. */
    public boolean pasoDisponible(long paso) {
        return paso > ultimoPaso;
    }

    public void registrarUso(long paso) {
        this.ultimoPaso = paso;
        this.confirmado = true;
    }

    public UUID usuarioId() { return usuarioId; }
    public String secretoCifrado() { return secretoCifrado; }
    public boolean confirmado() { return confirmado; }
    public long ultimoPaso() { return ultimoPaso; }
    public Instant createdAt() { return createdAt; }
}
