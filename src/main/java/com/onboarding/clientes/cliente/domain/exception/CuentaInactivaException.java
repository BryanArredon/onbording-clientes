package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

public final class CuentaInactivaException extends ErrorNegocio {

    public CuentaInactivaException(NumeroCuenta numeroCuenta) {
        super(
                CodigoError.CUENTA_INACTIVA,
                "La cuenta bancaria " + numeroCuenta.enmascarado() + " no se encuentra activa para operar");
    }
}
