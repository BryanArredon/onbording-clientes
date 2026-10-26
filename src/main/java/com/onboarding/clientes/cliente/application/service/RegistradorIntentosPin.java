package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.shared.evento.EventoDominio;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.error.TrazaActual;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra los intentos de uso del PIN de autorizacion en una transaccion independiente
 * ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>De este modo, cuando el cliente introduce un PIN incorrecto y el caso de uso lanza una excepcion que
 * revierte la transaccion de pago, el incremento de {@code intentos_fallidos_pin} y el bloqueo temporal tras
 * 3 intentos quedan confirmados de forma atomica y persistente en la base de datos.
 */
@Service
public class RegistradorIntentosPin {

    private final UsuarioRepositorio usuarios;
    private final PublicadorEvento publicadorEvento;

    public RegistradorIntentosPin(UsuarioRepositorio usuarios, PublicadorEvento publicadorEvento) {
        this.usuarios = usuarios;
        this.publicadorEvento = publicadorEvento;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registrarFalloAtomico(Usuario usuario, Instant momento) {
        usuario.registrarIntentoFallidoPin(momento);
        usuarios.guardar(usuario);

        int restantes = Math.max(0, Usuario.MAX_INTENTOS_PIN - usuario.intentosFallidosPin());
        publicadorEvento.publicar(EventoDominio.crear(
                "usuario.pin_intento_fallido",
                EventoDominio.TIPO_USUARIO,
                usuario.id(),
                Map.of(
                        "intentosFallidosPin", usuario.intentosFallidosPin(),
                        "intentosRestantes", restantes,
                        "bloqueado", usuario.estaPinBloqueado(momento)),
                "sistema",
                momento).conCorrelationId(TrazaActual.obtener()));
        return restantes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarExitoAtomico(Usuario usuario, Instant momento) {
        if (usuario.intentosFallidosPin() > 0 || usuario.pinBloqueadoHasta() != null) {
            usuario.registrarUsoExitosoPin(momento);
            usuarios.guardar(usuario);
        }
    }
}
