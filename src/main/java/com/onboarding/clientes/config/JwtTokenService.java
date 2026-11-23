package com.onboarding.clientes.config;

import com.onboarding.clientes.cliente.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio para la generacion, firma y validacion de tokens JWT propios.
 */
@Service
public class JwtTokenService {

    private final Key signingKey;
    private final String emisor;
    private final long duracionAccesoSegundos;
    private final long duracionRefreshSegundos;

    public JwtTokenService(
            @Value("${onboarding.token.secreto:onboarding-clientes-secret-key-must-be-at-least-256-bits-long-for-hmac-sha-256}") String secreto,
            PropiedadesOnboarding propiedades) {
        byte[] keyBytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Relleno seguro si el string fuera mas corto
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.emisor = propiedades.token().emisor();
        this.duracionAccesoSegundos = propiedades.token().duracionAcceso().toSeconds();
        this.duracionRefreshSegundos = propiedades.token().duracionRefresh().toSeconds();
    }

    /** Audiencia del token de acceso: es la unica que autentica peticiones a la API. */
    public static final String AUDIENCIA_ACCESO = "onboarding-api";
    /** Audiencia del token intermedio entre la contrasena y el segundo factor. */
    public static final String AUDIENCIA_MFA = "onboarding-mfa";
    public static final String CLAIM_SESION = "sid";
    private static final long DURACION_DESAFIO_SEGUNDOS = 300;

    /**
     * Token de acceso ligado a una sesion de servidor (claim {@code sid}). Aunque la firma y la
     * expiracion sean validas, el token deja de servir en cuanto la sesion se revoca.
     */
    public String generarAccessToken(Usuario usuario, UUID sesionId) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(duracionAccesoSegundos);
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(usuario.id().toString())
                .setIssuer(emisor)
                .setAudience(AUDIENCIA_ACCESO)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expira))
                .claim(CLAIM_SESION, sesionId.toString())
                .claim("clienteId", usuario.clienteId().toString())
                .claim("correo", usuario.correo().valor())
                .claim("rol", usuario.rol().name())
                .claim("roles", List.of(usuario.rol().autoridad()))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Token de corta vida que acredita que la contrasena ya se valido y falta el segundo factor. No
     * autentica ninguna peticion (su audiencia no es {@link #AUDIENCIA_ACCESO}).
     */
    public String generarTokenDesafio(Usuario usuario, String canal) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(usuario.id().toString())
                .setIssuer(emisor)
                .setAudience(AUDIENCIA_MFA)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plusSeconds(DURACION_DESAFIO_SEGUNDOS)))
                .claim("canal", canal)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** @return claims del desafio, o vacio si la firma, la audiencia o la expiracion no son validas */
    public java.util.Optional<Claims> validarTokenDesafio(String token) {
        try {
            Claims claims = extraerClaims(token);
            if (!AUDIENCIA_MFA.equals(claims.getAudience()) || !emisor.equals(claims.getIssuer())) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(claims);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    public long getDuracionDesafioSegundos() {
        return DURACION_DESAFIO_SEGUNDOS;
    }

    public String getEmisor() {
        return emisor;
    }

    public String generarRefreshToken(Usuario usuario, UUID familiaId) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(duracionRefreshSegundos);

        return Jwts.builder()
                .setSubject(usuario.id().toString())
                .setIssuer(emisor)
                .setAudience("onboarding-api-refresh")
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expira))
                .claim("tipo", "refresh")
                .claim("familiaId", familiaId.toString())
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extraerClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean esTokenValido(String token) {
        try {
            Claims claims = extraerClaims(token);
            // Solo la audiencia de acceso autentica: un token de refresco o de desafio MFA no sirve aqui.
            boolean audienciaValida = AUDIENCIA_ACCESO.equals(claims.getAudience())
                    || !emisor.equals(claims.getIssuer());
            return audienciaValida && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public long getDuracionAccesoSegundos() {
        return duracionAccesoSegundos;
    }
}
