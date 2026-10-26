package com.onboarding.clientes.cliente.infrastructure.persistence.jpa;

import com.onboarding.clientes.cliente.infrastructure.persistence.entity.RetencionCuentaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RetencionCuentaSpringDataRepository extends JpaRepository<RetencionCuentaEntity, UUID> {

    Optional<RetencionCuentaEntity> findByCuentaIdAndIdempotencyKey(UUID cuentaId, String idempotencyKey);

    Optional<RetencionCuentaEntity> findByNumeroCuentaAndIdempotencyKey(String numeroCuenta, String idempotencyKey);

    @Query("SELECT r FROM RetencionCuentaEntity r WHERE r.estado = 'RETENIDO' AND r.expiraEn < :corte ORDER BY r.expiraEn ASC")
    List<RetencionCuentaEntity> buscarRetenidasExpiradas(@Param("corte") Instant corte, Pageable pageable);
}
