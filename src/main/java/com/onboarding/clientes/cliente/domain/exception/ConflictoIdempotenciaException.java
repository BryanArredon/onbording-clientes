package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;

public final class ConflictoIdempotenciaException extends ErrorNegocio {

    public ConflictoIdempotenciaException() {
        super(
                CodigoError.IDEMPOTENCIA_CONFLICTO,
                "La clave de idempotencia ya fue utilizada con parametros de operacion distintos");
    }
}
