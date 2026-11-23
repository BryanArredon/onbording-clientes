package com.onboarding.clientes.shared.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpTest {

    /** Semilla de los vectores de prueba del RFC 6238 (apendice B, HMAC-SHA1): "12345678901234567890". */
    private static final String SEMILLA_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    @DisplayName("Coincide con los vectores oficiales del RFC 6238 (6 dígitos menos significativos)")
    void vectoresRfc6238() {
        assertThat(Totp.codigo(SEMILLA_RFC, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.codigo(SEMILLA_RFC, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.codigo(SEMILLA_RFC, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.codigo(SEMILLA_RFC, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    @DisplayName("Acepta un paso de desfase de reloj y rechaza códigos más viejos")
    void ventanaDeTolerancia() {
        Instant ahora = Instant.ofEpochSecond(1_800_000_000L);
        long paso = Totp.pasoDe(ahora);
        String secreto = Totp.generarSecreto();

        assertThat(Totp.validar(secreto, Totp.codigo(secreto, paso), ahora)).hasValue(paso);
        assertThat(Totp.validar(secreto, Totp.codigo(secreto, paso - 1), ahora)).hasValue(paso - 1);
        assertThat(Totp.validar(secreto, Totp.codigo(secreto, paso - 3), ahora)).isEmpty();
        assertThat(Totp.validar(secreto, "12345", ahora)).isEmpty();
        assertThat(Totp.validar(secreto, null, ahora)).isEmpty();
    }

    @Test
    @DisplayName("El secreto tiene 160 bits y Base32 es reversible")
    void secretoYBase32() {
        String secreto = Totp.generarSecreto();
        assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.decodificarBase32(secreto))).isEqualTo(secreto);
    }

    @Test
    @DisplayName("El cifrado AES-GCM es reversible, no determinista y detecta manipulación")
    void cifradoAutenticado() {
        CifradorSecretos cifrador = new CifradorSecretos("clave-de-pruebas-unitarias-0123456789abcdef");
        String a = cifrador.cifrar("JBSWY3DPEHPK3PXP");
        String b = cifrador.cifrar("JBSWY3DPEHPK3PXP");

        assertThat(a).isNotEqualTo(b);
        assertThat(cifrador.descifrar(a)).isEqualTo("JBSWY3DPEHPK3PXP");
        String alterado = a.substring(0, a.length() - 3) + (a.endsWith("A==") ? "B==" : "A==");
        assertThatThrownBy(() -> cifrador.descifrar(alterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradorSecretos("corta")).isInstanceOf(IllegalStateException.class);
    }
}
