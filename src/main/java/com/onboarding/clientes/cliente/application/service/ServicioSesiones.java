package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.dto.SesionDto;
import com.onboarding.clientes.cliente.application.port.SesionRepositorio;
import com.onboarding.clientes.cliente.domain.exception.SesionActivaException;
import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import com.onboarding.clientes.cliente.domain.model.MotivoRevocacion;
import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import com.onboarding.clientes.shared.evento.EventoDominio;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.tiempo.Reloj;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion centralizada de sesiones: un usuario mantiene a lo mas una sesion vigente entre la web y
 * la app movil.
 *
 * <p>Al abrir una sesion con otra todavia vigente se rechaza con {@code 409 sesion_activa}; el cliente
 * debe pedir confirmacion explicita al usuario y repetir con {@code cerrarSesionAnterior=true}. Las
 * sesiones vencen por inactividad y por duracion maxima, y se revocan en servidor al cerrar sesion o al
 * dar de baja al cliente.
 */
@Service
public class ServicioSesiones {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioSesiones.class);

    /** Resultado de validar la sesion de una peticion. */
    public enum Estado { VIGENTE, REVOCADA, EXPIRADA }

    private final SesionRepositorio sesiones;
    private final PublicadorEvento publicadorEvento;
    private final Reloj reloj;
    private final Duration inactividad;
    private final Duration duracionMaxima;

    public ServicioSesiones(
            SesionRepositorio sesiones,
            PublicadorEvento publicadorEvento,
            Reloj reloj,
            @Value("${onboarding.sesion.inactividad:PT15M}") Duration inactividad,
            @Value("${onboarding.sesion.duracion-maxima:PT8H}") Duration duracionMaxima) {
        this.sesiones = sesiones;
        this.publicadorEvento = publicadorEvento;
        this.reloj = reloj;
        this.inactividad = inactividad;
        this.duracionMaxima = duracionMaxima;
    }

    /** Abre una sesion nueva. Debe ejecutarse dentro de la transaccion del inicio de sesion. */
    @Transactional
    public Sesion abrir(UUID usuarioId, CanalSesion canal, String ipOrigen, String dispositivo, boolean cerrarAnterior) {
        Instant ahora = reloj.ahora();
        Optional<Sesion> anterior = sesiones.buscarNoRevocadaPorUsuario(usuarioId);
        if (anterior.isPresent()) {
            Sesion previa = anterior.get();
            if (previa.estaVigente(ahora, inactividad)) {
                if (!cerrarAnterior) {
                    throw new SesionActivaException(previa);
                }
                previa.revocar(MotivoRevocacion.REEMPLAZADA, ahora);
                publicadorEvento.publicar(EventoDominio.crear(
                        "sesion.reemplazada",
                        EventoDominio.TIPO_USUARIO,
                        usuarioId,
                        Map.of("canalAnterior", previa.canal().name(), "canalNuevo", canal.name()),
                        "sistema",
                        ahora));
                LOGGER.info("Sesion {} reemplazada por un acceso desde {}", previa.id(), canal);
            } else {
                previa.revocar(MotivoRevocacion.EXPIRADA, ahora);
            }
            sesiones.guardar(previa);
        }
        return sesiones.guardar(Sesion.abrir(usuarioId, canal, ipOrigen, dispositivo, ahora, duracionMaxima));
    }

    /** Valida la sesion de una peticion autenticada y registra actividad (como mucho una escritura por minuto). */
    @Transactional
    public Estado validar(UUID sesionId, UUID usuarioId) {
        Optional<Sesion> encontrada = sesiones.buscarPorId(sesionId);
        if (encontrada.isEmpty() || !encontrada.get().usuarioId().equals(usuarioId)) {
            return Estado.REVOCADA;
        }
        Sesion sesion = encontrada.get();
        if (sesion.estaRevocada()) {
            return sesion.motivoRevocacion() == MotivoRevocacion.EXPIRADA ? Estado.EXPIRADA : Estado.REVOCADA;
        }
        Instant ahora = reloj.ahora();
        if (!sesion.estaVigente(ahora, inactividad)) {
            sesion.revocar(MotivoRevocacion.EXPIRADA, ahora);
            sesiones.guardar(sesion);
            return Estado.EXPIRADA;
        }
        if (sesion.registrarActividad(ahora)) {
            sesiones.guardar(sesion);
        }
        return Estado.VIGENTE;
    }

    /** Renueva la actividad de la sesion para emitir un token de acceso nuevo sobre la misma sesion. */
    @Transactional
    public Sesion renovar(UUID sesionId) {
        Sesion sesion = sesiones.buscarPorId(sesionId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.SESION_REVOCADA, "La sesion ya no es valida"));
        Instant ahora = reloj.ahora();
        if (!sesion.estaVigente(ahora, inactividad)) {
            throw new ErrorNegocio(CodigoError.SESION_EXPIRADA, "La sesion expiro. Inicia sesion de nuevo");
        }
        sesion.renovar(ahora);
        return sesiones.guardar(sesion);
    }

    @Transactional
    public void cerrar(UUID sesionId) {
        sesiones.buscarPorId(sesionId).ifPresent(sesion -> {
            sesion.revocar(MotivoRevocacion.LOGOUT, reloj.ahora());
            sesiones.guardar(sesion);
        });
    }

    /** Revoca la sesion vigente del usuario (por ejemplo, en la baja logica del cliente). */
    @Transactional
    public void revocarDeUsuario(UUID usuarioId, MotivoRevocacion motivo) {
        sesiones.buscarNoRevocadaPorUsuario(usuarioId).ifPresent(sesion -> {
            sesion.revocar(motivo, reloj.ahora());
            sesiones.guardar(sesion);
        });
    }

    @Transactional(readOnly = true)
    public SesionDto consultar(UUID sesionId) {
        Sesion s = sesiones.buscarPorId(sesionId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.SESION_REVOCADA, "La sesion ya no es valida"));
        Instant porInactividad = s.ultimaActividad().plus(inactividad);
        return new SesionDto(s.id(), s.usuarioId(), s.canal(), s.creadaEn(), s.ultimaActividad(), s.expiraEn(),
                porInactividad.isBefore(s.expiraEn()) ? porInactividad : s.expiraEn());
    }
}
