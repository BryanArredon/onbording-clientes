package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.model.MovimientoContable;
import com.onboarding.clientes.cliente.domain.model.RetencionCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para retenciones bancarias (Holds) y asientos del libro mayor (Ledger).
 */
public interface RetencionCuentaRepositorio {

    RetencionCuenta guardar(RetencionCuenta retencion);

    Optional<RetencionCuenta> buscarPorId(UUID folioId);

    Optional<RetencionCuenta> buscarPorCuentaEIdempotencia(UUID cuentaId, String idempotencyKey);

    Optional<RetencionCuenta> buscarPorNumeroCuentaEIdempotencia(NumeroCuenta numeroCuenta, String idempotencyKey);

    List<RetencionCuenta> buscarRetenidasExpiradas(Instant corte, int limite);

    void registrarMovimiento(MovimientoContable movimiento);
}
