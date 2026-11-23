package com.onboarding.clientes.cliente.application.command;

/** Comando con las credenciales necesarias para iniciar sesion. */
public record ComandoLogin(
        String correo,
        String contrasenia,
        String ipOrigen,
        String dispositivo,
        String canal) {

    public ComandoLogin(String correo, String contrasenia, String ipOrigen, String dispositivo) {
        this(correo, contrasenia, ipOrigen, dispositivo, "WEB");
    }

    @Override
    public String toString() {
        return "ComandoLogin[correo=" + correo + ", contrasenia=***, canal=" + canal + "]";
    }
}
