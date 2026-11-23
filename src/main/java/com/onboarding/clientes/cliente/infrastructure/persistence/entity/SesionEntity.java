package com.onboarding.clientes.cliente.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA que mapea la tabla sesion. */
@Entity
@Table(name = "sesion")
public class SesionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "canal", length = 10, nullable = false, updatable = false)
    private String canal;

    @Column(name = "ip_origen", length = 45, updatable = false)
    private String ipOrigen;

    @Column(name = "dispositivo", length = 200, updatable = false)
    private String dispositivo;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "revocada_en")
    private Instant revocadaEn;

    @Column(name = "motivo_revocacion", length = 20)
    private String motivoRevocacion;

    public SesionEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }
    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(Instant ultimaActividad) { this.ultimaActividad = ultimaActividad; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
    public Instant getRevocadaEn() { return revocadaEn; }
    public void setRevocadaEn(Instant revocadaEn) { this.revocadaEn = revocadaEn; }
    public String getMotivoRevocacion() { return motivoRevocacion; }
    public void setMotivoRevocacion(String motivoRevocacion) { this.motivoRevocacion = motivoRevocacion; }
}
