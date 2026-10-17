package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.dto.ClienteDetalle;
import com.onboarding.clientes.cliente.application.dto.ClienteResumen;
import com.onboarding.clientes.cliente.application.mapper.ClienteMapeador;
import com.onboarding.clientes.cliente.application.port.ClienteRepositorio;
import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaCliente;
import com.onboarding.clientes.cliente.application.port.CuentaRepositorio;
import com.onboarding.clientes.cliente.application.query.ConsultaCliente;
import com.onboarding.clientes.cliente.domain.exception.ClienteNoEncontradoException;
import com.onboarding.clientes.cliente.domain.exception.CuentaNoEncontradaException;
import com.onboarding.clientes.cliente.domain.model.Cliente;
import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.cliente.domain.valueobject.Curp;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.Rfc;
import com.onboarding.clientes.config.PropiedadesOnboarding;
import com.onboarding.clientes.shared.error.DatoInvalidoException;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.web.Cursor;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicacion para la consulta de clientes.
 */
@Service
@Transactional(readOnly = true)
public class ServicioConsultaCliente {

    private static final String ZONA_OPERATIVA = "America/Mexico_City";

    private final ClienteRepositorio clientes;
    private final CuentaRepositorio cuentas;
    private final ClienteMapeador mapeador;
    private final PropiedadesOnboarding propiedades;
    private final AutorizadorRecursos autorizador;

    public ServicioConsultaCliente(
            ClienteRepositorio clientes,
            CuentaRepositorio cuentas,
            ClienteMapeador mapeador,
            PropiedadesOnboarding propiedades,
            AutorizadorRecursos autorizador) {
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.mapeador = mapeador;
        this.propiedades = propiedades;
        this.autorizador = autorizador;
    }

    public ClienteDetalle buscarPorId(UUID id) {
        return clientes.buscarPorId(id)
                .map(this::detallar)
                .orElseThrow(() -> new ClienteNoEncontradoException(id));
    }

    public ClienteDetalle buscarPorCurp(String curpTexto) {
        Curp curp = Curp.de(curpTexto);
        return clientes.buscarPorCurp(curp)
                .map(this::detallar)
                .orElseThrow(() -> new ClienteNoEncontradoException("CURP " + curpTexto));
    }

    public ClienteDetalle buscarPorRfc(String rfcTexto) {
        Rfc rfc = Rfc.de(rfcTexto);
        return clientes.buscarPorRfc(rfc)
                .map(this::detallar)
                .orElseThrow(() -> new ClienteNoEncontradoException("RFC " + rfcTexto));
    }

    public ClienteDetalle buscarPorCorreo(String correoTexto) {
        Correo correo = Correo.de(correoTexto);
        return clientes.buscarPorCorreo(correo)
                .map(this::detallar)
                .orElseThrow(() -> new ClienteNoEncontradoException("correo " + correoTexto));
    }

    public ClienteDetalle buscarPorNumeroCuenta(String numeroCuentaTexto) {
        NumeroCuenta numero = NumeroCuenta.de(numeroCuentaTexto);
        Cuenta cuenta = cuentas.buscarPorNumero(numero)
                .orElseThrow(() -> new CuentaNoEncontradaException(numero));
        return buscarPorId(cuenta.clienteId());
    }

    /** Autoriza sobre el cliente dueño del recurso y solo entonces lo proyecta a DTO. */
    private ClienteDetalle detallar(Cliente cliente) {
        autorizador.exigePropietarioOAdministrador(cliente.id());
        return mapeador.aDetalle(cliente);
    }

    public PaginaRespuesta<ClienteResumen> listar(Cursor cursor, Integer limiteSolicitado, boolean soloActivos) {
        if (!autorizador.esAdministrador()) {
            return buscar(ConsultaCliente.soloPropio(soloActivos, cursor != null ? cursor.codificar() : null, limiteSolicitado));
        }
        int limite = normalizarLimite(limiteSolicitado);
        List<Cliente> elementos = clientes.listar(cursor, limite, soloActivos);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aResumen,
                cliente -> new Cursor(cliente.createdAt(), cliente.id()));
    }

    public PaginaRespuesta<ClienteResumen> listarRegistradosEntre(
            LocalDate desde, LocalDate hasta, Cursor cursor, Integer limiteSolicitado) {
        if (!autorizador.esAdministrador()) {
            return buscar(ConsultaCliente.soloPropio(true, cursor != null ? cursor.codificar() : null, limiteSolicitado));
        }
        ZoneId zone = ZoneId.of(ZONA_OPERATIVA);
        Instant inicio = desde.atStartOfDay(zone).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        int limite = normalizarLimite(limiteSolicitado);

        List<Cliente> elementos = clientes.listarRegistradosEntre(inicio, fin, cursor, limite);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aResumen,
                cliente -> new Cursor(cliente.createdAt(), cliente.id()));
    }

    /**
     * Busqueda unificada con filtros combinables: un unico punto de entrada para consultar por id,
     * CURP, RFC, correo, numero de cuenta, estatus y rango de fechas, en cualquier combinacion.
     *
     * <p>Los textos recibidos se traducen a value objects de dominio antes de consultar, de modo que
     * un filtro con formato invalido se rechaza con el error de negocio correspondiente.
     */
    public PaginaRespuesta<ClienteResumen> buscar(ConsultaCliente consulta) {
        int limite = normalizarLimite(consulta.limite());
        validarRangoFechas(consulta.desde(), consulta.hasta());

        UUID idAlcance = autorizador.clienteIdPermitido();

        CriteriosBusquedaCliente criterios = new CriteriosBusquedaCliente(
                idAlcance != null ? idAlcance : consulta.id(),
                aCurp(consulta.curp()),
                aRfc(consulta.rfc()),
                aCorreo(consulta.correo()),
                aNumeroCuenta(consulta.numeroCuenta()),
                consulta.soloActivos(),
                aInicioDelDia(consulta.desde()),
                aFinDelDia(consulta.hasta()));

        List<Cliente> elementos = clientes.buscar(criterios, Cursor.decodificar(consulta.cursor()), limite);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aResumen,
                cliente -> new Cursor(cliente.createdAt(), cliente.id()));
    }

    private void validarRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new DatoInvalidoException(
                    "El rango de fechas de consulta no es valido",
                    List.of("desde: no puede ser posterior a hasta"));
        }
    }

    private Curp aCurp(String texto) {
        return texto != null && !texto.isBlank() ? Curp.de(texto) : null;
    }

    private Rfc aRfc(String texto) {
        return texto != null && !texto.isBlank() ? Rfc.de(texto) : null;
    }

    private Correo aCorreo(String texto) {
        return texto != null && !texto.isBlank() ? Correo.de(texto) : null;
    }

    private NumeroCuenta aNumeroCuenta(String texto) {
        return texto != null && !texto.isBlank() ? NumeroCuenta.de(texto) : null;
    }

    private Instant aInicioDelDia(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay(ZoneId.of(ZONA_OPERATIVA)).toInstant();
    }

    private Instant aFinDelDia(LocalDate fecha) {
        return fecha == null
                ? null
                : fecha.plusDays(1).atStartOfDay(ZoneId.of(ZONA_OPERATIVA)).toInstant().minusNanos(1);
    }

    private int normalizarLimite(Integer solicitado) {
        PropiedadesOnboarding.Paginacion pag = propiedades.paginacion();
        if (solicitado == null || solicitado <= 0) {
            return pag.limitePorDefecto();
        }
        return Math.min(solicitado, pag.limiteMaximo());
    }
}
