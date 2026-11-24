package com.onboarding.clientes.cliente.infrastructure.notificaciones;

import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.cliente.domain.model.Usuario;
import com.onboarding.clientes.cliente.domain.valueobject.Correo;
import com.onboarding.clientes.shared.evento.EventoDominio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionSeguridadListenerTest {

    private static final UUID USUARIO_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private UsuarioRepositorio usuarios;

    @Mock
    private ApiGestoPagoNotificacionesClient notificaciones;

    private NotificacionSeguridadListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificacionSeguridadListener(usuarios, notificaciones);
    }

    @Test
    void notificaUnInicioDeSesion() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.correo()).thenReturn(Correo.de("cliente@test.com"));
        when(usuarios.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));

        listener.alOcurrirEvento(evento("usuario.login_exitoso", Map.of(
                "canal", "WEB",
                "ipOrigen", "10.0.0.5",
                "dispositivo", "Chrome en macOS")));

        ArgumentCaptor<NotificacionExterna> captor = ArgumentCaptor.forClass(NotificacionExterna.class);
        verify(notificaciones).enviar(captor.capture());
        NotificacionExterna enviada = captor.getValue();
        assertEquals("cliente@test.com", enviada.email());
        assertEquals("SEGURIDAD", enviada.tipo());
        assertEquals("Nuevo inicio de sesion", enviada.titulo());
        assertTrue(enviada.cuerpo().contains("10.0.0.5"));
        assertTrue(enviada.dedupeKey().startsWith("evt:"));
    }

    @Test
    void ignoraEventosDeRetencionesQueYaNotificaElApiDePagos() {
        listener.alOcurrirEvento(evento("cuenta.saldo_retenido", Map.of("monto", "100.00")));

        verify(notificaciones, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void soloNotificaElBloqueoDelPinNoCadaIntentoFallido() {
        listener.alOcurrirEvento(evento("usuario.pin_intento_fallido", Map.of(
                "intentosFallidosPin", 1,
                "bloqueado", false)));

        verify(notificaciones, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    private static EventoDominio evento(String tipo, Map<String, Object> carga) {
        return EventoDominio.crear(
                tipo,
                EventoDominio.TIPO_USUARIO,
                USUARIO_ID,
                carga,
                "sistema",
                Instant.now());
    }
}
