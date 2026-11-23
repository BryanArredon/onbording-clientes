package com.onboarding.clientes.cliente.application.dto;

/**
 * Resultado del primer factor (contrasena correcta): falta el codigo de la app autenticadora.
 *
 * @param estado {@code ENROLAMIENTO_REQUERIDO} la primera vez (incluye el QR) o {@code CODIGO_REQUERIDO}
 * @param tokenDesafio token de 5 minutos que se presenta junto con el codigo; no autentica peticiones
 * @param expiraEnSegundos vigencia del desafio
 * @param otpauthUri URI para el codigo QR; solo durante el enrolamiento
 * @param secreto la misma semilla en Base32 para captura manual; solo durante el enrolamiento
 */
public record DesafioMfaRespuesta(
        String estado,
        String tokenDesafio,
        long expiraEnSegundos,
        String otpauthUri,
        String secreto) {

    public static final String ENROLAMIENTO_REQUERIDO = "ENROLAMIENTO_REQUERIDO";
    public static final String CODIGO_REQUERIDO = "CODIGO_REQUERIDO";
}
