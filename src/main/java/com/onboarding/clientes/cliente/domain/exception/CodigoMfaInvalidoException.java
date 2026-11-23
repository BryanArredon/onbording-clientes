package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

/** El codigo de la app autenticadora es incorrecto, expiro o ya fue utilizado. */
public class CodigoMfaInvalidoException extends ErrorNegocio {
    public CodigoMfaInvalidoException() {
        super(CodigoError.CODIGO_MFA_INVALIDO, "El codigo de verificacion es incorrecto o ya expiro");
    }
}
