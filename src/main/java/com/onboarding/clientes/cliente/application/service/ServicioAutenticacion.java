package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.command.ComandoCambiarPassword;
import com.onboarding.clientes.cliente.application.command.ComandoCambiarPin;
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
import com.onboarding.clientes.cliente.domain.exception.ContraseniaInvalidaException;
import com.onboarding.clientes.cliente.domain.exception.CredencialesInvalidasException;
import com.onboarding.clientes.cliente.domain.exception.DesafioMfaInvalidoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioBloqueadoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioInactivoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioNoEncontradoException;
import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import com.onboarding.clientes.cliente.domain.model.FactorTotp;
import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.config.JwtTokenService;
import com.onboarding.clientes.config.PropiedadesOnboarding;
import com.onboarding.clientes.shared.evento.EventoDominio;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.seguridad.CifradorSecretos;
import com.onboarding.clientes.shared.seguridad.GeneradorHash;
import com.onboarding.clientes.shared.seguridad.PoliticaCredenciales;
import com.onboarding.clientes.shared.seguridad.Totp;
import com.onboarding.clientes.shared.tiempo.Reloj;
import com.onboarding.clientes.shared.web.Cursor;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicacion para la autenticacion y gestion de usuarios de acceso.
 */
@Service
public class ServicioAutenticacion {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioAutenticacion.class);

    private final UsuarioRepositorio usuarios;
    private final GeneradorHash generadorHash;
    private final JwtTokenService jwtTokenService;
    private final ClienteMapeador mapeador;
    private final PublicadorEvento publicadorEvento;
    private final Reloj reloj;
    private final PropiedadesOnboarding propiedades;
    private final AutorizadorRecursos autorizador;
    private final FactorTotpRepositorio factores;
    private final CifradorSecretos cifrador;
    private final ServicioSesiones servicioSesiones;

    public ServicioAutenticacion(
            UsuarioRepositorio usuarios,
            GeneradorHash generadorHash,
            JwtTokenService jwtTokenService,
            ClienteMapeador mapeador,
            PublicadorEvento publicadorEvento,
            Reloj reloj,
            PropiedadesOnboarding propiedades,
            AutorizadorRecursos autorizador,
            FactorTotpRepositorio factores,
            CifradorSecretos cifrador,
            ServicioSesiones servicioSesiones) {
        this.usuarios = usuarios;
        this.generadorHash = generadorHash;
        this.jwtTokenService = jwtTokenService;
        this.mapeador = mapeador;
        this.publicadorEvento = publicadorEvento;
        this.reloj = reloj;
        this.propiedades = propiedades;
        this.autorizador = autorizador;
        this.factores = factores;
        this.cifrador = cifrador;
        this.servicioSesiones = servicioSesiones;
    }

    /**
     * Primer factor: valida correo y contrasena y emite un desafio para el segundo factor (TOTP). No
     * entrega token de acceso: sin el codigo de la app autenticadora no hay sesion.
     *
     * <p>{@code noRollbackFor}: el intento fallido debe persistir aunque se lance la excepcion; de lo
     * contrario el rollback desharia el contador y el bloqueo por fuerza bruta nunca se activaria.
     */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public DesafioMfaRespuesta login(ComandoLogin comando) {
        Correo correo = Correo.de(comando.correo());
        Instant ahora = reloj.ahora();

        Usuario usuario = usuarios.buscarPorCorreo(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!usuario.activo()) {
            throw new UsuarioInactivoException(correo.valor());
        }
        if (usuario.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException(
                    "El usuario esta temporalmente bloqueado por superar los intentos fallidos. Intente mas tarde.");
        }
        if (!generadorHash.coincide(comando.contrasenia(), usuario.hashContrasena())) {
            usuario.registrarIntentoFallido(ahora);
            usuarios.guardar(usuario);
            LOGGER.warn("Intento de login fallido para el usuario id={}", usuario.id());
            throw new CredencialesInvalidasException();
        }

        String canal = CanalSesion.de(comando.canal()).name();
        String tokenDesafio = jwtTokenService.generarTokenDesafio(usuario, canal);
        long vigencia = jwtTokenService.getDuracionDesafioSegundos();

        FactorTotp factor = factores.buscarPorUsuario(usuario.id()).orElse(null);
        if (factor != null && factor.confirmado()) {
            return new DesafioMfaRespuesta(DesafioMfaRespuesta.CODIGO_REQUERIDO, tokenDesafio, vigencia, null, null);
        }

        // Enrolamiento: se reutiliza la semilla pendiente para que el QR escaneado siga siendo valido
        // si el usuario no termino la verificacion en el intento anterior.
        String secreto;
        if (factor == null) {
            secreto = Totp.generarSecreto();
            factores.guardar(FactorTotp.pendiente(usuario.id(), cifrador.cifrar(secreto), ahora));
        } else {
            secreto = cifrador.descifrar(factor.secretoCifrado());
        }
        String uri = Totp.uriAprovisionamiento("Gesto Pago", correo.valor(), secreto);
        return new DesafioMfaRespuesta(DesafioMfaRespuesta.ENROLAMIENTO_REQUERIDO, tokenDesafio, vigencia, uri, secreto);
    }

    /**
     * Segundo factor: valida el codigo TOTP contra el desafio, abre la sesion (una sola vigente entre web
     * y movil) y emite el token de acceso ligado a ella.
     *
     * <p>Los codigos fallidos cuentan para el mismo bloqueo que las contrasenas incorrectas, y un codigo
     * ya aceptado no puede reutilizarse.
     */
    @Transactional(noRollbackFor = CodigoMfaInvalidoException.class)
    public LoginRespuesta verificarSegundoFactor(ComandoVerificarMfa comando) {
        var claims = jwtTokenService.validarTokenDesafio(comando.tokenDesafio())
                .orElseThrow(DesafioMfaInvalidoException::new);
        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new DesafioMfaInvalidoException();
        }
        Instant ahora = reloj.ahora();
        Usuario usuario = usuarios.buscarPorId(usuarioId).orElseThrow(DesafioMfaInvalidoException::new);
        if (!usuario.activo()) {
            throw new UsuarioInactivoException(usuario.correo().valor());
        }
        if (usuario.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException(
                    "El usuario esta temporalmente bloqueado por superar los intentos fallidos. Intente mas tarde.");
        }
        FactorTotp factor = factores.buscarPorUsuario(usuarioId).orElseThrow(DesafioMfaInvalidoException::new);

        OptionalLong paso = Totp.validar(cifrador.descifrar(factor.secretoCifrado()), comando.codigo(), ahora);
        if (paso.isEmpty() || !factor.pasoDisponible(paso.getAsLong())) {
            usuario.registrarIntentoFallido(ahora);
            usuarios.guardar(usuario);
            LOGGER.warn("Codigo de segundo factor invalido para el usuario id={}", usuarioId);
            throw new CodigoMfaInvalidoException();
        }

        CanalSesion canal = CanalSesion.de(claims.get("canal", String.class));
        // Puede lanzar 409 sesion_activa: en ese caso no se consume el codigo y el cliente puede repetir
        // con cerrarSesionAnterior=true dentro de la misma ventana de 30 s.
        Sesion sesion = servicioSesiones.abrir(
                usuarioId, canal, comando.ipOrigen(), comando.dispositivo(), comando.cerrarSesionAnterior());

        boolean enrolamiento = !factor.confirmado();
        factor.registrarUso(paso.getAsLong());
        factores.guardar(factor);
        usuario.registrarAccesoExitoso(ahora);
        usuarios.guardar(usuario);

        publicadorEvento.publicar(EventoDominio.crear(
                enrolamiento ? "usuario.mfa_enrolado" : "usuario.login_exitoso",
                EventoDominio.TIPO_USUARIO,
                usuario.id(),
                Map.of("canal", canal.name(),
                        "ipOrigen", comando.ipOrigen() != null ? comando.ipOrigen() : "desconocida",
                        "dispositivo", comando.dispositivo() != null ? comando.dispositivo() : "desconocido"),
                "sistema",
                ahora));
        LOGGER.info("Login exitoso (2FA) usuario id={} canal={}", usuario.id(), canal);
        return respuestaDeAcceso(usuario, sesion);
    }

    /** Emite un token de acceso nuevo sobre la misma sesion, si sigue vigente. */
    @Transactional
    public LoginRespuesta renovar(UUID sesionId, UUID usuarioId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .filter(Usuario::activo)
                .orElseThrow(() -> new UsuarioInactivoException(usuarioId.toString()));
        return respuestaDeAcceso(usuario, servicioSesiones.renovar(sesionId));
    }

    private LoginRespuesta respuestaDeAcceso(Usuario usuario, Sesion sesion) {
        return new LoginRespuesta(
                jwtTokenService.generarAccessToken(usuario, sesion.id()),
                null,
                "Bearer",
                jwtTokenService.getDuracionAccesoSegundos(),
                usuario.clienteId(),
                usuario.id(),
                usuario.rol(),
                sesion.id());
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtenerPorId(UUID id) {
        autorizador.exigeUsuarioPropietarioOAdministrador(id);
        return usuarios.buscarPorId(id)
                .map(mapeador::aDto)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }

    /**
     * Busqueda unificada de usuarios con filtros combinables: un unico punto de entrada para consultar
     * por id, correo, cliente asociado o estatus, en cualquier combinacion.
     *
     * <p>El alcance se limita siempre al propio usuario cuando el rol no es {@code ADMIN}, de modo que
     * un cliente no puede enumerar ni descubrir usuarios de terceros (prevencion de IDOR por busqueda).
     */
    @Transactional(readOnly = true)
    public PaginaRespuesta<UsuarioDto> buscar(ConsultaUsuario consulta) {
        int limite = normalizarLimite(consulta.limite());

        UUID idAlcance = autorizador.usuarioIdPermitido();
        UUID clienteIdAlcance = autorizador.clienteIdPermitido();

        CriteriosBusquedaUsuario criterios = new CriteriosBusquedaUsuario(
                idAlcance != null ? idAlcance : consulta.id(),
                consulta.correo() != null && !consulta.correo().isBlank()
                        ? Correo.de(consulta.correo())
                        : null,
                clienteIdAlcance != null ? clienteIdAlcance : consulta.clienteId(),
                consulta.activo());

        List<Usuario> elementos = usuarios.buscar(criterios, Cursor.decodificar(consulta.cursor()), limite);
        return PaginaRespuesta.construir(
                elementos,
                limite,
                mapeador::aDto,
                usuario -> new Cursor(usuario.createdAt(), usuario.id()));
    }

    private int normalizarLimite(Integer solicitado) {
        PropiedadesOnboarding.Paginacion pag = propiedades.paginacion();
        if (solicitado == null || solicitado <= 0) {
            return pag.limitePorDefecto();
        }
        return Math.min(solicitado, pag.limiteMaximo());
    }

    @Transactional
    public void cambiarContrasenia(ComandoCambiarPassword comando) {
        autorizador.exigeUsuarioPropietarioOAdministrador(comando.usuarioId());
        Usuario usuario = usuarios.buscarPorId(comando.usuarioId())
                .orElseThrow(() -> new UsuarioNoEncontradoException(comando.usuarioId()));

        if (!usuario.activo()) {
            throw new UsuarioInactivoException(usuario.correo().valor());
        }

        if (!generadorHash.coincide(comando.contraseniaActual(), usuario.hashContrasena())) {
            throw new CredencialesInvalidasException();
        }

        int minLen = propiedades.seguridad().longitudMinimaContrasenia();
        PoliticaCredenciales.validarContrasenia(comando.nuevaContrasenia(), minLen);

        String nuevoHash = generadorHash.hash(comando.nuevaContrasenia());
        usuario.cambiarContrasena(nuevoHash);
        usuario.marcarActualizada(reloj.ahora());
        usuarios.guardar(usuario);

        publicadorEvento.publicar(EventoDominio.crear(
                "usuario.password_actualizado",
                EventoDominio.TIPO_USUARIO,
                usuario.id(),
                Map.of("correo", usuario.correo().valor()),
                "sistema",
                reloj.ahora()));

        LOGGER.info("Contrasena actualizada exitosamente para el usuario id={}", usuario.id());
    }

    @Transactional
    public void cambiarPin(ComandoCambiarPin comando) {
        autorizador.exigeUsuarioPropietarioOAdministrador(comando.usuarioId());
        Usuario usuario = usuarios.buscarPorId(comando.usuarioId())
                .orElseThrow(() -> new UsuarioNoEncontradoException(comando.usuarioId()));

        if (!usuario.activo()) {
            throw new UsuarioInactivoException(usuario.correo().valor());
        }

        if (!generadorHash.coincide(comando.contraseniaActual(), usuario.hashContrasena())) {
            throw new CredencialesInvalidasException();
        }

        PoliticaCredenciales.validarPin(comando.nuevoPin(), 4, 4);
        PoliticaCredenciales.exigePinDistintoDeContrasena(comando.nuevoPin(), comando.contraseniaActual());

        Instant ahora = reloj.ahora();
        String nuevoHash = generadorHash.hash(comando.nuevoPin());
        usuario.establecerPin(nuevoHash);
        usuario.marcarActualizada(ahora);
        usuarios.guardar(usuario);

        publicadorEvento.publicar(EventoDominio.crear(
                "usuario.pin_actualizado",
                EventoDominio.TIPO_USUARIO,
                usuario.id(),
                Map.of("usuarioId", usuario.id().toString()),
                "sistema",
                ahora));

        LOGGER.info("PIN de autorizacion actualizado exitosamente para el usuario id={}", usuario.id());
    }
}
