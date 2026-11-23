package com.onboarding.clientes.cliente.domain.model;

/** Motivo por el que una sesion dejo de estar vigente. */
public enum MotivoRevocacion {
    /** El usuario cerro sesion. */
    LOGOUT,
    /** El usuario inicio sesion en otro canal y eligio cerrar esta. */
    REEMPLAZADA,
    /** Vencio por inactividad o por duracion maxima. */
    EXPIRADA,
    /** El cliente fue dado de baja. */
    BAJA
}
