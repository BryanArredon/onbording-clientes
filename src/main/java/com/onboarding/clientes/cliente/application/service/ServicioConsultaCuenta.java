package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.dto.CuentaDto;
import com.onboarding.clientes.cliente.application.dto.SaldoCuentaDto;
import com.onboarding.clientes.cliente.application.mapper.ClienteMapeador;
import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaCuenta;
import com.onboarding.clientes.cliente.application.port.CuentaRepositorio;
import com.onboarding.clientes.cliente.application.query.ConsultaCuenta;
import com.onboarding.clientes.cliente.domain.exception.CuentaNoEncontradaException;
import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.config.PropiedadesOnboarding;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.web.Cursor;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicacion para la consulta de cuentas bancarias.
 */
@Service
@Transactional(readOnly = true)
public class ServicioConsultaCuenta {

    private final CuentaRepositorio cuentas;
    private final ClienteMapeador mapeador;
    private final PropiedadesOnboarding propiedades;
    private final AutorizadorRecursos autorizador;

    public ServicioConsultaCuenta(
            CuentaRepositorio cuentas,
            ClienteMapeador mapeador,
            PropiedadesOnboarding propiedades,
            AutorizadorRecursos autorizador) {
        this.cuentas = cuentas;
        this.mapeador = mapeador;
        this.propiedades = propiedades;
        this.autorizador = autorizador;
    }

    public CuentaDto buscarPorNumero(String numeroCuentaTexto) {
        NumeroCuenta numero = NumeroCuenta.de(numeroCuentaTexto);
        return cuentas.buscarPorNumero(numero)
                .map(cuenta -> {
                    autorizador.exigePropietarioOAdministrador(cuenta.clienteId());
                    return mapeador.aDto(cuenta);
                })
                .orElseThrow(() -> new CuentaNoEncontradaException(numero));
    }

    public SaldoCuentaDto consultarSaldo(String numeroCuentaTexto) {
        NumeroCuenta numero = NumeroCuenta.de(numeroCuentaTexto);
        Cuenta cuenta = cuentas.buscarPorNumero(numero)
                .orElseThrow(() -> new CuentaNoEncontradaException(numero));
        autorizador.exigePropietarioOAdministrador(cuenta.clienteId());
        return new SaldoCuentaDto(
                cuenta.numeroCuenta(),
                cuenta.saldo().moneda(),
                cuenta.saldo().monto(),
                cuenta.estatus());
    }

    public PaginaRespuesta<CuentaDto> listarActivas(Cursor cursor, Integer limiteSolicitado) {
        UUID clienteIdAlcance = autorizador.clienteIdPermitido();
        if (clienteIdAlcance != null) {
            return buscar(ConsultaCuenta.soloPropias(cursor != null ? cursor.codificar() : null, limiteSolicitado));
        }
        int limite = normalizarLimite(limiteSolicitado);
        List<Cuenta> elementos = cuentas.listarActivas(cursor, limite);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aDto,
                cuenta -> new Cursor(cuenta.createdAt(), cuenta.id()));
    }

    /**
     * Busqueda unificada de cuentas con filtros combinables: un unico punto de entrada para consultar
     * por numero de cuenta o por cliente, con paginacion. La proyeccion de respuesta ya incluye el
     * saldo disponible.
     *
     * <p>Un rol distinto de {@code ADMIN} solo puede ver las cuentas de su propio cliente aunque pida
     * otro numero de cuenta o identificador de terceros (prevencion de IDOR).
     */
    public PaginaRespuesta<CuentaDto> buscar(ConsultaCuenta consulta) {
        int limite = normalizarLimite(consulta.limite());

        UUID clienteIdAlcance = autorizador.clienteIdPermitido();

        CriteriosBusquedaCuenta criterios = new CriteriosBusquedaCuenta(
                consulta.numeroCuenta() != null && !consulta.numeroCuenta().isBlank()
                        ? NumeroCuenta.de(consulta.numeroCuenta())
                        : null,
                clienteIdAlcance != null ? clienteIdAlcance : consulta.clienteId(),
                consulta.soloActivas());

        List<Cuenta> elementos = cuentas.buscar(criterios, Cursor.decodificar(consulta.cursor()), limite);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aDto,
                cuenta -> new Cursor(cuenta.createdAt(), cuenta.id()));
    }

    private int normalizarLimite(Integer solicitado) {
        PropiedadesOnboarding.Paginacion pag = propiedades.paginacion();
        if (solicitado == null || solicitado <= 0) {
            return pag.limitePorDefecto();
        }
        return Math.min(solicitado, pag.limiteMaximo());
    }
}
