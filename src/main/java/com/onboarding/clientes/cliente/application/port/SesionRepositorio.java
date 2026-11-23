package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.model.Sesion;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de sesiones de acceso. */
public interface SesionRepositorio {

    Sesion guardar(Sesion sesion);

    Optional<Sesion> buscarPorId(UUID id);

    /** La sesion no revocada del usuario, si existe (a lo mas una por la restriccion de la base). */
    Optional<Sesion> buscarNoRevocadaPorUsuario(UUID usuarioId);
}
