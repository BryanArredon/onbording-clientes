package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

public final class PinInvalidoException extends ErrorNegocio {

    public PinInvalidoException(int intentosRestantes) {
        super(
                CodigoError.PIN_INVALIDO,
                intentosRestantes > 0
                        ? "El PIN de autorizacion es incorrecto. Intentos restantes: " + intentosRestantes
                        : "El PIN de autorizacion es incorrecto. Se alcanzo el limite de intentos y el PIN ha sido bloqueado temporalmente.");
    }
}
