package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.command.ComandoCambiarPassword;
import com.onboarding.clientes.cliente.application.command.ComandoCambiarPin;
import com.onboarding.clientes.cliente.application.command.ComandoLogin;
import com.onboarding.clientes.cliente.application.dto.LoginRespuesta;
import com.onboarding.clientes.cliente.application.dto.UsuarioDto;
import com.onboarding.clientes.cliente.application.mapper.ClienteMapeador;
import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaUsuario;
import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.application.query.ConsultaUsuario;
import com.onboarding.clientes.cliente.domain.exception.ContraseniaInvalidaException;
import com.onboarding.clientes.cliente.domain.exception.CredencialesInvalidasException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioBloqueadoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioInactivoException;
import com.onboarding.clientes.cliente.domain.exception.UsuarioNoEncontradoException;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.config.JwtTokenService;
import com.onboarding.clientes.config.PropiedadesOnboarding;
import com.onboarding.clientes.shared.evento.EventoDominio;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.id.UuidV7;
import com.onboarding.clientes.shared.seguridad.AutorizadorRecursos;
import com.onboarding.clientes.shared.seguridad.GeneradorHash;
import com.onboarding.clientes.shared.seguridad.PoliticaCredenciales;
import com.onboarding.clientes.shared.tiempo.Reloj;
import com.onboarding.clientes.shared.web.Cursor;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public ServicioAutenticacion(
            UsuarioRepositorio usuarios,
            GeneradorHash generadorHash,
            JwtTokenService jwtTokenService,
            ClienteMapeador mapeador,
            PublicadorEvento publicadorEvento,
            Reloj reloj,
            PropiedadesOnboarding propiedades,
            AutorizadorRecursos autorizador) {
        this.usuarios = usuarios;
        this.generadorHash = generadorHash;
        this.jwtTokenService = jwtTokenService;
        this.mapeador = mapeador;
        this.publicadorEvento = publicadorEvento;
        this.reloj = reloj;
        this.propiedades = propiedades;
        this.autorizador = autorizador;
    }

    @Transactional
    public LoginRespuesta login(ComandoLogin comando) {
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

        boolean coincide = generadorHash.coincide(comando.contrasenia(), usuario.hashContrasena());
        if (!coincide) {
            usuario.registrarIntentoFallido(ahora);
            usuarios.guardar(usuario);
            LOGGER.warn("Intento de login fallido para el usuario: {}", correo.valor());
            throw new CredencialesInvalidasException();
        }

        usuario.registrarAccesoExitoso(ahora);
        usuarios.guardar(usuario);

        String accessToken = jwtTokenService.generarAccessToken(usuario);
        String refreshToken = jwtTokenService.generarRefreshToken(usuario, UuidV7.generar());

        publicadorEvento.publicar(EventoDominio.crear(
                "usuario.login_exitoso",
                EventoDominio.TIPO_USUARIO,
                usuario.id(),
                Map.of("correo", correo.valor(), "ipOrigen", comando.ipOrigen() != null ? comando.ipOrigen() : "desconocida"),
                "sistema",
                ahora));

        LOGGER.info("Login exitoso para usuario: {}", correo.valor());
        return new LoginRespuesta(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenService.getDuracionAccesoSegundos(),
                usuario.clienteId(),
                usuario.id(),
                usuario.rol());
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
