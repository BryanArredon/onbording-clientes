package com.onboarding.clientes.cliente.domain.model;

import com.onboarding.clientes.shared.id.UuidV7;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sesion de acceso administrada en servidor.
 *
 * <p>Vence por inactividad (sin peticiones durante {@code inactividad}) o al alcanzar su duracion maxima,
 * lo que ocurra primero. Una sesion revocada nunca vuelve a ser vigente.
 */
public class Sesion {

    /** Intervalo minimo entre dos registros de actividad, para no escribir en cada peticion. */
    public static final Duration GRANULARIDAD_ACTIVIDAD = Duration.ofSeconds(60);

    private final UUID id;
    private final UUID usuarioId;
    private final CanalSesion canal;
    private final String ipOrigen;
    private final String dispositivo;
    private final Instant creadaEn;
    private Instant ultimaActividad;
    private final Instant expiraEn;
    private Instant revocadaEn;
    private MotivoRevocacion motivoRevocacion;

    private Sesion(UUID id, UUID usuarioId, CanalSesion canal, String ipOrigen, String dispositivo, Instant creadaEn,
            Instant ultimaActividad, Instant expiraEn, Instant revocadaEn, MotivoRevocacion motivoRevocacion) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.canal = canal;
        this.ipOrigen = ipOrigen;
        this.dispositivo = dispositivo;
        this.creadaEn = creadaEn;
        this.ultimaActividad = ultimaActividad;
        this.expiraEn = expiraEn;
        this.revocadaEn = revocadaEn;
        this.motivoRevocacion = motivoRevocacion;
    }

    public static Sesion abrir(UUID usuarioId, CanalSesion canal, String ipOrigen, String dispositivo,
            Instant momento, Duration duracionMaxima) {
        return new Sesion(UuidV7.generar(), usuarioId, canal, recortar(ipOrigen, 45), recortar(dispositivo, 200),
                momento, momento, momento.plus(duracionMaxima), null, null);
    }

    public static Sesion rehidratar(UUID id, UUID usuarioId, CanalSesion canal, String ipOrigen, String dispositivo,
            Instant creadaEn, Instant ultimaActividad, Instant expiraEn, Instant revocadaEn,
            MotivoRevocacion motivoRevocacion) {
        return new Sesion(id, usuarioId, canal, ipOrigen, dispositivo, creadaEn, ultimaActividad, expiraEn,
                revocadaEn, motivoRevocacion);
    }

    public boolean estaVigente(Instant momento, Duration inactividad) {
        return revocadaEn == null
                && momento.isBefore(expiraEn)
                && momento.isBefore(ultimaActividad.plus(inactividad));
    }

    public boolean estaRevocada() {
        return revocadaEn != null;
    }

    public void revocar(MotivoRevocacion motivo, Instant momento) {
        if (revocadaEn == null) {
            this.revocadaEn = momento;
            this.motivoRevocacion = motivo;
        }
    }

    /** Registra actividad; devuelve true si hubo cambio que persistir. */
    public boolean registrarActividad(Instant momento) {
        if (momento.isAfter(ultimaActividad.plus(GRANULARIDAD_ACTIVIDAD))) {
            this.ultimaActividad = momento;
            return true;
        }
        return false;
    }

    /** Renovacion explicita del token: siempre cuenta como actividad. */
    public void renovar(Instant momento) {
        this.ultimaActividad = momento;
    }

    private static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    public UUID id() { return id; }
    public UUID usuarioId() { return usuarioId; }
    public CanalSesion canal() { return canal; }
    public String ipOrigen() { return ipOrigen; }
    public String dispositivo() { return dispositivo; }
    public Instant creadaEn() { return creadaEn; }
    public Instant ultimaActividad() { return ultimaActividad; }
    public Instant expiraEn() { return expiraEn; }
    public Instant revocadaEn() { return revocadaEn; }
    public MotivoRevocacion motivoRevocacion() { return motivoRevocacion; }
}
