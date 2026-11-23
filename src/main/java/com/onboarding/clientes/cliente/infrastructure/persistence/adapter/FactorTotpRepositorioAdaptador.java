package com.onboarding.clientes.cliente.infrastructure.persistence.adapter;

import com.onboarding.clientes.cliente.application.port.FactorTotpRepositorio;
import com.onboarding.clientes.cliente.domain.model.FactorTotp;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.FactorTotpEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.FactorTotpSpringDataRepository;
import com.onboarding.clientes.shared.tiempo.Reloj;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador JPA del puerto {@link FactorTotpRepositorio}. */
@Component
public class FactorTotpRepositorioAdaptador implements FactorTotpRepositorio {

    private final FactorTotpSpringDataRepository jpa;
    private final Reloj reloj;

    public FactorTotpRepositorioAdaptador(FactorTotpSpringDataRepository jpa, Reloj reloj) {
        this.jpa = jpa;
        this.reloj = reloj;
    }

    @Override
    public FactorTotp guardar(FactorTotp f) {
        FactorTotpEntity e = new FactorTotpEntity();
        e.setUsuarioId(f.usuarioId());
        e.setSecretoCifrado(f.secretoCifrado());
        e.setConfirmado(f.confirmado());
        e.setUltimoPaso(f.ultimoPaso());
        e.setCreatedAt(f.createdAt());
        e.setUpdatedAt(reloj.ahora());
        return aDominio(jpa.save(e));
    }

    @Override
    public Optional<FactorTotp> buscarPorUsuario(UUID usuarioId) {
        return jpa.findById(usuarioId).map(FactorTotpRepositorioAdaptador::aDominio);
    }

    private static FactorTotp aDominio(FactorTotpEntity e) {
        return FactorTotp.rehidratar(e.getUsuarioId(), e.getSecretoCifrado(), e.isConfirmado(), e.getUltimoPaso(),
                e.getCreatedAt());
    }
}
