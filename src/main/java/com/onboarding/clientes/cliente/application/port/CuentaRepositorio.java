package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.model.EstatusCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.web.Cursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de cuentas bancarias. */
public interface CuentaRepositorio {

    /**
     * Reserva el siguiente numero de cuenta valido (9 digitos de secuencia + verificador Luhn).
     *
     * <p>Lo delega en la base de datos (secuencia transaccional) para que dos altas concurrentes jamas
     * obtengan el mismo numero sin necesidad de bloqueos en la aplicacion.
     *
     * @return numero de cuenta asignado
     */
    NumeroCuenta siguienteNumero();

    Optional<Cuenta> buscarPorId(UUID id);

    Optional<Cuenta> buscarPorNumero(NumeroCuenta numero);

    /**
     * Descuenta de forma atomica el importe indicado solo si la cuenta esta ACTIVA y su saldo es
     * mayor o igual al importe.
     *
     * @return {@code true} si el descuento se aplico; {@code false} si el saldo fue insuficiente o no estaba activa
     */
    boolean descontarSaldoAtomico(UUID cuentaId, Dinero importe, Instant momento);

    /**
     * Abona de forma atomica el importe indicado (movimiento compensatorio de reverso).
     *
     * @return {@code true} si el abono se aplico
     */
    boolean abonarSaldoAtomico(UUID cuentaId, Dinero importe, Instant momento);

    List<Cuenta> buscarPorCliente(UUID clienteId);

    /** @return cuentas en estatus ACTIVA, paginadas por cursor */
    List<Cuenta> listarActivas(Cursor cursor, int limite);

    long contarPorEstatus(EstatusCuenta estatus);

    /**
     * Busqueda dinamica con filtros combinables: el adaptador construye la consulta unicamente con
     * los criterios presentes.
     *
     * @param criterios criterios ya normalizados por el servicio
     * @param cursor posicion previa, {@code null} para la primera pagina
     * @param limite maximo de elementos (se pide {@code limite + 1} para detectar pagina siguiente)
     * @return hasta {@code limite + 1} elementos ordenados del mas reciente al mas antiguo
     */
    List<Cuenta> buscar(CriteriosBusquedaCuenta criterios, Cursor cursor, int limite);
}
