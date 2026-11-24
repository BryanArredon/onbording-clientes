package com.onboarding.clientes.cliente.infrastructure.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicBoolean;

/** Envia notificaciones de seguridad al API de pagos, que es el dueno de la bandeja y del correo. */
@Component
public class ApiGestoPagoNotificacionesClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGestoPagoNotificacionesClient.class);
    private static final int INTENTOS = 3;

    private final RestClient restClient;
    private final String token;
    private final boolean habilitado;
    private final AtomicBoolean avisoConfiguracion = new AtomicBoolean();

    public ApiGestoPagoNotificacionesClient(
            @Value("${onboarding.notificaciones.api-url:http://localhost:8080}") String apiUrl,
            @Value("${onboarding.notificaciones.token:}") String token,
            @Value("${onboarding.notificaciones.habilitadas:true}") boolean habilitado) {
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
        this.token = token;
        this.habilitado = habilitado;
    }

    public void enviar(NotificacionExterna notificacion) {
        if (!habilitado) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            if (avisoConfiguracion.compareAndSet(false, true)) {
                LOGGER.warn("Notificaciones externas deshabilitadas: falta onboarding.notificaciones.token");
            }
            return;
        }

        Exception ultimoError = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                restClient.post()
                        .uri("/interno/notificaciones")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(notificacion)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (Exception e) {
                ultimoError = e;
                esperar(intento);
            }
        }
        LOGGER.error("No se pudo enviar la notificacion {} tras {} intentos",
                notificacion.dedupeKey(), INTENTOS, ultimoError);
    }

    private void esperar(int intento) {
        try {
            Thread.sleep(250L * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
