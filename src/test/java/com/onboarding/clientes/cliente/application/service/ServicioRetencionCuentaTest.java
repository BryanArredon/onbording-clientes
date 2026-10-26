package com.onboarding.clientes.cliente.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.onboarding.clientes.cliente.domain.exception.ConflictoIdempotenciaException;
import com.onboarding.clientes.cliente.domain.exception.PinBloqueadoException;
import com.onboarding.clientes.cliente.domain.exception.PinInvalidoException;
import com.onboarding.clientes.cliente.domain.exception.SaldoInsuficienteException;
import com.onboarding.clientes.cliente.domain.model.Cliente;
import com.onboarding.clientes.cliente.domain.model.Cuenta;
import com.onboarding.clientes.cliente.domain.model.Domicilio;
import com.onboarding.clientes.cliente.domain.model.EstadoCivil;
import com.onboarding.clientes.cliente.domain.model.EstadoRetencion;
import com.onboarding.clientes.cliente.domain.model.EstatusCuenta;
import com.onboarding.clientes.cliente.domain.model.RetencionCuenta;
import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import com.onboarding.clientes.cliente.domain.model.Sexo;
import com.onboarding.clientes.cliente.domain.model.TipoCuenta;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.CodigoPostal;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.cliente.domain.valueobject.Curp;
import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.cliente.domain.valueobject.Rfc;
import com.onboarding.clientes.cliente.domain.valueobject.Telefono;
import com.onboarding.clientes.shared.dinero.Dinero;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.seguridad.GeneradorHash;
import com.onboarding.clientes.shared.tiempo.Reloj;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioRetencionCuentaTest {

    private static final Instant AHORA = Instant.parse("2026-10-24T12:00:00Z");
    private static final String NUMERO_CUENTA_VALIDO = "0000000018";
    private static final UUID CLIENTE_ID = UUID.fromString("0192b100-0000-7000-8000-000000000001");
    private static final UUID CUENTA_ID = UUID.fromString("0192b100-0000-7000-8000-000000000002");
    private static final UUID USUARIO_ID = UUID.fromString("0192b100-0000-7000-8000-000000000003");

    @Mock
    private CuentaRepositorio cuentas;

    @Mock
    private ClienteRepositorio clientes;

    @Mock
    private UsuarioRepositorio usuarios;

    @Mock
    private RetencionCuentaRepositorio retenciones;

    @Mock
    private GeneradorHash generadorHash;

    @Mock
    private PublicadorEvento publicadorEvento;

    @Mock
    private AutorizadorRecursos autorizador;

    @Mock
    private Reloj reloj;

    private RegistradorIntentosPin registradorIntentosPin;
    private ServicioRetencionCuenta servicio;

    @BeforeEach
    void configurar() {
        registradorIntentosPin = new RegistradorIntentosPin(usuarios, publicadorEvento);
        servicio = new ServicioRetencionCuenta(
                cuentas,
                clientes,
                usuarios,
                retenciones,
                registradorIntentosPin,
                generadorHash,
                publicadorEvento,
                autorizador,
                new ClienteMapeador(),
                reloj);
    }

    @Test
    @DisplayName("Retiene saldo atomicamente cuando la cuenta esta activa, el PIN es valido y hay saldo suficiente")
    void retieneSaldoExitosamente() {
        Cuenta cuentaInicial = cuentaActiva(new BigDecimal("500.00"));
        Cuenta cuentaDespues = cuentaActiva(new BigDecimal("390.00"));
        Cliente cliente = clienteActivo(cuentaInicial);
        Usuario usuario = usuarioConPin(0, null);

        when(reloj.ahora()).thenReturn(AHORA);
        when(cuentas.buscarPorNumero(NumeroCuenta.de(NUMERO_CUENTA_VALIDO))).thenReturn(Optional.of(cuentaInicial));
        when(clientes.buscarPorId(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        doNothing().when(autorizador).exigeTitularCuentaOAdministrador(CLIENTE_ID, "cliente@banco.mx");
        when(retenciones.buscarPorCuentaEIdempotencia(CUENTA_ID, "idem-001")).thenReturn(Optional.empty());
        when(usuarios.buscarPorCliente(CLIENTE_ID)).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("4829", "$2a$12$hashPin")).thenReturn(true);
        when(cuentas.descontarSaldoAtomico(CUENTA_ID, Dinero.de(new BigDecimal("110.00"), "MXN"), AHORA))
                .thenReturn(true);
        when(cuentas.buscarPorId(CUENTA_ID)).thenReturn(Optional.of(cuentaDespues));
        when(retenciones.guardar(any(RetencionCuenta.class)))
                .thenAnswer(inv -> inv.getArgument(0, RetencionCuenta.class));

        ComandoRetenerSaldo comando = new ComandoRetenerSaldo(
                NUMERO_CUENTA_VALIDO,
                "4829",
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                "MXN",
                "Pago CFE",
                "idem-001");

        RetencionCuentaDto resultado = servicio.retener(comando);

        assertThat(resultado.estado()).isEqualTo(EstadoRetencion.RETENIDO);
        assertThat(resultado.montoTotal()).isEqualByComparingTo("110.00");
        assertThat(resultado.numeroCuenta()).isEqualTo(NUMERO_CUENTA_VALIDO);
        verify(retenciones).registrarMovimiento(any());
        verify(publicadorEvento).publicar(any());
    }

    @Test
    @DisplayName("Idempotencia devuelve la misma retencion si la clave y el payload coinciden, y rechaza con 409 si el payload cambia")
    void validaIdempotenciaConHashDePayload() {
        Cuenta cuenta = cuentaActiva(new BigDecimal("500.00"));
        Cliente cliente = clienteActivo(cuenta);

        when(cuentas.buscarPorNumero(NumeroCuenta.de(NUMERO_CUENTA_VALIDO))).thenReturn(Optional.of(cuenta));
        when(clientes.buscarPorId(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        doNothing().when(autorizador).exigeTitularCuentaOAdministrador(CLIENTE_ID, "cliente@banco.mx");

        ComandoRetenerSaldo comandoOriginal = new ComandoRetenerSaldo(
                NUMERO_CUENTA_VALIDO,
                "4829",
                new BigDecimal("100.00"),
                new BigDecimal("0.00"),
                "MXN",
                "Recarga Telcel",
                "idem-dup");

        // Primero ejecutamos una retencion para capturar su payloadHash real
        Usuario usuario = usuarioConPin(0, null);
        when(reloj.ahora()).thenReturn(AHORA);
        when(retenciones.buscarPorCuentaEIdempotencia(CUENTA_ID, "idem-dup")).thenReturn(Optional.empty());
        when(usuarios.buscarPorCliente(CLIENTE_ID)).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("4829", "$2a$12$hashPin")).thenReturn(true);
        when(cuentas.descontarSaldoAtomico(eq(CUENTA_ID), any(), eq(AHORA))).thenReturn(true);
        when(cuentas.buscarPorId(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        final RetencionCuenta[] capturada = new RetencionCuenta[1];
        when(retenciones.guardar(any(RetencionCuenta.class))).thenAnswer(inv -> {
            capturada[0] = inv.getArgument(0, RetencionCuenta.class);
            return capturada[0];
        });

        RetencionCuentaDto primera = servicio.retener(comandoOriginal);

        // Segunda llamada con misma clave y mismo payload -> idempotente
        when(retenciones.buscarPorCuentaEIdempotencia(CUENTA_ID, "idem-dup"))
                .thenReturn(Optional.of(capturada[0]));
        RetencionCuentaDto segunda = servicio.retener(comandoOriginal);
        assertThat(segunda.folioMovimiento()).isEqualTo(primera.folioMovimiento());

        // Tercera llamada con misma clave pero monto distinto -> 409 Conflict
        ComandoRetenerSaldo comandoAlterado = new ComandoRetenerSaldo(
                NUMERO_CUENTA_VALIDO,
                "4829",
                new BigDecimal("200.00"),
                new BigDecimal("0.00"),
                "MXN",
                "Recarga Telcel",
                "idem-dup");
        assertThatThrownBy(() -> servicio.retener(comandoAlterado))
                .isInstanceOf(ConflictoIdempotenciaException.class);
    }

    @Test
    @DisplayName("Incrementa intentos de PIN de forma atomica y bloquea al tercer intento fallido")
    void bloqueaPinAlTercerIntentoFallido() {
        Cuenta cuenta = cuentaActiva(new BigDecimal("500.00"));
        Cliente cliente = clienteActivo(cuenta);
        Usuario usuario = usuarioConPin(2, null);

        when(reloj.ahora()).thenReturn(AHORA);
        when(cuentas.buscarPorNumero(NumeroCuenta.de(NUMERO_CUENTA_VALIDO))).thenReturn(Optional.of(cuenta));
        when(clientes.buscarPorId(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        when(retenciones.buscarPorCuentaEIdempotencia(CUENTA_ID, "idem-pin")).thenReturn(Optional.empty());
        when(usuarios.buscarPorCliente(CLIENTE_ID)).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("0000", "$2a$12$hashPin")).thenReturn(false);

        ComandoRetenerSaldo comando = new ComandoRetenerSaldo(
                NUMERO_CUENTA_VALIDO,
                "0000",
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                "MXN",
                "Recarga",
                "idem-pin");

        assertThatThrownBy(() -> servicio.retener(comando))
                .isInstanceOf(PinInvalidoException.class);

        assertThat(usuario.intentosFallidosPin()).isEqualTo(3);
        assertThat(usuario.estaPinBloqueado(AHORA)).isTrue();
        verify(usuarios).guardar(usuario);
        verify(cuentas, never()).descontarSaldoAtomico(any(), any(), any());

        // Un cuarto intento mientras sigue bloqueado lanza PinBloqueadoException sin verificar BCrypt
        assertThatThrownBy(() -> servicio.retener(comando))
                .isInstanceOf(PinBloqueadoException.class);
    }

    @Test
    @DisplayName("Rechaza con SaldoInsuficienteException si el UPDATE atomico no afecta filas")
    void rechazaPorSaldoInsuficiente() {
        Cuenta cuenta = cuentaActiva(new BigDecimal("50.00"));
        Cliente cliente = clienteActivo(cuenta);
        Usuario usuario = usuarioConPin(0, null);

        when(reloj.ahora()).thenReturn(AHORA);
        when(cuentas.buscarPorNumero(NumeroCuenta.de(NUMERO_CUENTA_VALIDO))).thenReturn(Optional.of(cuenta));
        when(clientes.buscarPorId(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        when(retenciones.buscarPorCuentaEIdempotencia(CUENTA_ID, "idem-saldo")).thenReturn(Optional.empty());
        when(usuarios.buscarPorCliente(CLIENTE_ID)).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("4829", "$2a$12$hashPin")).thenReturn(true);
        when(cuentas.descontarSaldoAtomico(CUENTA_ID, Dinero.de(new BigDecimal("100.00"), "MXN"), AHORA))
                .thenReturn(false);

        ComandoRetenerSaldo comando = new ComandoRetenerSaldo(
                NUMERO_CUENTA_VALIDO,
                "4829",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "MXN",
                "Recarga",
                "idem-saldo");

        assertThatThrownBy(() -> servicio.retener(comando))
                .isInstanceOf(SaldoInsuficienteException.class);
        verify(retenciones, never()).guardar(any());
    }

    @Test
    @DisplayName("Liquida, reversa compensatoriamente y escala a REQUIERE_REVISION respetando idempotencia")
    void cicloDeVidaLiquidacionReversoYRevision() {
        Cuenta cuenta = cuentaActiva(new BigDecimal("500.00"));
        Cliente cliente = clienteActivo(cuenta);
        RetencionCuenta retencionParaLiquidar = RetencionCuenta.crear(
                CUENTA_ID,
                CLIENTE_ID,
                NumeroCuenta.de(NUMERO_CUENTA_VALIDO),
                "idem-liq",
                "hash-1",
                Dinero.de(new BigDecimal("100.00"), "MXN"),
                Dinero.cero("MXN"),
                "Pago",
                AHORA.plusSeconds(900),
                AHORA);

        when(reloj.ahora()).thenReturn(AHORA);
        when(clientes.buscarPorId(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        when(cuentas.buscarPorId(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(retenciones.guardar(any(RetencionCuenta.class)))
                .thenAnswer(inv -> inv.getArgument(0, RetencionCuenta.class));

        when(retenciones.buscarPorId(retencionParaLiquidar.id()))
                .thenReturn(Optional.of(retencionParaLiquidar));
        RetencionCuentaDto liquidada = servicio.liquidar(
                new ComandoLiquidarRetencion(retencionParaLiquidar.id(), "AUTH-001"));
        assertThat(liquidada.estado()).isEqualTo(EstadoRetencion.LIQUIDADO);
        assertThat(liquidada.referenciaExterna()).isEqualTo("AUTH-001");

        // Reverso compensatorio sobre otra retencion
        RetencionCuenta retencionParaReversar = RetencionCuenta.crear(
                CUENTA_ID,
                CLIENTE_ID,
                NumeroCuenta.de(NUMERO_CUENTA_VALIDO),
                "idem-rev",
                "hash-2",
                Dinero.de(new BigDecimal("80.00"), "MXN"),
                Dinero.de(new BigDecimal("5.00"), "MXN"),
                "Pago fallido",
                AHORA.plusSeconds(900),
                AHORA);
        when(retenciones.buscarPorId(retencionParaReversar.id()))
                .thenReturn(Optional.of(retencionParaReversar));

        RetencionCuentaDto reversada = servicio.reversar(
                new ComandoReversarRetencion(retencionParaReversar.id(), "Rechazo proveedor"));
        assertThat(reversada.estado()).isEqualTo(EstadoRetencion.REVERSADO);
        verify(cuentas).abonarSaldoAtomico(CUENTA_ID, Dinero.de(new BigDecimal("85.00"), "MXN"), AHORA);

        // Escalamiento a REQUIERE_REVISION
        RetencionCuenta retencionIndeterminada = RetencionCuenta.crear(
                CUENTA_ID,
                CLIENTE_ID,
                NumeroCuenta.de(NUMERO_CUENTA_VALIDO),
                "idem-rev-manual",
                "hash-3",
                Dinero.de(new BigDecimal("120.00"), "MXN"),
                Dinero.cero("MXN"),
                "Pago incierto",
                AHORA.plusSeconds(900),
                AHORA);
        when(retenciones.buscarPorId(retencionIndeterminada.id()))
                .thenReturn(Optional.of(retencionIndeterminada));

        RetencionCuentaDto enRevision = servicio.marcarParaRevision(
                new ComandoRevisionRetencion(retencionIndeterminada.id(), "Timeout confirmTx agotado"));
        assertThat(enRevision.estado()).isEqualTo(EstadoRetencion.REQUIERE_REVISION);
    }

    private static Cuenta cuentaActiva(BigDecimal saldo) {
        return Cuenta.rehidratar(
                CUENTA_ID,
                CLIENTE_ID,
                NumeroCuenta.de(NUMERO_CUENTA_VALIDO),
                TipoCuenta.AHORRO,
                Dinero.de(saldo, "MXN"),
                EstatusCuenta.ACTIVA,
                1L,
                AHORA,
                AHORA);
    }

    private static Usuario usuarioConPin(int intentosPin, Instant bloqueadoHasta) {
        return Usuario.rehidratar(
                USUARIO_ID,
                CLIENTE_ID,
                Correo.de("cliente@banco.mx"),
                RolUsuario.CLIENTE,
                "$2a$12$hashPass",
                "$2a$12$hashPin",
                true,
                0,
                null,
                intentosPin,
                bloqueadoHasta,
                AHORA,
                null,
                AHORA,
                AHORA);
    }

    private static Cliente clienteActivo(Cuenta cuenta) {
        return Cliente.rehidratar(
                CLIENTE_ID,
                "Juan",
                null,
                "Perez",
                "Lopez",
                LocalDate.of(1990, 5, 15),
                Curp.de("PELJ900515HDFRRL09"),
                Rfc.de("PELJ900515AB1"),
                Sexo.HOMBRE,
                "Mexicana",
                EstadoCivil.SOLTERO,
                Correo.de("cliente@banco.mx"),
                Telefono.de("5512345678"),
                null,
                "Ingeniero",
                "ACME",
                Dinero.de(new BigDecimal("25000.00"), "MXN"),
                new Domicilio("Av Reforma", "100", null, "Juarez", "Cuauhtemoc", "CDMX", CodigoPostal.de("06600"), "México"),
                usuarioConPin(0, null),
                List.of(cuenta),
                true,
                null,
                AHORA,
                AHORA);
    }
}
