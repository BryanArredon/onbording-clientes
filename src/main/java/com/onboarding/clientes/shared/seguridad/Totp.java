package com.onboarding.clientes.shared.seguridad;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Codigos de un solo uso basados en tiempo (TOTP, RFC 6238) compatibles con Google Authenticator,
 * Microsoft Authenticator y similares: HMAC-SHA1, 6 digitos, pasos de 30 segundos.
 *
 * <p>Se acepta una ventana de un paso hacia atras y uno hacia adelante para tolerar el desfase de reloj
 * del telefono. {@link #validar} devuelve el paso que coincidio para que el llamador rechace codigos ya
 * usados (anti-replay).
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final long PASO_SEGUNDOS = 30;
    private static final int VENTANA = 1;
    private static final int BYTES_SECRETO = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {}

    /** Genera un secreto aleatorio de 160 bits codificado en Base32 (sin relleno). */
    public static String generarSecreto() {
        byte[] bytes = new byte[BYTES_SECRETO];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    public static long pasoDe(Instant momento) {
        return momento.getEpochSecond() / PASO_SEGUNDOS;
    }

    /** Codigo esperado para un paso dado. */
    public static String codigo(String secretoBase32, long paso) {
        byte[] hash = hmacSha1(decodificarBase32(secretoBase32), ByteBuffer.allocate(8).putLong(paso).array());
        int desplazamiento = hash[hash.length - 1] & 0x0F;
        int binario = ((hash[desplazamiento] & 0x7F) << 24)
                | ((hash[desplazamiento + 1] & 0xFF) << 16)
                | ((hash[desplazamiento + 2] & 0xFF) << 8)
                | (hash[desplazamiento + 3] & 0xFF);
        int otp = binario % (int) Math.pow(10, DIGITOS);
        return String.format("%0" + DIGITOS + "d", otp);
    }

    /**
     * Valida un codigo contra la ventana permitida.
     *
     * @return el paso que coincidio, o vacio si el codigo no es valido
     */
    public static OptionalLong validar(String secretoBase32, String codigo, Instant momento) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long actual = pasoDe(momento);
        byte[] recibido = codigo.getBytes(StandardCharsets.US_ASCII);
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            byte[] esperado = codigo(secretoBase32, paso).getBytes(StandardCharsets.US_ASCII);
            // Comparacion en tiempo constante para no filtrar digitos por tiempos de respuesta.
            if (MessageDigest.isEqual(esperado, recibido)) {
                return OptionalLong.of(paso);
            }
        }
        return OptionalLong.empty();
    }

    /** URI {@code otpauth://} que la app autenticadora lee desde el codigo QR. */
    public static String uriAprovisionamiento(String emisor, String cuenta, String secretoBase32) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta
                + "?secret=" + secretoBase32
                + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmacSha1(byte[] clave, byte[] mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            return mac.doFinal(mensaje);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder((datos.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto Base32 invalido");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
