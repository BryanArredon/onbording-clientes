package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

/** El desafio de segundo factor no es valido o expiro: hay que volver a ingresar la contrasena. */
public class DesafioMfaInvalidoException extends ErrorNegocio {
    public DesafioMfaInvalidoException() {
        super(CodigoError.DESAFIO_MFA_INVALIDO, "La verificacion expiro. Inicia sesion de nuevo");
    }
}
