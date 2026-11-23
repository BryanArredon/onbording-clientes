package com.onboarding.clientes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.FabricaProblemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Respuesta 401 con el mismo formato ProblemDetail que el resto de la API. Sin este punto de entrada
 * Spring Security responde 403 sin cuerpo y el cliente no puede distinguir "sin sesion" de "sin permiso".
 *
 * <p>Cuando el token era valido pero su sesion fue revocada o vencio, el codigo lo indica
 * ({@code sesion_revocada} / {@code sesion_expirada}) para que la app explique al usuario que paso.
 */
@Component
public class PuntoEntradaNoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public PuntoEntradaNoAutenticado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        CodigoError codigo = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_MOTIVO_RECHAZO) instanceof CodigoError c
                ? c
                : CodigoError.NO_AUTENTICADO;
        String detalle = switch (codigo) {
            case SESION_REVOCADA -> "Tu sesion se cerro: se inicio sesion en otro dispositivo o se cerro desde otro canal";
            case SESION_EXPIRADA -> "Tu sesion expiro por inactividad. Inicia sesion de nuevo";
            default -> "Se requiere autenticacion para acceder a este recurso";
        };
        ProblemDetail problema = FabricaProblemas.crear(codigo, "No autenticado", detalle, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
