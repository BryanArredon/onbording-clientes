package com.onboarding.clientes.cliente.domain.model;

/**
 * Rol asignado a un usuario de acceso.
 *
 * <p>El rol determina el alcance de las operaciones sobre los recursos:
 *
 * <ul>
 *   <li>{@code CLIENTE}: solo puede consultar y modificar los recursos propios (su cliente, sus
 *       cuentas y su usuario de acceso). Es el rol por defecto de todo registro.
 *   <li>{@code ADMIN}: puede consultar y administrar los recursos de cualquier cliente.
 * </ul>
 *
 * <p>Es la base del control de autorizacion de la API: conocer el identificador de otro recurso
 * nunca basta para acceder a el (prevencion de IDOR).
 */
public enum RolUsuario {

    CLIENTE,
    ADMIN;

    /** @return nombre del rol en el formato de autoridad de Spring Security ({@code ROLE_*}) */
    public String autoridad() {
        return "ROLE_" + name();
    }
}
