package com.onboarding.clientes.cliente.application.command;

import java.util.UUID;

/** Comando para configurar o actualizar el PIN de autorizacion de un usuario. */
public record ComandoCambiarPin(
        UUID usuarioId,
        String contraseniaActual,
        String nuevoPin) {

    @Override
    public String toString() {
        return "ComandoCambiarPin[usuarioId=" + usuarioId + ", contraseniaActual=***, nuevoPin=****]";
    }
}
