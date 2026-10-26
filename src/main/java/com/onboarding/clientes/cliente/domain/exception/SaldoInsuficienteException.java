package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.cliente.domain.valueobject.NumeroCuenta;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

public final class SaldoInsuficienteException extends ErrorNegocio {

    public SaldoInsuficienteException(NumeroCuenta numeroCuenta) {
        super(
                CodigoError.SALDO_INSUFICIENTE,
                "La cuenta " + numeroCuenta.enmascarado() + " no cuenta con saldo disponible suficiente para cubrir el monto mas la comision");
    }
}
