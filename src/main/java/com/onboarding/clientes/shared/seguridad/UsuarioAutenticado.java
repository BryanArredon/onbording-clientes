package com.onboarding.clientes.shared.seguridad;

import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado (principal de Spring Security) extraido del JWT.
 *
 * <p>Ademas del identificador del usuario expone el {@code clienteId} y el rol, de modo que los servicios
 * puedan autorizar cada operacion sobre el recurso correcto sin volver a tocar la base de datos
 * (prevencion de IDOR: el cliente autenticado solo puede tocar sus propios recursos).
 */
public record UsuarioAutenticado(UUID usuarioId, UUID clienteId, RolUsuario rol) implements UserDetails {

    public UsuarioAutenticado {
        if (rol == null) {
            rol = RolUsuario.CLIENTE;
        }
    }

    public boolean esAdministrador() {
        return rol == RolUsuario.ADMIN;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol.autoridad()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return usuarioId != null ? usuarioId.toString() : null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
