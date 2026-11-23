package com.onboarding.clientes.cliente.infrastructure.persistence.jpa;

import com.onboarding.clientes.cliente.infrastructure.persistence.entity.SesionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SesionSpringDataRepository extends JpaRepository<SesionEntity, UUID> {

    Optional<SesionEntity> findFirstByUsuarioIdAndRevocadaEnIsNull(UUID usuarioId);
}
