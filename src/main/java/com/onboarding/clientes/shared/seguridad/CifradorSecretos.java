package com.onboarding.clientes.shared.seguridad;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado simetrico autenticado (AES-256-GCM) para secretos que deben recuperarse en claro, como la
 * semilla TOTP (un hash no sirve: el servidor necesita la semilla para calcular el codigo esperado).
 *
 * <p>Formato: Base64(iv[12] || ciphertext+tag). La clave se deriva con SHA-256 de
 * {@code onboarding.seguridad.clave-cifrado} y debe provenir de una variable de entorno o gestor de
 * secretos, nunca del repositorio.
 */
@Component
public class CifradorSecretos {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_TAG = 128;

    private final SecretKeySpec clave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorSecretos(@Value("${onboarding.seguridad.clave-cifrado}") String claveConfigurada) {
        if (claveConfigurada == null || claveConfigurada.length() < 32) {
            throw new IllegalStateException("onboarding.seguridad.clave-cifrado debe tener al menos 32 caracteres");
        }
        try {
            byte[] derivada = MessageDigest.getInstance("SHA-256")
                    .digest(claveConfigurada.getBytes(StandardCharsets.UTF_8));
            this.clave = new SecretKeySpec(derivada, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[BYTES_IV];
            aleatorio.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_TAG, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible cifrar el secreto", e);
        }
    }

    public String descifrar(String textoCifrado) {
        try {
            byte[] datos = Base64.getDecoder().decode(textoCifrado);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_TAG, datos, 0, BYTES_IV));
            return new String(cipher.doFinal(datos, BYTES_IV, datos.length - BYTES_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No fue posible descifrar el secreto", e);
        }
    }
}
