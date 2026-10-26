package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import java.time.Instant;

public final class PinBloqueadoException extends ErrorNegocio {

    public PinBloqueadoException(Instant bloqueadoHasta) {
        super(
                CodigoError.PIN_BLOQUEADO,
                "El PIN de autorizacion se encuentra bloqueado temporalmente por superar el limite de 3 intentos fallidos. Vigente hasta " + bloqueadoHasta);
    }
}
