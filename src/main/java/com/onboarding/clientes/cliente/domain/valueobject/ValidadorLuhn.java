package com.onboarding.clientes.cliente.domain.valueobject;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validador y generador de números con el algoritmo de Luhn (Módulo 10) conforme a ISO/IEC 7812.
 * Utilizado para cuentas bancarias (10 dígitos) y números de tarjeta (PAN de 13 a 19 dígitos).
 */
public final class ValidadorLuhn {

    public static final int LONGITUD_CUENTA = 10;
    public static final int LONGITUD_TARJETA_ESTANDAR = 16;
    private static final Pattern SOLO_DIGITOS = Pattern.compile("^[0-9]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum FranquiciaTarjeta {
        VISA,
        MASTERCARD,
        AMERICAN_EXPRESS,
        CARNET,
        DISCOVER,
        DESCONOCIDA
    }

    private ValidadorLuhn() {
    }

    /**
     * Validador genérico del algoritmo de Luhn (Mod 10) para cualquier longitud (mínimo 2 dígitos).
     */
    public static boolean esLuhnValido(String numero) {
        if (numero == null) {
            return false;
        }
        String normalizado = normalizar(numero);
        int len = normalizado.length();
        if (len < 2 || !SOLO_DIGITOS.matcher(normalizado).matches()) {
            return false;
        }

        int suma = 0;
        for (int i = 0; i < len; i++) {
            int digito = Character.digit(normalizado.charAt(i), 10);
            boolean duplicar = (len - 1 - i) % 2 == 1;
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
        }
        return suma % 10 == 0;
    }

    /**
     * Valida si un número de tarjeta (PAN de 13 a 19 dígitos) cumple con ISO/IEC 7812 y Luhn.
     */
    public static boolean esTarjetaValida(String numeroTarjeta) {
        if (numeroTarjeta == null || numeroTarjeta.isBlank()) {
            return false;
        }
        String normalizado = normalizar(numeroTarjeta);
        int len = normalizado.length();
        if (len < 13 || len > 19 || !SOLO_DIGITOS.matcher(normalizado).matches()) {
            return false;
        }
        return esLuhnValido(normalizado);
    }

    /**
     * Calcula el dígito verificador Luhn (0 a 9) para una base de dígitos dada.
     */
    public static String calcularDigitoLuhn(String base) {
        Objects.requireNonNull(base, "La base numérica no puede ser nula");
        String normalizado = normalizar(base);
        if (normalizado.isEmpty() || !SOLO_DIGITOS.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("La base debe contener al menos un dígito");
        }

        int suma = 0;
        int len = normalizado.length();
        for (int i = 0; i < len; i++) {
            int digito = Character.digit(normalizado.charAt(i), 10);
            boolean duplicar = (len - 1 - i) % 2 == 0;
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
        }
        int digitoVerificador = (10 - (suma % 10)) % 10;
        return String.valueOf(digitoVerificador);
    }

    /**
     * Genera un número de tarjeta válido de la longitud especificada con el BIN provisto y checksum Luhn.
     */
    public static String generarTarjeta(String bin, int longitud) {
        Objects.requireNonNull(bin, "El BIN no puede ser nulo");
        String binLimpio = normalizar(bin);
        if (!SOLO_DIGITOS.matcher(binLimpio).matches()) {
            throw new IllegalArgumentException("El BIN solo debe contener dígitos");
        }
        int faltantes = longitud - binLimpio.length() - 1;
        if (faltantes < 0) {
            throw new IllegalArgumentException("La longitud total debe ser mayor que el BIN");
        }

        StringBuilder sb = new StringBuilder(binLimpio);
        for (int i = 0; i < faltantes; i++) {
            sb.append(RANDOM.nextInt(10));
        }

        String base = sb.toString();
        String verificador = calcularDigitoLuhn(base);
        return base + verificador;
    }

    public static String generarTarjeta(String bin) {
        return generarTarjeta(bin, LONGITUD_TARJETA_ESTANDAR);
    }

    /**
     * Detecta la franquicia de la tarjeta según prefijo internacional.
     */
    public static FranquiciaTarjeta obtenerFranquicia(String numeroTarjeta) {
        if (numeroTarjeta == null) return FranquiciaTarjeta.DESCONOCIDA;
        String limpio = normalizar(numeroTarjeta);
        if (limpio.isEmpty()) return FranquiciaTarjeta.DESCONOCIDA;

        if (limpio.startsWith("4")) return FranquiciaTarjeta.VISA;
        if (limpio.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720).*")) return FranquiciaTarjeta.MASTERCARD;
        if (limpio.matches("^3[47].*")) return FranquiciaTarjeta.AMERICAN_EXPRESS;
        if (limpio.matches("^(506|636|286).*")) return FranquiciaTarjeta.CARNET;
        if (limpio.matches("^(6011|65|64[4-9]|622).*")) return FranquiciaTarjeta.DISCOVER;

        return FranquiciaTarjeta.DESCONOCIDA;
    }

    /**
     * Enmascara un número de tarjeta de forma segura (ej. '**** **** **** 1234').
     */
    public static String enmascararTarjeta(String numeroTarjeta) {
        if (numeroTarjeta == null || numeroTarjeta.isBlank()) {
            return "**** **** **** ****";
        }
        String limpio = normalizar(numeroTarjeta);
        if (limpio.length() <= 4) {
            return "****";
        }
        String ultimos = limpio.substring(limpio.length() - 4);
        if (limpio.length() == 15) {
            return "**** ****** *" + ultimos.substring(1);
        }
        return "**** **** **** " + ultimos;
    }

    /**
     * Agrupa el número en bloques de 4 para visualización legible.
     */
    public static String formatearTarjeta(String numeroTarjeta) {
        if (numeroTarjeta == null) return "";
        String limpio = normalizar(numeroTarjeta);
        if (obtenerFranquicia(limpio) == FranquiciaTarjeta.AMERICAN_EXPRESS && limpio.length() == 15) {
            return limpio.substring(0, 4) + " " + limpio.substring(4, 10) + " " + limpio.substring(10);
        }
        return limpio.replaceAll("(\\d{4})(?=\\d)", "$1 ").trim();
    }

    private static String normalizar(String texto) {
        return texto.replaceAll("[\\s-]", "");
    }
}

