package com.onboarding.clientes.cliente.application.command;

/** Segundo paso del inicio de sesion: codigo TOTP sobre un desafio emitido tras validar la contrasena. */
public record ComandoVerificarMfa(
        String tokenDesafio,
        String codigo,
        boolean cerrarSesionAnterior,
        String ipOrigen,
        String dispositivo) {

    @Override
    public String toString() {
        return "ComandoVerificarMfa[codigo=******, cerrarSesionAnterior=" + cerrarSesionAnterior + "]";
    }
}
