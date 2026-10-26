package com.onboarding.clientes.cliente.infrastructure.persistence.adapter;

import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaCuenta;
import com.onboarding.clientes.cliente.application.port.CuentaRepositorio;
import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.model.EstatusCuenta;
import com.onboarding.clientes.cliente.domain.model.TipoCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.CuentaEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.CuentaSpringDataRepository;
import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.web.Cursor;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA para el puerto CuentaRepositorio.
 */
@Component
public class CuentaRepositorioAdaptador implements CuentaRepositorio {

    private final CuentaSpringDataRepository cuentaRepository;

    public CuentaRepositorioAdaptador(CuentaSpringDataRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    @Override
    public NumeroCuenta siguienteNumero() {
        String num = cuentaRepository.siguienteNumeroCuenta();
        return NumeroCuenta.de(num.trim());
    }

    @Override
    public Optional<Cuenta> buscarPorId(UUID id) {
        return cuentaRepository.findById(id).map(this::aDominio);
    }

    @Override
    public Optional<Cuenta> buscarPorNumero(NumeroCuenta numero) {
        return cuentaRepository.findByNumeroCuenta(numero.valor()).map(this::aDominio);
    }

    @Override
    public boolean descontarSaldoAtomico(UUID cuentaId, Dinero importe, Instant momento) {
        return cuentaRepository.descontarSaldoAtomico(cuentaId, importe.monto(), momento) > 0;
    }

    @Override
    public boolean abonarSaldoAtomico(UUID cuentaId, Dinero importe, Instant momento) {
        return cuentaRepository.abonarSaldoAtomico(cuentaId, importe.monto(), momento) > 0;
    }

    @Override
    public List<Cuenta> buscarPorCliente(UUID clienteId) {
        return cuentaRepository.findByClienteId(clienteId).stream().map(this::aDominio).toList();
    }

    @Override
    public List<Cuenta> listarActivas(Cursor cursor, int limite) {
        PageRequest pageable = PageRequest.of(0, limite + 1);
        List<CuentaEntity> entities;
        if (cursor == null) {
            entities = cuentaRepository.listarActivasSinCursor(pageable);
        } else {
            entities = cuentaRepository.listarActivasConCursor(
                    cursor.marcaTiempo(), cursor.id(), pageable);
        }
        return entities.stream().map(this::aDominio).toList();
    }

    @Override
    public long contarPorEstatus(EstatusCuenta estatus) {
        return cuentaRepository.countByEstatus(estatus.name());
    }

    @Override
    public List<Cuenta> buscar(CriteriosBusquedaCuenta criterios, Cursor cursor, int limite) {
        PageRequest pageable = PageRequest.of(
                0, limite + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<CuentaEntity> entities =
                cuentaRepository.findAll(especificacion(criterios, cursor), pageable).getContent();
        return entities.stream().map(this::aDominio).toList();
    }

    /**
     * Construye la clausula WHERE unicamente con los criterios presentes (consulta dinamica).
     */
    private Specification<CuentaEntity> especificacion(CriteriosBusquedaCuenta criterios, Cursor cursor) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (criterios.numero() != null) {
                predicados.add(cb.equal(root.get("numeroCuenta"), criterios.numero().valor()));
            }
            if (criterios.clienteId() != null) {
                predicados.add(cb.equal(root.get("clienteId"), criterios.clienteId()));
            }
            if (Boolean.TRUE.equals(criterios.soloActivas())) {
                predicados.add(cb.equal(root.get("estatus"), EstatusCuenta.ACTIVA.name()));
            }
            if (Boolean.FALSE.equals(criterios.soloActivas())) {
                predicados.add(cb.notEqual(root.get("estatus"), EstatusCuenta.ACTIVA.name()));
            }
            if (cursor != null) {
                predicados.add(cb.or(
                        cb.lessThan(root.get("createdAt"), cursor.marcaTiempo()),
                        cb.and(
                                cb.equal(root.get("createdAt"), cursor.marcaTiempo()),
                                cb.lessThan(root.get("id"), cursor.id()))));
            }

            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private Cuenta aDominio(CuentaEntity c) {
        return Cuenta.rehidratar(
                c.getId(),
                c.getClienteId(),
                c.getNumeroCuenta() != null ? NumeroCuenta.de(c.getNumeroCuenta()) : null,
                TipoCuenta.valueOf(c.getTipoCuenta()),
                Dinero.de(c.getSaldo(), c.getMoneda()),
                EstatusCuenta.valueOf(c.getEstatus()),
                c.getVersion() != null ? c.getVersion() : 0L,
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
