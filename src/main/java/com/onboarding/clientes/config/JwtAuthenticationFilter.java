package com.onboarding.clientes.config;

import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import com.onboarding.clientes.shared.seguridad.UsuarioAutenticado;
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

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
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
