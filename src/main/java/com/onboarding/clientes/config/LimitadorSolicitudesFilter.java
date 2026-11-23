package com.onboarding.clientes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.FabricaProblemas;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de tasa por IP para los endpoints publicos expuestos a fuerza bruta y abuso: inicio de sesion,
 * verificacion del segundo factor y alta de clientes (OWASP API4: consumo de recursos sin limite).
 *
 * <p>Ventana deslizante de 60 s en memoria. Complementa (no sustituye) el bloqueo por intentos fallidos
 * del usuario: este frena el credential stuffing contra muchas cuentas desde una misma IP. En un
 * despliegue con varias replicas se reemplazaria por un contador compartido (Redis).
 */
@Component
public class LimitadorSolicitudesFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000;
    private static final int MAX_LLAVES = 50_000;

    private final ObjectMapper objectMapper;
    private final int maxAutenticacion;
    private final int maxRegistro;
    private final Map<String, Deque<Long>> solicitudes = new ConcurrentHashMap<>();

    public LimitadorSolicitudesFilter(
            ObjectMapper objectMapper,
            @Value("${onboarding.limites.autenticacion-por-minuto:10}") int maxAutenticacion,
            @Value("${onboarding.limites.registro-por-minuto:5}") int maxRegistro) {
        this.objectMapper = objectMapper;
        this.maxAutenticacion = maxAutenticacion;
        this.maxRegistro = maxRegistro;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Integer limite = limitePara(request);
        if (limite != null && !permitir(request.getRemoteAddr() + "|" + request.getRequestURI(), limite)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), FabricaProblemas.crear(
                    CodigoError.DEMASIADAS_SOLICITUDES, "Demasiadas solicitudes",
                    "Demasiados intentos. Espera un minuto e intentalo de nuevo", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private Integer limitePara(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String ruta = request.getRequestURI();
        if (ruta.equals("/auth/login") || ruta.equals("/auth/mfa/verificar")) {
            return maxAutenticacion;
        }
        if (ruta.equals("/clientes")) {
            return maxRegistro;
        }
        return null;
    }

    private boolean permitir(String llave, int limite) {
        long ahora = System.currentTimeMillis();
        if (solicitudes.size() > MAX_LLAVES) {
            solicitudes.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long ultima = e.getValue().peekLast();
                    return ultima == null || ahora - ultima > VENTANA_MS;
                }
            });
        }
        Deque<Long> marcas = solicitudes.computeIfAbsent(llave, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > VENTANA_MS) {
                marcas.pollFirst();
            }
            if (marcas.size() >= limite) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }
}
