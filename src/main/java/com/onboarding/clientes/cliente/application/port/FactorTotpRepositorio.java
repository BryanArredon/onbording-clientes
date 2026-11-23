package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.model.FactorTotp;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia del segundo factor TOTP. */
public interface FactorTotpRepositorio {

    FactorTotp guardar(FactorTotp factor);

    Optional<FactorTotp> buscarPorUsuario(UUID usuarioId);
}
