package com.onboarding.clientes.cliente.domain.model;

/** Canal desde el que se abrio la sesion. Un usuario solo puede tener una sesion vigente entre ambos. */
public enum CanalSesion {
    WEB,
    MOVIL;

    /** Interpreta el canal declarado por el cliente; cualquier valor desconocido se trata como WEB. */
    public static CanalSesion de(String valor) {
        return "MOVIL".equalsIgnoreCase(valor) ? MOVIL : WEB;
    }
}
