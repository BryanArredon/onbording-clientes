package com.onboarding.clientes.cliente.infrastructure.persistence.adapter;

import com.onboarding.clientes.cliente.application.port.RetencionCuentaRepositorio;
import com.onboarding.clientes.cliente.domain.model.EstadoRetencion;
import com.onboarding.clientes.cliente.domain.model.MovimientoContable;
import com.onboarding.clientes.cliente.domain.model.RetencionCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.MovimientoContableEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.RetencionCuentaEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.MovimientoContableSpringDataRepository;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.RetencionCuentaSpringDataRepository;
import com.onboarding.clientes.shared.dinero.Dinero;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA para retenciones bancarias (Holds) y movimientos contables.
 */
@Component
public class RetencionCuentaRepositorioAdaptador implements RetencionCuentaRepositorio {

    private final RetencionCuentaSpringDataRepository retencionRepository;
    private final MovimientoContableSpringDataRepository movimientoRepository;

    public RetencionCuentaRepositorioAdaptador(
            RetencionCuentaSpringDataRepository retencionRepository,
            MovimientoContableSpringDataRepository movimientoRepository) {
        this.retencionRepository = retencionRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    public RetencionCuenta guardar(RetencionCuenta retencion) {
        Optional<RetencionCuentaEntity> existente = retencionRepository.findById(retencion.id());
        RetencionCuentaEntity entity = existente.orElseGet(RetencionCuentaEntity::new);
        entity.setId(retencion.id());
        entity.setCuentaId(retencion.cuentaId());
        entity.setClienteId(retencion.clienteId());
        entity.setNumeroCuenta(retencion.numeroCuenta().valor());
        entity.setIdempotencyKey(retencion.idempotencyKey());
        entity.setPayloadHash(retencion.payloadHash());
        entity.setMonto(retencion.monto().monto());
        entity.setComision(retencion.comision().monto());
        entity.setMontoTotal(retencion.montoTotal().monto());
        entity.setMoneda(retencion.monto().moneda());
        entity.setConcepto(retencion.concepto());
        entity.setEstado(retencion.estado().name());
        entity.setReferenciaExterna(retencion.referenciaExterna());
        entity.setMotivoReverso(retencion.motivoReverso());
        entity.setExpiraEn(retencion.expiraEn());
        if (existente.isPresent()) {
            entity.setVersion(retencion.version());
        }
        entity.setCreatedAt(retencion.createdAt());
        entity.setUpdatedAt(retencion.updatedAt());

        RetencionCuentaEntity saved = retencionRepository.saveAndFlush(entity);
        return aDominio(saved);
    }

    @Override
    public Optional<RetencionCuenta> buscarPorId(UUID folioId) {
        return retencionRepository.findById(folioId).map(this::aDominio);
    }

    @Override
    public Optional<RetencionCuenta> buscarPorCuentaEIdempotencia(UUID cuentaId, String idempotencyKey) {
        return retencionRepository.findByCuentaIdAndIdempotencyKey(cuentaId, idempotencyKey).map(this::aDominio);
    }

    @Override
    public Optional<RetencionCuenta> buscarPorNumeroCuentaEIdempotencia(
            NumeroCuenta numeroCuenta, String idempotencyKey) {
        return retencionRepository
                .findByNumeroCuentaAndIdempotencyKey(numeroCuenta.valor(), idempotencyKey)
                .map(this::aDominio);
    }

    @Override
    public List<RetencionCuenta> buscarRetenidasExpiradas(Instant corte, int limite) {
        return retencionRepository.buscarRetenidasExpiradas(corte, PageRequest.of(0, limite)).stream()
                .map(this::aDominio)
                .toList();
    }

    @Override
    public void registrarMovimiento(MovimientoContable movimiento) {
        MovimientoContableEntity entity = new MovimientoContableEntity();
        entity.setId(movimiento.id());
        entity.setRetencionId(movimiento.retencionId());
        entity.setCuentaId(movimiento.cuentaId());
        entity.setTipoMovimiento(movimiento.tipo().name());
        entity.setMonto(movimiento.importe().monto());
        entity.setMoneda(movimiento.importe().moneda());
        entity.setSaldoPosterior(movimiento.saldoPosterior().monto());
        entity.setCorrelationId(movimiento.correlationId());
        entity.setCreatedAt(movimiento.createdAt());
        movimientoRepository.save(entity);
    }

    private RetencionCuenta aDominio(RetencionCuentaEntity e) {
        String moneda = e.getMoneda();
        return RetencionCuenta.rehidratar(
                e.getId(),
                e.getCuentaId(),
                e.getClienteId(),
                NumeroCuenta.de(e.getNumeroCuenta()),
                e.getIdempotencyKey(),
                e.getPayloadHash(),
                Dinero.de(e.getMonto(), moneda),
                Dinero.de(e.getComision(), moneda),
                Dinero.de(e.getMontoTotal(), moneda),
                e.getConcepto(),
                EstadoRetencion.valueOf(e.getEstado()),
                e.getReferenciaExterna(),
                e.getMotivoReverso(),
                e.getExpiraEn(),
                e.getVersion() != null ? e.getVersion() : 0L,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
