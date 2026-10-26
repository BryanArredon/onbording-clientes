package com.onboarding.clientes.shared.seguridad;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Punto unico de autorizacion de la aplicacion.
 *
 * <p>Cada servicio que trabaja sobre un recurso identificado por UUID debe llamar a
 * {@link #exigePropietarioOAdministrador(UUID)} (o a la variante de usuario) antes de operar. Asi se
 * garantiza que un cliente autenticado nunca pueda leer ni modificar datos de otro cliente solo por
 * conocer su identificador (IDOR), y que el rol {@code ADMIN} es el unico con alcance global.
 */
@Component
public class AutorizadorRecursos {

    /** @return el principal autenticado; {@code null} si no hay sesion valida */
    public UsuarioAutenticado usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioAutenticado principal) {
            return principal;
        }
        return null;
    }

    public boolean esAdministrador() {
        UsuarioAutenticado usuario = usuarioActual();
        return usuario != null && usuario.esAdministrador();
    }

    /**
     * Cliente sobre el que esta permitido operar. Para {@code ADMIN} es {@code null} (sin restriccion);
     * para {@code CLIENTE} es siempre su propio cliente.
     */
    public UUID clienteIdPermitido() {
        UsuarioAutenticado usuario = usuarioActual();
        if (usuario == null) {
            throw new AccessDeniedException("No hay un usuario autenticado");
        }
        return usuario.esAdministrador() ? null : usuario.clienteId();
    }

    /** Usuario sobre el que esta permitido operar; {@code null} si el rol es {@code ADMIN}. */
    public UUID usuarioIdPermitido() {
        UsuarioAutenticado usuario = usuarioActual();
        if (usuario == null) {
            throw new AccessDeniedException("No hay un usuario autenticado");
        }
        return usuario.esAdministrador() ? null : usuario.usuarioId();
    }

    /**
     * Exige que el usuario autenticado sea el propietario del cliente indicado o administrador.
     *
     * @param clienteId identificador del cliente dueño del recurso
     * @throws AccessDeniedException si no es propietario ni administrador
     */
    public void exigePropietarioOAdministrador(UUID clienteId) {
        UsuarioAutenticado usuario = usuarioActual();
        if (usuario == null) {
            throw new AccessDeniedException("No hay un usuario autenticado");
        }
        if (usuario.esAdministrador()) {
            return;
        }
        if (clienteId == null || !clienteId.equals(usuario.clienteId())) {
            throw new AccessDeniedException("El recurso no pertenece al usuario autenticado");
        }
    }

    /**
     * Exige que el usuario autenticado sea el dueño del identificador de usuario indicado o administrador.
     *
     * @param usuarioId identificador del usuario dueño del recurso
     * @throws AccessDeniedException si no es propietario ni administrador
     */
    public void exigeUsuarioPropietarioOAdministrador(UUID usuarioId) {
        UsuarioAutenticado usuario = usuarioActual();
        if (usuario == null) {
            throw new AccessDeniedException("No hay un usuario autenticado");
        }
        if (usuario.esAdministrador()) {
            return;
        }
        if (usuarioId == null || !usuarioId.equals(usuario.usuarioId())) {
            throw new AccessDeniedException("El recurso no pertenece al usuario autenticado");
        }
    }

    /**
     * Exige que el usuario autenticado sea el titular de la cuenta (por {@code clienteId} o por correo
     * electronico homologado en el JWT) o tenga rol {@code ADMIN}.
     *
     * @param clienteId identificador del cliente titular de la cuenta
     * @param correoTitular correo electronico del titular de la cuenta
     * @throws AccessDeniedException si el token no corresponde al titular ni a un administrador
     */
    public void exigeTitularCuentaOAdministrador(UUID clienteId, String correoTitular) {
        UsuarioAutenticado usuario = usuarioActual();
        if (usuario == null) {
            throw new AccessDeniedException("No hay un usuario autenticado");
        }
        if (usuario.esAdministrador()) {
            return;
        }
        boolean coincideClienteId = clienteId != null && clienteId.equals(usuario.clienteId());
        boolean coincideCorreo = correoTitular != null
                && usuario.correo() != null
                && correoTitular.equalsIgnoreCase(usuario.correo());
        if (!coincideClienteId && !coincideCorreo) {
            throw new AccessDeniedException("La cuenta bancaria no pertenece al usuario autenticado");
        }
    }
}
