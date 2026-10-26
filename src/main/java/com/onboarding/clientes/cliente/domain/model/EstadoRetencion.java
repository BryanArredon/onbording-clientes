package com.onboarding.clientes.cliente.domain.model;

import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados cerrada para una retención bancaria (Hold) dentro de la Saga de pagos.
 */
public enum EstadoRetencion {
    RETENIDO,
    LIQUIDADO,
    REVERSANDO,
    REVERSADO,
    REQUIERE_REVISION;

    public boolean esFinal() {
        return this == LIQUIDADO || this == REVERSADO;
    }

    public boolean permiteTransicionarA(EstadoRetencion destino) {
        Set<EstadoRetencion> permitidos = switch (this) {
            case RETENIDO -> EnumSet.of(LIQUIDADO, REVERSANDO, REVERSADO, REQUIERE_REVISION);
            case REVERSANDO -> EnumSet.of(REVERSADO, REQUIERE_REVISION);
            case REQUIERE_REVISION -> EnumSet.of(LIQUIDADO, REVERSANDO, REVERSADO);
            case LIQUIDADO, REVERSADO -> EnumSet.noneOf(EstadoRetencion.class);
        };
        return permitidos.contains(destino);
    }

    public EstadoRetencion transicionarA(EstadoRetencion destino) {
        if (this == destino) {
            return this;
        }
        if (!permiteTransicionarA(destino)) {
            throw new ErrorNegocio(
                    CodigoError.ESTADO_RETENCION_INVALIDO,
                    "Transicion de retencion no permitida de " + this + " a " + destino);
        }
        return destino;
    }
}
