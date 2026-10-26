package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import java.util.UUID;

public final class RetencionNoEncontradaException extends ErrorNegocio {

    public RetencionNoEncontradaException(UUID folioId) {
        super(
                CodigoError.RETENCION_NO_ENCONTRADA,
                "No se encontro la retencion bancaria con folio " + folioId);
    }

    public RetencionNoEncontradaException(String idempotencyKey) {
        super(
                CodigoError.RETENCION_NO_ENCONTRADA,
                "No se encontro una retencion bancaria para la clave de idempotencia indicada");
    }
}
