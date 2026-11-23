package com.onboarding.clientes.config;

import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import com.onboarding.clientes.shared.seguridad.UsuarioAutenticado;
import com.onboarding.clientes.cliente.application.service.ServicioSesiones;
import com.onboarding.clientes.shared.error.CodigoError;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que intercepta las peticiones HTTP y autentica al usuario si se proporciona un JWT valido.
 *
 * <p>El principal instalado en el {@code SecurityContext} es {@link UsuarioAutenticado}: ademas del
 * identificador del usuario incluye el cliente dueño de la sesion y el rol, que son los datos que
 * {@code AutorizadorRecursos} usa para autorizar cada operacion (prevencion de IDOR).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** Atributo de la peticion con el id de la sesion (UUID) del token autenticado. */
    public static final String ATRIBUTO_SESION = "onboarding.sesionId";
    /** Atributo con el codigo de error cuando el token es valido pero su sesion ya no lo es. */
    public static final String ATRIBUTO_MOTIVO_RECHAZO = "onboarding.motivoRechazo";

    private final JwtTokenService jwtTokenService;
    private final ServicioSesiones servicioSesiones;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, ServicioSesiones servicioSesiones) {
        this.jwtTokenService = jwtTokenService;
        this.servicioSesiones = servicioSesiones;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                if (SecurityContextHolder.getContext().getAuthentication() == null
                        && jwtTokenService.esTokenValido(token)) {
                    Claims claims = jwtTokenService.extraerClaims(token);
                    String subject = claims.getSubject();
                    UUID usuarioId = parsearUuidSeguro(subject);
                    UUID clienteId = parsearUuidSeguro(claims.get("clienteId", String.class));
                    String correoClaim = claims.get("correo", String.class);
                    String correo = (correoClaim != null && !correoClaim.isBlank())
                            ? correoClaim.trim()
                            : (usuarioId == null && subject != null ? subject.trim() : null);
                    RolUsuario rol = claims.get("rol", String.class) != null
                            ? RolUsuario.valueOf(claims.get("rol", String.class))
                            : rolDesdeAuthorities(claims);

                    // Los tokens propios estan ligados a una sesion de servidor: si se revoco (logout, baja,
                    // acceso desde otro canal) o vencio por inactividad, el token deja de autenticar.
                    if (jwtTokenService.getEmisor().equals(claims.getIssuer())) {
                        UUID sesionId = parsearUuidSeguro(claims.get(JwtTokenService.CLAIM_SESION, String.class));
                        ServicioSesiones.Estado estado = sesionId == null || usuarioId == null
                                ? ServicioSesiones.Estado.REVOCADA
                                : servicioSesiones.validar(sesionId, usuarioId);
                        if (estado != ServicioSesiones.Estado.VIGENTE) {
                            request.setAttribute(ATRIBUTO_MOTIVO_RECHAZO, estado == ServicioSesiones.Estado.EXPIRADA
                                    ? CodigoError.SESION_EXPIRADA
                                    : CodigoError.SESION_REVOCADA);
                            throw new SesionNoVigente();
                        }
                        request.setAttribute(ATRIBUTO_SESION, sesionId);
                    }

                    UsuarioAutenticado principal = new UsuarioAutenticado(usuarioId, clienteId, rol, correo);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // Token invalido: continuar sin establecer autenticacion (Spring Security respondera 401/403)
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Corta la autenticacion de la peticion; la captura el bloque que continua sin principal. */
    private static final class SesionNoVigente extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SesionNoVigente() {
            super(null, null, false, false);
        }
    }

    private static UUID parsearUuidSeguro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Compatibilidad con tokens antiguos: si no trae el claim {@code rol} lo deduce de {@code roles}. */
    private static RolUsuario rolDesdeAuthorities(Claims claims) {
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles != null && roles.contains(RolUsuario.ADMIN.autoridad())) {
            return RolUsuario.ADMIN;
        }
        return RolUsuario.CLIENTE;
    }
}
