package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

public final class PinNoConfiguradoException extends ErrorNegocio {

    public PinNoConfiguradoException() {
        super(
                CodigoError.PIN_NO_CONFIGURADO,
                "El usuario no tiene un PIN de autorizacion de 4 digitos configurado para autorizar pagos");
    }
}
