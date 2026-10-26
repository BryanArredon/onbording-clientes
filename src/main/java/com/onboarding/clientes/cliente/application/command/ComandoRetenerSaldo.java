package com.onboarding.clientes.cliente.application.command;

import java.math.BigDecimal;

/**
 * Comando para retener fondos (Hold) en una cuenta bancaria validando PIN de 4 digitos.
 */
public record ComandoRetenerSaldo(
        String numeroCuenta,
        String pinAutorizacion,
        BigDecimal monto,
        BigDecimal comision,
        String moneda,
        String concepto,
        String idempotencyKey) {

    @Override
    public String toString() {
        return "ComandoRetenerSaldo[numeroCuenta=******"
                + (numeroCuenta != null && numeroCuenta.length() >= 4
                        ? numeroCuenta.substring(numeroCuenta.length() - 4)
                        : "****")
                + ", pinAutorizacion=****, monto=" + monto
                + ", comision=" + comision
                + ", idempotencyKey=" + idempotencyKey + "]";
    }
}
