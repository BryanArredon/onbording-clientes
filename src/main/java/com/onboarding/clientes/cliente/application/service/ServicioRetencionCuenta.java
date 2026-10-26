package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.command.ComandoLiquidarRetencion;
import com.onboarding.clientes.cliente.application.command.ComandoRetenerSaldo;
import com.onboarding.clientes.cliente.application.command.ComandoReversarRetencion;
import com.onboarding.clientes.cliente.application.command.ComandoRevisionRetencion;
import com.onboarding.clientes.cliente.application.dto.RetencionCuentaDto;
import com.onboarding.clientes.cliente.application.mapper.ClienteMapeador;
import com.onboarding.clientes.cliente.application.port.ClienteRepositorio;
import com.onboarding.clientes.cliente.application.port.CuentaRepositorio;
import com.onboarding.clientes.cliente.application.port.RetencionCuentaRepositorio;
import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.application.query.ConsultaRetencion;
import com.onboarding.clientes.cliente.domain.exception.ClienteInactivoException;
import com.onboarding.clientes.cliente.domain.exception.ClienteNoEncontradoException;
import com.onboarding.clientes.cliente.domain.exception.ConflictoIdempotenciaException;
import com.onboarding.clientes.cliente.domain.exception.CuentaInactivaException;
import com.onboarding.clientes.cliente.domain.exception.CuentaNoEncontradaException;
import com.onboarding.clientes.cliente.domain.exception.PinBloqueadoException;
import com.onboarding.clientes.cliente.domain.exception.PinInvalidoException;
import com.onboarding.clientes.cliente.domain.exception.PinNoConfiguradoException;
import com.onboarding.clientes.cliente.domain.exception.RetencionNoEncontradaException;
import com.onboarding.clientes.cliente.domain.exception.SaldoInsuficienteException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioInactivoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioNoEncontradoException;
import com.onboarding.clientes.cliente.domain.model.Cliente;
import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.model.EstadoRetencion;
import com.onboarding.clientes.cliente.domain.model.MovimientoContable;
import com.onboarding.clientes.cliente.domain.model.RetencionCuenta;
import com.onboarding.clientes.cliente.domain.model.TipoMovimientoContable;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import com.onboarding.clientes.shared.error.TrazaActual;
import com.onboarding.clientes.shared.evento.EventoDominio;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.seguridad.GeneradorHash;
import com.onboarding.clientes.shared.seguridad.PoliticaCredenciales;
import com.onboarding.clientes.shared.tiempo.Reloj;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: Etapa 2 de la Saga de pagos bancarios (validacion de cuenta, PIN, saldo y ciclo de vida del Hold).
 *
 * <p>Aplica todas las invariantes bancarias antes de permitir que el switch de pagos contacte al proveedor:
 * <ul>
 *   <li>Cuenta existente y {@code ACTIVA}, perteneciente a un cliente {@code ACTIVO}.
 *   <li>Autorizacion por propiedad (prevencion de IDOR contra el JWT autenticado).
 *   <li>Idempotencia estricta por {@code (cuenta_id, idempotency_key)} validando hash SHA-256 del payload.
 *   <li>Validacion del PIN de 4 digitos contra hash BCrypt (coste 12) con maximo de 3 intentos persistentes.
 *   <li>Descuento atomico en base de datos ({@code UPDATE ... WHERE saldo >= total}) y registro en el ledger
 *       contable inmutable.
 * </ul>
 */
@Service
public class ServicioRetencionCuenta {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioRetencionCuenta.class);
    private static final Duration TTL_HOLD_POR_DEFECTO = Duration.ofMinutes(15);
    private static final String MONEDA_POR_DEFECTO = "MXN";

    private final CuentaRepositorio cuentas;
    private final ClienteRepositorio clientes;
    private final UsuarioRepositorio usuarios;
    private final RetencionCuentaRepositorio retenciones;
    private final RegistradorIntentosPin registradorIntentosPin;
    private final GeneradorHash generadorHash;
    private final PublicadorEvento publicadorEvento;
    private final AutorizadorRecursos autorizador;
    private final ClienteMapeador mapeador;
    private final Reloj reloj;

    public ServicioRetencionCuenta(
            CuentaRepositorio cuentas,
            ClienteRepositorio clientes,
            UsuarioRepositorio usuarios,
            RetencionCuentaRepositorio retenciones,
            RegistradorIntentosPin registradorIntentosPin,
            GeneradorHash generadorHash,
            PublicadorEvento publicadorEvento,
            AutorizadorRecursos autorizador,
            ClienteMapeador mapeador,
            Reloj reloj) {
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.retenciones = retenciones;
        this.registradorIntentosPin = registradorIntentosPin;
        this.generadorHash = generadorHash;
        this.publicadorEvento = publicadorEvento;
        this.autorizador = autorizador;
        this.mapeador = mapeador;
        this.reloj = reloj;
    }

    @Transactional
    public RetencionCuentaDto retener(ComandoRetenerSaldo comando) {
        NumeroCuenta numeroCuenta = NumeroCuenta.de(comando.numeroCuenta());
        PoliticaCredenciales.validarPin(comando.pinAutorizacion(), 4, 4);

        String moneda = comando.moneda() == null || comando.moneda().isBlank()
                ? MONEDA_POR_DEFECTO
                : comando.moneda().trim();
        Dinero monto = Dinero.de(comando.monto(), moneda);
        Dinero comision = comando.comision() == null
                ? Dinero.cero(moneda)
                : Dinero.de(comando.comision(), moneda);
        if (!monto.esPositivo()) {
            throw new ErrorNegocio(CodigoError.SALDO_INVALIDO, "El monto a retener debe ser mayor que cero");
        }

        Cuenta cuenta = cuentas.buscarPorNumero(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(numeroCuenta));
        Cliente cliente = clientes.buscarPorId(cuenta.clienteId())
                .orElseThrow(() -> new ClienteNoEncontradoException(cuenta.clienteId()));

        autorizador.exigeTitularCuentaOAdministrador(cliente.id(), cliente.correo().valor());

        if (!cliente.activo()) {
            throw new ClienteInactivoException();
        }
        if (!cuenta.esActiva()) {
            throw new CuentaInactivaException(numeroCuenta);
        }

        String idempotencyKey = comando.idempotencyKey().trim();
        String payloadHash = calcularHashPayload(numeroCuenta, monto, comision, comando.concepto());

        Optional<RetencionCuenta> previa = retenciones.buscarPorCuentaEIdempotencia(cuenta.id(), idempotencyKey);
        if (previa.isPresent()) {
            RetencionCuenta existente = previa.get();
            if (!existente.payloadHash().equals(payloadHash)) {
                throw new ConflictoIdempotenciaException();
            }
            return mapeador.aDto(existente);
        }

        Instant ahora = reloj.ahora();
        validarPinAutorizacion(cliente.id(), comando.pinAutorizacion().trim(), ahora);

        Dinero montoTotal = monto.sumar(comision);
        boolean descontado = cuentas.descontarSaldoAtomico(cuenta.id(), montoTotal, ahora);
        if (!descontado) {
            throw new SaldoInsuficienteException(numeroCuenta);
        }

        Cuenta cuentaActualizada = cuentas.buscarPorId(cuenta.id())
                .orElseThrow(() -> new CuentaNoEncontradaException(numeroCuenta));

        RetencionCuenta retencion = RetencionCuenta.crear(
                cuenta.id(),
                cliente.id(),
                numeroCuenta,
                idempotencyKey,
                payloadHash,
                monto,
                comision,
                comando.concepto(),
                ahora.plus(TTL_HOLD_POR_DEFECTO),
                ahora);
        RetencionCuenta guardada = retenciones.guardar(retencion);

        String traza = TrazaActual.obtener();
        retenciones.registrarMovimiento(MovimientoContable.registrar(
                guardada.id(),
                cuenta.id(),
                TipoMovimientoContable.CARGO_RETENCION,
                montoTotal,
                cuentaActualizada.saldo(),
                traza,
                ahora));

        publicarAuditoriaRetencion("cuenta.saldo_retenido", guardada, traza, ahora);
        LOGGER.info(
                "Retencion bancaria aplicada: folio={} cuenta={} estado={} traza={}",
                guardada.id(),
                numeroCuenta.enmascarado(),
                guardada.estado(),
                traza);

        return mapeador.aDto(guardada);
    }

    @Transactional
    public RetencionCuentaDto liquidar(ComandoLiquidarRetencion comando) {
        RetencionCuenta retencion = retenciones.buscarPorId(comando.folioMovimiento())
                .orElseThrow(() -> new RetencionNoEncontradaException(comando.folioMovimiento()));
        exigeAutorizacionTitular(retencion);

        if (retencion.estado() == EstadoRetencion.LIQUIDADO) {
            return mapeador.aDto(retencion);
        }

        Instant ahora = reloj.ahora();
        retencion.liquidar(comando.referenciaAutorizacion(), ahora);
        RetencionCuenta guardada = retenciones.guardar(retencion);

        Cuenta cuenta = cuentas.buscarPorId(guardada.cuentaId())
                .orElseThrow(() -> new CuentaNoEncontradaException(guardada.numeroCuenta()));
        String traza = TrazaActual.obtener();
        retenciones.registrarMovimiento(MovimientoContable.registrar(
                guardada.id(),
                guardada.cuentaId(),
                TipoMovimientoContable.LIQUIDACION_DEFINITIVA,
                guardada.montoTotal(),
                cuenta.saldo(),
                traza,
                ahora));

        publicarAuditoriaRetencion("cuenta.retencion_liquidada", guardada, traza, ahora);
        LOGGER.info(
                "Retencion bancaria liquidada: folio={} cuenta={} estado={} traza={}",
                guardada.id(),
                guardada.numeroCuenta().enmascarado(),
                guardada.estado(),
                traza);
        return mapeador.aDto(guardada);
    }

    @Transactional
    public RetencionCuentaDto reversar(ComandoReversarRetencion comando) {
        RetencionCuenta retencion = retenciones.buscarPorId(comando.folioMovimiento())
                .orElseThrow(() -> new RetencionNoEncontradaException(comando.folioMovimiento()));
        exigeAutorizacionTitular(retencion);

        if (retencion.estado() == EstadoRetencion.REVERSADO) {
            return mapeador.aDto(retencion);
        }

        Instant ahora = reloj.ahora();
        retencion.iniciarReverso(comando.motivo(), ahora);
        cuentas.abonarSaldoAtomico(retencion.cuentaId(), retencion.montoTotal(), ahora);
        retencion.completarReverso(comando.motivo(), ahora);
        RetencionCuenta guardada = retenciones.guardar(retencion);

        Cuenta cuenta = cuentas.buscarPorId(guardada.cuentaId())
                .orElseThrow(() -> new CuentaNoEncontradaException(guardada.numeroCuenta()));
        String traza = TrazaActual.obtener();
        retenciones.registrarMovimiento(MovimientoContable.registrar(
                guardada.id(),
                guardada.cuentaId(),
                TipoMovimientoContable.ABONO_REVERSO_COMPENSATORIO,
                guardada.montoTotal(),
                cuenta.saldo(),
                traza,
                ahora));

        publicarAuditoriaRetencion("cuenta.retencion_reversada", guardada, traza, ahora);
        LOGGER.info(
                "Retencion bancaria reversada: folio={} cuenta={} estado={} traza={}",
                guardada.id(),
                guardada.numeroCuenta().enmascarado(),
                guardada.estado(),
                traza);
        return mapeador.aDto(guardada);
    }

    @Transactional
    public RetencionCuentaDto marcarParaRevision(ComandoRevisionRetencion comando) {
        RetencionCuenta retencion = retenciones.buscarPorId(comando.folioMovimiento())
                .orElseThrow(() -> new RetencionNoEncontradaException(comando.folioMovimiento()));
        exigeAutorizacionTitular(retencion);

        if (retencion.estado() == EstadoRetencion.REQUIERE_REVISION) {
            return mapeador.aDto(retencion);
        }

        Instant ahora = reloj.ahora();
        retencion.marcarRequiereRevision(comando.motivo(), ahora);
        RetencionCuenta guardada = retenciones.guardar(retencion);

        String traza = TrazaActual.obtener();
        publicarAuditoriaRetencion("cuenta.retencion_requiere_revision", guardada, traza, ahora);
        LOGGER.warn(
                "Retencion bancaria enviada a REQUIERE_REVISION: folio={} cuenta={} traza={}",
                guardada.id(),
                guardada.numeroCuenta().enmascarado(),
                traza);
        return mapeador.aDto(guardada);
    }

    @Transactional(readOnly = true)
    public RetencionCuentaDto consultar(ConsultaRetencion consulta) {
        RetencionCuenta retencion;
        if (consulta.folioMovimiento() != null) {
            retencion = retenciones.buscarPorId(consulta.folioMovimiento())
                    .orElseThrow(() -> new RetencionNoEncontradaException(consulta.folioMovimiento()));
        } else if (consulta.numeroCuenta() != null && !consulta.numeroCuenta().isBlank()
                && consulta.idempotencyKey() != null && !consulta.idempotencyKey().isBlank()) {
            NumeroCuenta numero = NumeroCuenta.de(consulta.numeroCuenta());
            retencion = retenciones.buscarPorNumeroCuentaEIdempotencia(numero, consulta.idempotencyKey().trim())
                    .orElseThrow(() -> new RetencionNoEncontradaException(consulta.idempotencyKey()));
        } else {
            throw new ErrorNegocio(
                    CodigoError.SOLICITUD_INVALIDA,
                    "Debe proporcionar folioMovimiento o la combinacion de numeroCuenta e idempotencyKey");
        }
        exigeAutorizacionTitular(retencion);
        return mapeador.aDto(retencion);
    }

    private void validarPinAutorizacion(java.util.UUID clienteId, String pinEnClaro, Instant ahora) {
        Usuario usuario = usuarios.buscarPorCliente(clienteId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(clienteId));
        if (!usuario.activo()) {
            throw new UsuarioInactivoException(usuario.correo().valor());
        }
        if (!usuario.tienePin()) {
            throw new PinNoConfiguradoException();
        }
        if (usuario.estaPinBloqueado(ahora)) {
            throw new PinBloqueadoException(usuario.pinBloqueadoHasta());
        }
        boolean coincide = generadorHash.coincide(pinEnClaro, usuario.hashPin());
        if (!coincide) {
            int restantes = registradorIntentosPin.registrarFalloAtomico(usuario, ahora);
            throw new PinInvalidoException(restantes);
        }
        registradorIntentosPin.registrarExitoAtomico(usuario, ahora);
    }

    private void exigeAutorizacionTitular(RetencionCuenta retencion) {
        Cliente cliente = clientes.buscarPorId(retencion.clienteId())
                .orElseThrow(() -> new ClienteNoEncontradoException(retencion.clienteId()));
        autorizador.exigeTitularCuentaOAdministrador(cliente.id(), cliente.correo().valor());
    }

    private static String calcularHashPayload(
            NumeroCuenta numeroCuenta, Dinero monto, Dinero comision, String concepto) {
        String canonico = numeroCuenta.valor()
                + "|" + monto.moneda()
                + "|" + monto.monto().toPlainString()
                + "|" + comision.monto().toPlainString()
                + "|" + (concepto == null ? "" : concepto.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private void publicarAuditoriaRetencion(
            String tipoEvento, RetencionCuenta retencion, String traza, Instant momento) {
        Map<String, Object> carga = new LinkedHashMap<>();
        carga.put("folioMovimiento", retencion.id().toString());
        carga.put("cuentaEnmascarada", retencion.numeroCuenta().enmascarado());
        carga.put("montoTotal", retencion.montoTotal().monto());
        carga.put("moneda", retencion.montoTotal().moneda());
        carga.put("estado", retencion.estado().name());
        carga.put("idempotencyKey", retencion.idempotencyKey());
        if (retencion.referenciaExterna() != null) {
            carga.put("referenciaExterna", retencion.referenciaExterna());
        }
        publicadorEvento.publicar(EventoDominio.crear(
                tipoEvento,
                EventoDominio.TIPO_RETENCION,
                retencion.id(),
                carga,
                "sistema",
                momento).conCorrelationId(traza));
    }
}
