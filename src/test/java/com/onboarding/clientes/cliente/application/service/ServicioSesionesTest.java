package com.onboarding.clientes.cliente.application.service;

import com.onboarding.clientes.cliente.application.port.SesionRepositorio;
import com.onboarding.clientes.cliente.domain.exception.SesionActivaException;
import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import com.onboarding.clientes.cliente.domain.model.MotivoRevocacion;
import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.shared.evento.PublicadorEvento;
import com.onboarding.clientes.shared.tiempo.Reloj;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioSesionesTest {

    @Mock
    private SesionRepositorio sesiones;
    @Mock
    private PublicadorEvento publicadorEvento;
    @Mock
    private Reloj reloj;

    private ServicioSesiones servicio;
    private final Instant momento = Instant.parse("2026-11-22T12:00:00Z");
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        servicio = new ServicioSesiones(sesiones, publicadorEvento, reloj, Duration.ofMinutes(15), Duration.ofHours(8));
        lenient().when(reloj.ahora()).thenReturn(momento);
        lenient().when(sesiones.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Con una sesión vigente en móvil, abrir otra en web exige confirmación")
    void sesionVigenteExigeConfirmacion() {
        Sesion movil = sesion(CanalSesion.MOVIL, momento.minusSeconds(60));
        when(sesiones.buscarNoRevocadaPorUsuario(usuarioId)).thenReturn(Optional.of(movil));

        assertThatThrownBy(() -> servicio.abrir(usuarioId, CanalSesion.WEB, "1.1.1.1", "Chrome", false))
                .isInstanceOf(SesionActivaException.class)
                .hasMessageContaining("app movil");
        assertThat(movil.estaRevocada()).isFalse();
        verify(sesiones, never()).guardar(any());
    }

    @Test
    @DisplayName("Al confirmar, la sesión anterior se revoca como REEMPLAZADA y se abre la nueva")
    void confirmarReemplazaSesion() {
        Sesion movil = sesion(CanalSesion.MOVIL, momento.minusSeconds(60));
        when(sesiones.buscarNoRevocadaPorUsuario(usuarioId)).thenReturn(Optional.of(movil));

        Sesion nueva = servicio.abrir(usuarioId, CanalSesion.WEB, "1.1.1.1", "Chrome", true);

        assertThat(movil.motivoRevocacion()).isEqualTo(MotivoRevocacion.REEMPLAZADA);
        assertThat(nueva.canal()).isEqualTo(CanalSesion.WEB);
        assertThat(nueva.estaVigente(momento, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    @DisplayName("Una sesión inactiva más de 15 minutos no bloquea un nuevo acceso")
    void sesionInactivaNoBloquea() {
        Sesion vieja = sesion(CanalSesion.MOVIL, momento.minus(Duration.ofMinutes(16)));
        when(sesiones.buscarNoRevocadaPorUsuario(usuarioId)).thenReturn(Optional.of(vieja));

        servicio.abrir(usuarioId, CanalSesion.WEB, null, null, false);

        assertThat(vieja.motivoRevocacion()).isEqualTo(MotivoRevocacion.EXPIRADA);
    }

    @Test
    @DisplayName("Un token de una sesión cerrada (logout) deja de ser válido")
    void sesionCerradaNoValida() {
        Sesion s = sesion(CanalSesion.WEB, momento);
        s.revocar(MotivoRevocacion.LOGOUT, momento);
        when(sesiones.buscarPorId(s.id())).thenReturn(Optional.of(s));

        assertThat(servicio.validar(s.id(), usuarioId)).isEqualTo(ServicioSesiones.Estado.REVOCADA);
    }

    @Test
    @DisplayName("La sesión vence por inactividad y se marca EXPIRADA")
    void sesionVencePorInactividad() {
        Sesion s = sesion(CanalSesion.WEB, momento.minus(Duration.ofMinutes(20)));
        when(sesiones.buscarPorId(s.id())).thenReturn(Optional.of(s));

        assertThat(servicio.validar(s.id(), usuarioId)).isEqualTo(ServicioSesiones.Estado.EXPIRADA);
        assertThat(s.motivoRevocacion()).isEqualTo(MotivoRevocacion.EXPIRADA);
    }

    @Test
    @DisplayName("Un sid de otro usuario no autentica")
    void sesionDeOtroUsuario() {
        Sesion ajena = Sesion.abrir(UUID.randomUUID(), CanalSesion.WEB, null, null, momento, Duration.ofHours(8));
        when(sesiones.buscarPorId(ajena.id())).thenReturn(Optional.of(ajena));

        assertThat(servicio.validar(ajena.id(), usuarioId)).isEqualTo(ServicioSesiones.Estado.REVOCADA);
    }

    @Test
    @DisplayName("La actividad se registra como mucho una vez por minuto")
    void actividadConGranularidad() {
        Sesion s = sesion(CanalSesion.WEB, momento.minusSeconds(30));
        when(sesiones.buscarPorId(s.id())).thenReturn(Optional.of(s));

        assertThat(servicio.validar(s.id(), usuarioId)).isEqualTo(ServicioSesiones.Estado.VIGENTE);
        verify(sesiones, never()).guardar(any());
    }

    private Sesion sesion(CanalSesion canal, Instant ultimaActividad) {
        return Sesion.rehidratar(UUID.randomUUID(), usuarioId, canal, "10.0.0.1", "app", ultimaActividad.minusSeconds(1),
                ultimaActividad, ultimaActividad.plus(Duration.ofHours(8)), null, null);
    }
}
