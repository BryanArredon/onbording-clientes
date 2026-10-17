package com.onboarding.clientes.cliente.application.port;

import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.shared.web.Cursor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de usuarios de acceso.
 *
 * <p>Las consultas por correo ignoran mayusculas porque el dominio normaliza el correo al construir el value
 * object, de modo que la comparacion en base de datos es siempre exacta.
 */
public interface UsuarioRepositorio {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(UUID id);

    Optional<Usuario> buscarPorCorreo(Correo correo);

    boolean existePorCorreo(Correo correo);

    boolean existePorCliente(UUID clienteId);

    /**
     * Busqueda dinamica con filtros combinables: el adaptador construye la consulta unicamente con
     * los criterios presentes.
     *
     * @param criterios criterios ya normalizados por el servicio
     * @param cursor posicion previa, {@code null} para la primera pagina
     * @param limite maximo de elementos (se pide {@code limite + 1} para detectar pagina siguiente)
     * @return hasta {@code limite + 1} elementos ordenados del mas reciente al mas antiguo
     */
    List<Usuario> buscar(CriteriosBusquedaUsuario criterios, Cursor cursor, int limite);
}
