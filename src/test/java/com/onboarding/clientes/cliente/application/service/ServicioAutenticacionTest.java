package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.command.ComandoCambiarPassword;
import com.onboarding.clientes.cliente.application.command.ComandoLogin;
import com.onboarding.clientes.cliente.application.command.ComandoVerificarMfa;
import com.onboarding.clientes.cliente.application.dto.DesafioMfaRespuesta;
import com.onboarding.clientes.cliente.application.dto.LoginRespuesta;
import com.onboarding.clientes.cliente.application.dto.UsuarioDto;
import com.onboarding.clientes.cliente.application.mapper.ClienteMapeador;
import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaUsuario;
import com.onboarding.clientes.cliente.application.port.FactorTotpRepositorio;
import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.application.query.ConsultaUsuario;
import com.onboarding.clientes.cliente.domain.exception.CodigoMfaInvalidoException;
import com.onboarding.clientes.cliente.domain.exception.CredencialesInvalidasException;
import com.onboarding.clientes.cliente.domain.exception.DesafioMfaInvalidoException;
import com.onboarding.clientes.cliente.domain.exception.SesionActivaException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioInactivoException;
import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import com.onboarding.clientes.cliente.domain.model.FactorTotp;
import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.config.JwtTokenService;
import com.onboarding.clientes.config.PropiedadesOnboarding;
import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.seguridad.CifradorSecretos;
import com.onboarding.clientes.shared.seguridad.Totp;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import com.onboarding.clientes.shared.seguridad.GeneradorHash;
import com.onboarding.clientes.shared.tiempo.Reloj;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioAutenticacionTest {

    @Mock
    private UsuarioRepositorio usuarios;
    @Mock
    private GeneradorHash generadorHash;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private PublicadorEvento publicadorEvento;
    @Mock
    private Reloj reloj;
    @Mock
    private AutorizadorRecursos autorizador;
    @Mock
    private FactorTotpRepositorio factores;
    @Mock
    private ServicioSesiones servicioSesiones;

    private final CifradorSecretos cifrador = new CifradorSecretos("clave-de-pruebas-unitarias-0123456789abcdef");

    private ServicioAutenticacion servicio;
    private final Instant momento = Instant.parse("2026-01-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        PropiedadesOnboarding props = new PropiedadesOnboarding(
                new PropiedadesOnboarding.Cuenta(java.math.BigDecimal.ZERO, "MXN", "AHORRO", true),
                new PropiedadesOnboarding.Seguridad(4, 6, 5, 15, 8),
                new PropiedadesOnboarding.Paginacion(20, 100),
                new PropiedadesOnboarding.Token("onboarding-clientes", Duration.ofMinutes(15), Duration.ofDays(7), "", "", "", ""),
                new PropiedadesOnboarding.Keycloak(false, "", "", "", "", null)
        );

        servicio = new ServicioAutenticacion(
                usuarios,
                generadorHash,
                jwtTokenService,
                new ClienteMapeador(),
                publicadorEvento,
                reloj,
                props,
                autorizador,
                factores,
                cifrador,
                servicioSesiones);

        // Por defecto los tests actuan con un administrador sin alcance restringido
        lenient().when(autorizador.clienteIdPermitido()).thenReturn(null);
        lenient().when(autorizador.usuarioIdPermitido()).thenReturn(null);
        lenient().when(autorizador.esAdministrador()).thenReturn(true);
        lenient().when(reloj.ahora()).thenReturn(momento);
    }

    @Test
    @DisplayName("La contraseña correcta no entrega token: exige el segundo factor")
    void loginExigeSegundoFactor() {
        Usuario usuario = usuarioDePrueba();
        when(usuarios.buscarPorCorreo(usuario.correo())).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("Segura123!", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtTokenService.generarTokenDesafio(any(), eq("WEB"))).thenReturn("desafio.jwt");
        when(factores.buscarPorUsuario(usuario.id()))
                .thenReturn(Optional.of(FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), true, 0, momento)));

        DesafioMfaRespuesta respuesta = servicio.login(comandoLogin("Segura123!"));

        assertThat(respuesta.estado()).isEqualTo(DesafioMfaRespuesta.CODIGO_REQUERIDO);
        assertThat(respuesta.tokenDesafio()).isEqualTo("desafio.jwt");
        assertThat(respuesta.otpauthUri()).isNull();
        verify(jwtTokenService, never()).generarAccessToken(any(), any());
    }

    @Test
    @DisplayName("Sin factor confirmado el login devuelve el QR de enrolamiento con la semilla cifrada en reposo")
    void loginSinFactorIniciaEnrolamiento() {
        Usuario usuario = usuarioDePrueba();
        when(usuarios.buscarPorCorreo(usuario.correo())).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("Segura123!", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtTokenService.generarTokenDesafio(any(), any())).thenReturn("desafio.jwt");
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.empty());

        DesafioMfaRespuesta respuesta = servicio.login(comandoLogin("Segura123!"));

        assertThat(respuesta.estado()).isEqualTo(DesafioMfaRespuesta.ENROLAMIENTO_REQUERIDO);
        assertThat(respuesta.otpauthUri()).startsWith("otpauth://totp/Gesto%20Pago:").contains("secret=" + respuesta.secreto());
        verify(factores).guardar(argThat(f -> !f.confirmado()
                && !f.secretoCifrado().contains(respuesta.secreto())
                && cifrador.descifrar(f.secretoCifrado()).equals(respuesta.secreto())));
    }

    @Test
    @DisplayName("Un código TOTP válido abre la sesión y emite el token ligado a ella")
    void segundoFactorValidoAbreSesion() {
        Usuario usuario = usuarioDePrueba();
        prepararDesafio(usuario);
        FactorTotp factor = FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), false, 0, momento);
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(factor));
        Sesion sesion = Sesion.abrir(usuario.id(), CanalSesion.WEB, "127.0.0.1", "test", momento, Duration.ofHours(8));
        when(servicioSesiones.abrir(eq(usuario.id()), eq(CanalSesion.WEB), any(), any(), eq(false))).thenReturn(sesion);
        when(jwtTokenService.generarAccessToken(usuario, sesion.id())).thenReturn("access.jwt");

        LoginRespuesta respuesta = servicio.verificarSegundoFactor(comandoMfa(codigoActual(), false));

        assertThat(respuesta.accessToken()).isEqualTo("access.jwt");
        assertThat(respuesta.sesionId()).isEqualTo(sesion.id());
        assertThat(factor.confirmado()).isTrue();
        assertThat(factor.ultimoPaso()).isEqualTo(Totp.pasoDe(momento));
    }

    @Test
    @DisplayName("Un código ya utilizado se rechaza (anti-replay) y cuenta como intento fallido")
    void codigoReutilizadoSeRechaza() {
        Usuario usuario = usuarioDePrueba();
        prepararDesafio(usuario);
        long pasoUsado = Totp.pasoDe(momento) + 1;
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(
                FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), true, pasoUsado, momento)));

        assertThatThrownBy(() -> servicio.verificarSegundoFactor(comandoMfa(codigoActual(), false)))
                .isInstanceOf(CodigoMfaInvalidoException.class);
        assertThat(usuario.intentosFallidos()).isEqualTo(1);
        verify(servicioSesiones, never()).abrir(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Cinco códigos incorrectos bloquean al usuario igual que las contraseñas")
    void codigosIncorrectosBloquean() {
        Usuario usuario = usuarioDePrueba();
        prepararDesafio(usuario);
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(
                FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), true, 0, momento)));

        for (int i = 0; i < Usuario.MAX_INTENTOS; i++) {
            assertThatThrownBy(() -> servicio.verificarSegundoFactor(comandoMfa("000000", false)))
                    .isInstanceOf(CodigoMfaInvalidoException.class);
        }
        assertThat(usuario.estaBloqueado(momento)).isTrue();
    }

    @Test
    @DisplayName("Con otra sesión vigente responde sesion_activa sin consumir el código")
    void sesionActivaNoConsumeCodigo() {
        Usuario usuario = usuarioDePrueba();
        prepararDesafio(usuario);
        FactorTotp factor = FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), true, 0, momento);
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(factor));
        Sesion movil = Sesion.abrir(usuario.id(), CanalSesion.MOVIL, "10.0.0.1", "app", momento, Duration.ofHours(8));
        when(servicioSesiones.abrir(any(), any(), any(), any(), eq(false))).thenThrow(new SesionActivaException(movil));

        assertThatThrownBy(() -> servicio.verificarSegundoFactor(comandoMfa(codigoActual(), false)))
                .isInstanceOf(SesionActivaException.class);
        assertThat(factor.ultimoPaso()).isZero();
        verify(factores, never()).guardar(any());
    }

    @Test
    @DisplayName("Un desafío inválido o expirado se rechaza")
    void desafioInvalido() {
        when(jwtTokenService.validarTokenDesafio("caducado")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verificarSegundoFactor(
                new ComandoVerificarMfa("caducado", "123456", false, "127.0.0.1", "test")))
                .isInstanceOf(DesafioMfaInvalidoException.class);
    }

    private static final String SECRETO = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private Usuario usuarioDePrueba() {
        return Usuario.crear(UUID.randomUUID(), Correo.de("alejandro.morales@fintechmail.com"),
                "$2a$12$hashedPassword", null, momento);
    }

    private void prepararDesafio(Usuario usuario) {
        Claims claims = new DefaultClaims();
        claims.setSubject(usuario.id().toString());
        claims.put("canal", "WEB");
        when(jwtTokenService.validarTokenDesafio("desafio.jwt")).thenReturn(Optional.of(claims));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    private String codigoActual() {
        return Totp.codigo(SECRETO, Totp.pasoDe(momento));
    }

    private ComandoVerificarMfa comandoMfa(String codigo, boolean cerrarAnterior) {
        return new ComandoVerificarMfa("desafio.jwt", codigo, cerrarAnterior, "127.0.0.1", "test");
    }

    private ComandoLogin comandoLogin(String contrasenia) {
        return new ComandoLogin("alejandro.morales@fintechmail.com", contrasenia, "127.0.0.1", "Postman");
    }

    @Test
    @DisplayName("Debe fallar ante contraseña incorrecta")
    void debeFallarContraseniaIncorrecta() {
        UUID clienteId = UUID.randomUUID();
        Correo correo = Correo.de("alejandro.morales@fintechmail.com");
        Usuario usuario = Usuario.crear(clienteId, correo, "$2a$12$hashedPassword", null, momento);

        when(usuarios.buscarPorCorreo(correo)).thenReturn(Optional.of(usuario));
        when(generadorHash.coincide("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        ComandoLogin comando = new ComandoLogin("alejandro.morales@fintechmail.com", "WrongPassword", "127.0.0.1", "Postman");

        assertThatThrownBy(() -> servicio.login(comando))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("Debe denegar acceso si el usuario está inactivo")
    void debeDenegarAccesoUsuarioInactivo() {
        UUID clienteId = UUID.randomUUID();
        Correo correo = Correo.de("inactivo@ejemplo.com");
        Usuario usuario = Usuario.crear(clienteId, correo, "$2a$12$hashedPassword", null, momento);
        usuario.darDeBaja();

        when(usuarios.buscarPorCorreo(correo)).thenReturn(Optional.of(usuario));

        ComandoLogin comando = new ComandoLogin("inactivo@ejemplo.com", "Segura123!", "127.0.0.1", "Postman");

        assertThatThrownBy(() -> servicio.login(comando))
                .isInstanceOf(UsuarioInactivoException.class);
    }

    @Test
    @DisplayName("Debe buscar usuarios combinando filtros en el endpoint unico de consulta")
    void buscarUsuariosCombinandoFiltros() {
        UUID clienteId = UUID.randomUUID();
        Correo correo = Correo.de("alejandro.morales@fintechmail.com");
        Usuario usuario = Usuario.crear(clienteId, correo, "$2a$12$hashedPassword", null, momento);

        when(usuarios.buscar(any(CriteriosBusquedaUsuario.class), any(), anyInt()))
                .thenReturn(List.of(usuario));

        PaginaRespuesta<UsuarioDto> pagina = servicio.buscar(
                new ConsultaUsuario(null, "alejandro.morales@fintechmail.com", null, true, null, 10));

        assertThat(pagina.elementos()).hasSize(1);
        assertThat(pagina.elementos().get(0).correo()).isEqualTo(correo);
        assertThat(pagina.elementos().get(0).clienteId()).isEqualTo(clienteId);
    }

    @Test
    @DisplayName("Debe limitar la busqueda al propio usuario cuando el rol no es ADMIN")
    void buscarLimitadoAlClientePropio() {
        UUID clientePropio = UUID.randomUUID();
        UUID usuarioPropio = UUID.randomUUID();
        Correo correo = Correo.de("propio@ejemplo.com");
        Usuario usuario = Usuario.crear(clientePropio, correo, "$2a$12$hashedPassword", null, momento);

        lenient().when(autorizador.usuarioIdPermitido()).thenReturn(usuarioPropio);
        lenient().when(autorizador.clienteIdPermitido()).thenReturn(clientePropio);
        lenient().when(autorizador.esAdministrador()).thenReturn(false);

        when(usuarios.buscar(any(CriteriosBusquedaUsuario.class), any(), anyInt()))
                .thenReturn(List.of(usuario));

        // Aun pidiendo otro usuario, los criterios quedan forzados al propio
        PaginaRespuesta<UsuarioDto> pagina = servicio.buscar(
                new ConsultaUsuario(null, null, null, null, null, 10));

        verify(usuarios).buscar(
                argThat(c -> usuarioPropio.equals(c.id())
                        && clientePropio.equals(c.clienteId())),
                any(),
                anyInt());
        assertThat(pagina.elementos()).hasSize(1);
    }

    @Test
    @DisplayName("Debe denegar el cambio de contrasena de un usuario ajeno")
    void denegarCambioContrasenaDeUsuarioAjeno() {
        UUID ajeno = UUID.randomUUID();
        doThrow(new AccessDeniedException("denegado"))
                .when(autorizador).exigeUsuarioPropietarioOAdministrador(ajeno);

        assertThatThrownBy(() -> servicio.cambiarContrasenia(
                new ComandoCambiarPassword(ajeno, "Actual123!", "NuevaSegura123!")))
                .isInstanceOf(AccessDeniedException.class);
        verify(usuarios, never()).buscarPorId(any());
    }

    @Test
    @DisplayName("Debe devolver el rol del usuario en el resultado del login")
    void loginDevuelveRol() {
        Usuario usuario = usuarioDePrueba();
        prepararDesafio(usuario);
        when(factores.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(
                FactorTotp.rehidratar(usuario.id(), cifrador.cifrar(SECRETO), true, 0, momento)));
        Sesion sesion = Sesion.abrir(usuario.id(), CanalSesion.WEB, null, null, momento, Duration.ofHours(8));
        when(servicioSesiones.abrir(any(), any(), any(), any(), anyBoolean())).thenReturn(sesion);

        LoginRespuesta respuesta = servicio.verificarSegundoFactor(comandoMfa(codigoActual(), false));

        assertThat(respuesta.rol()).isEqualTo(RolUsuario.CLIENTE);
    }
}
