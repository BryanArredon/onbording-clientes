package com.onboarding.clientes.cliente.infrastructure.persistence.adapter;

import com.onboarding.clientes.cliente.application.port.CriteriosBusquedaUsuario;
import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.domain.model.RolUsuario;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.UsuarioEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.UsuarioSpringDataRepository;
import com.onboarding.clientes.shared.web.Cursor;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA para el puerto UsuarioRepositorio.
 */
@Component
public class UsuarioRepositorioAdaptador implements UsuarioRepositorio {

    private final UsuarioSpringDataRepository usuarioRepository;

    public UsuarioRepositorioAdaptador(UsuarioSpringDataRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity entity = usuarioRepository.findById(usuario.id()).orElseGet(UsuarioEntity::new);
        entity.setId(usuario.id());
        entity.setClienteId(usuario.clienteId());
        entity.setCorreoElectronico(usuario.correo().valor());
        entity.setRol(usuario.rol().name());
        entity.setPasswordHash(usuario.hashContrasena());
        entity.setPinHash(usuario.hashPin());
        entity.setActivo(usuario.activo());
        entity.setIntentosFallidos(usuario.intentosFallidos());
        entity.setBloqueadoHasta(usuario.bloqueadoHasta());
        entity.setUltimoAcceso(usuario.ultimoAcceso());
        entity.setKeycloakSub(usuario.keycloakSub());
        entity.setCreatedAt(usuario.createdAt());
        entity.setUpdatedAt(usuario.updatedAt());

        UsuarioEntity saved = usuarioRepository.saveAndFlush(entity);
        return aDominio(saved);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return usuarioRepository.findById(id).map(this::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(Correo correo) {
        return usuarioRepository.findByCorreoElectronicoIgnoreCase(correo.valor()).map(this::aDominio);
    }

    @Override
    public boolean existePorCorreo(Correo correo) {
        return usuarioRepository.existsByCorreoElectronicoIgnoreCase(correo.valor());
    }

    @Override
    public boolean existePorCliente(UUID clienteId) {
        return usuarioRepository.existsByClienteId(clienteId);
    }

    @Override
    public List<Usuario> buscar(CriteriosBusquedaUsuario criterios, Cursor cursor, int limite) {
        PageRequest pageable = PageRequest.of(
                0, limite + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<UsuarioEntity> entities =
                usuarioRepository.findAll(especificacion(criterios, cursor), pageable).getContent();
        return entities.stream().map(this::aDominio).toList();
    }

    /**
     * Construye la clausula WHERE unicamente con los criterios presentes (consulta dinamica).
     */
    private Specification<UsuarioEntity> especificacion(CriteriosBusquedaUsuario criterios, Cursor cursor) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (criterios.id() != null) {
                predicados.add(cb.equal(root.get("id"), criterios.id()));
            }
            if (criterios.correo() != null) {
                predicados.add(cb.equal(cb.lower(root.get("correoElectronico")), criterios.correo().valor()));
            }
            if (criterios.clienteId() != null) {
                predicados.add(cb.equal(root.get("clienteId"), criterios.clienteId()));
            }
            if (Boolean.TRUE.equals(criterios.activo())) {
                predicados.add(cb.isTrue(root.get("activo")));
            }
            if (Boolean.FALSE.equals(criterios.activo())) {
                predicados.add(cb.isFalse(root.get("activo")));
            }
            if (cursor != null) {
                predicados.add(cb.or(
                        cb.lessThan(root.get("createdAt"), cursor.marcaTiempo()),
                        cb.and(
                                cb.equal(root.get("createdAt"), cursor.marcaTiempo()),
                                cb.lessThan(root.get("id"), cursor.id()))));
            }

            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private Usuario aDominio(UsuarioEntity u) {
        return Usuario.rehidratar(
                u.getId(),
                u.getClienteId(),
                Correo.de(u.getCorreoElectronico()),
                u.getRol() != null ? RolUsuario.valueOf(u.getRol()) : RolUsuario.CLIENTE,
                u.getPasswordHash(),
                u.getPinHash(),
                u.isActivo(),
                u.getIntentosFallidos(),
                u.getBloqueadoHasta(),
                u.getUltimoAcceso(),
                u.getKeycloakSub(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
