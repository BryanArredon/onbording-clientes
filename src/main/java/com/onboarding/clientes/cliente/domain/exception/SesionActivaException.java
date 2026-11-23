package com.onboarding.clientes.cliente.domain.exception;

import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import java.util.Map;

/**
 * El usuario ya tiene una sesion vigente en otro canal. Los detalles permiten al cliente explicar donde
 * esta abierta y pedir confirmacion antes de cerrarla; no incluyen IP completa ni identificadores internos.
 */
public class SesionActivaException extends ErrorNegocio {
    public SesionActivaException(Sesion activa) {
        super(CodigoError.SESION_ACTIVA,
                "Ya tienes una sesion activa en " + (activa.canal().name().equals("MOVIL") ? "la app movil" : "la web")
                        + ". Cierrala para continuar.",
                Map.of(
                        "canal", activa.canal().name(),
                        "dispositivo", activa.dispositivo() != null ? activa.dispositivo() : "desconocido",
                        "iniciadaEn", activa.creadaEn().toString(),
                        "ultimaActividad", activa.ultimaActividad().toString()));
    }
}
