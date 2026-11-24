package com.onboarding.clientes.cliente.infrastructure.notificaciones;

import com.onboarding.clientes.cliente.application.port.UsuarioRepositorio;
import com.onboarding.clientes.shared.evento.EventoDominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;

/** Traduce eventos de dominio importantes en notificaciones para el cliente. */
@Component
public class NotificacionSeguridadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionSeguridadListener.class);

    private final UsuarioRepositorio usuarios;
    private final ApiGestoPagoNotificacionesClient notificaciones;

    public NotificacionSeguridadListener(
            UsuarioRepositorio usuarios,
            ApiGestoPagoNotificacionesClient notificaciones) {
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alOcurrirEvento(EventoDominio evento) {
        Mensaje mensaje = mensajeDe(evento);
        if (mensaje == null) {
            return;
        }
        Optional<String> correo = correoDe(evento);
        if (correo.isEmpty()) {
            LOGGER.warn("No se encontro correo para notificar el evento {} del agregado {}",
                    evento.tipo(), evento.agregadoId());
            return;
        }
        notificaciones.enviar(new NotificacionExterna(
                correo.get(),
                mensaje.tipo(),
                mensaje.titulo(),
                mensaje.cuerpo(),
                "evt:" + evento.id(),
                mensaje.datos(),
                mensaje.correo()));
    }

    private Optional<String> correoDe(EventoDominio evento) {
        if (EventoDominio.TIPO_USUARIO.equals(evento.agregadoTipo())) {
            return usuarios.buscarPorId(evento.agregadoId()).map(usuario -> usuario.correo().valor());
        }
        if (EventoDominio.TIPO_CLIENTE.equals(evento.agregadoTipo())) {
            return usuarios.buscarPorCliente(evento.agregadoId()).map(usuario -> usuario.correo().valor());
        }
        return Optional.empty();
    }

    private Mensaje mensajeDe(EventoDominio evento) {
        Map<String, Object> carga = evento.carga();
        return switch (evento.tipo()) {
            case "usuario.login_exitoso" -> {
                String canal = texto(carga.get("canal"), "desconocido");
                String ip = texto(carga.get("ipOrigen"), "desconocida");
                String dispositivo = texto(carga.get("dispositivo"), "desconocido");
                yield new Mensaje(
                        "SEGURIDAD",
                        "Nuevo inicio de sesion",
                        "Se inicio sesion en tu cuenta. Canal: " + canal
                                + ". IP: " + ip + ". Dispositivo: " + dispositivo
                                + ". Si no fuiste tu, cambia tu contrasena de inmediato.",
                        Map.of("canal", canal, "ip", ip, "dispositivo", dispositivo),
                        true);
            }
            case "usuario.mfa_enrolado" -> new Mensaje(
                    "SEGURIDAD",
                    "Segundo factor activado",
                    "Se activo la verificacion en dos pasos para tu cuenta. A partir de ahora se pedira un codigo al iniciar sesion.",
                    Map.of("evento", "mfa_enrolado"),
                    true);
            case "usuario.password_actualizado" -> new Mensaje(
                    "SEGURIDAD",
                    "Contrasena actualizada",
                    "La contrasena de tu cuenta fue actualizada. Si no fuiste tu, contacta a soporte de inmediato.",
                    Map.of("evento", "password_actualizado"),
                    true);
            case "usuario.pin_actualizado" -> new Mensaje(
                    "SEGURIDAD",
                    "PIN de autorizacion actualizado",
                    "El PIN que autoriza tus pagos fue actualizado. Si no fuiste tu, contacta a soporte de inmediato.",
                    Map.of("evento", "pin_actualizado"),
                    true);
            case "usuario.pin_intento_fallido" -> {
                if (!esVerdadero(carga.get("bloqueado"))) {
                    yield null;
                }
                yield new Mensaje(
                        "SEGURIDAD",
                        "PIN bloqueado temporalmente",
                        "Tu PIN de autorizacion fue bloqueado temporalmente por varios intentos fallidos. Podras intentarlo de nuevo mas tarde.",
                        Map.of("evento", "pin_bloqueado"),
                        true);
            }
            case "sesion.reemplazada" -> {
                String canalAnterior = texto(carga.get("canalAnterior"), "desconocido");
                String canalNuevo = texto(carga.get("canalNuevo"), "desconocido");
                yield new Mensaje(
                        "SEGURIDAD",
                        "Sesion cerrada por un nuevo acceso",
                        "Se inicio sesion desde " + canalNuevo + " y se cerro la sesion que tenias abierta en "
                                + canalAnterior + ". Si no reconoces este acceso, cambia tu contrasena.",
                        Map.of("canalAnterior", canalAnterior, "canalNuevo", canalNuevo),
                        true);
            }
            case "cliente.registrado" -> new Mensaje(
                    "CUENTA",
                    "Bienvenido a Gesto Pago",
                    "Tu cuenta fue creada correctamente. Ya puedes consultar tu saldo y realizar pagos desde la app.",
                    Map.of("evento", "cliente_registrado"),
                    true);
            case "cliente.actualizado" -> new Mensaje(
                    "CUENTA",
                    "Datos de perfil actualizados",
                    "Los datos de tu perfil fueron actualizados correctamente. Si no reconoces este cambio, contacta a soporte.",
                    Map.of("evento", "cliente_actualizado"),
                    true);
            case "cliente.dado_de_baja" -> new Mensaje(
                    "CUENTA",
                    "Cuenta dada de baja",
                    "Tu cuenta de Gesto Pago fue dada de baja. Si no solicitaste esta accion, contacta a soporte de inmediato.",
                    Map.of("evento", "cliente_baja"),
                    true);
            default -> null;
        };
    }

    private static String texto(Object valor, String defecto) {
        if (valor == null) {
            return defecto;
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? defecto : texto;
    }

    private static boolean esVerdadero(Object valor) {
        return Boolean.TRUE.equals(valor) || "true".equalsIgnoreCase(String.valueOf(valor));
    }

    private record Mensaje(
            String tipo,
            String titulo,
            String cuerpo,
            Map<String, Object> datos,
            boolean correo) {
    }
}
