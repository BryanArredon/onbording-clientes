package com.onboarding.clientes.cliente.api;

import com.onboarding.clientes.cliente.api.request.LoginRequest;
import com.onboarding.clientes.cliente.api.request.VerificarMfaRequest;
import com.onboarding.clientes.cliente.application.command.ComandoLogin;
import com.onboarding.clientes.cliente.application.command.ComandoVerificarMfa;
import com.onboarding.clientes.cliente.application.dto.DesafioMfaRespuesta;
import com.onboarding.clientes.cliente.application.dto.LoginRespuesta;
import com.onboarding.clientes.cliente.application.dto.SesionDto;
import com.onboarding.clientes.cliente.application.service.ServicioAutenticacion;
import com.onboarding.clientes.cliente.application.service.ServicioSesiones;
import com.onboarding.clientes.config.JwtAuthenticationFilter;
import com.onboarding.clientes.shared.error.CodigoError;
import com.onboarding.clientes.shared.error.ErrorNegocio;
import com.onboarding.clientes.shared.seguridad.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio de sesion en dos pasos (contrasena + TOTP) y administracion de la sesion de servidor.
 *
 * <p>Flujo: {@code POST /auth/login} → desafio → {@code POST /auth/mfa/verificar} → token de acceso.
 * Si el usuario ya tiene una sesion vigente en otro canal, la verificacion responde
 * {@code 409 sesion_activa}; repetirla con {@code cerrarSesionAnterior=true} la cierra y continua.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión con doble factor y gestión de sesiones")
public class AuthController {

    private final ServicioAutenticacion servicioAutenticacion;
    private final ServicioSesiones servicioSesiones;

    public AuthController(ServicioAutenticacion servicioAutenticacion, ServicioSesiones servicioSesiones) {
        this.servicioAutenticacion = servicioAutenticacion;
        this.servicioSesiones = servicioSesiones;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión (primer factor)",
            description = "Valida correo y contraseña y devuelve un desafío para el segundo factor. La primera vez incluye el URI otpauth para enrolar la app autenticadora.")
    @ApiResponse(responseCode = "200", description = "Contraseña válida: falta el código TOTP")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    @ApiResponse(responseCode = "403", description = "Usuario inactivo")
    @ApiResponse(responseCode = "423", description = "Usuario bloqueado por intentos fallidos")
    @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes")
    public ResponseEntity<DesafioMfaRespuesta> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        ComandoLogin comando = new ComandoLogin(
                request.correo(), request.contrasenia(), httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"), request.canal());
        return ResponseEntity.ok(servicioAutenticacion.login(comando));
    }

    @PostMapping("/mfa/verificar")
    @Operation(summary = "Verificar el segundo factor",
            description = "Valida el código TOTP en el servidor, abre la sesión y emite el token de acceso ligado a ella.")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "Código incorrecto, reutilizado o desafío expirado")
    @ApiResponse(responseCode = "409", description = "Ya existe una sesión activa en otro canal")
    @ApiResponse(responseCode = "423", description = "Usuario bloqueado por intentos fallidos")
    public ResponseEntity<LoginRespuesta> verificarMfa(
            @Valid @RequestBody VerificarMfaRequest request, HttpServletRequest httpRequest) {
        ComandoVerificarMfa comando = new ComandoVerificarMfa(
                request.tokenDesafio(), request.codigo(), Boolean.TRUE.equals(request.cerrarSesionAnterior()),
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(servicioAutenticacion.verificarSegundoFactor(comando));
    }

    @PostMapping("/renovar")
    @Operation(summary = "Renovar el token de acceso",
            description = "Emite un token nuevo sobre la misma sesión mientras siga vigente (inactividad máxima de 15 min, duración máxima de 8 h).")
    @ApiResponse(responseCode = "200", description = "Token renovado")
    @ApiResponse(responseCode = "401", description = "Sesión expirada o revocada")
    public ResponseEntity<LoginRespuesta> renovar(
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(servicioAutenticacion.renovar(sesionDe(httpRequest), principal.usuarioId()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión en el servidor: el token deja de ser válido de inmediato en todos los servicios.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        servicioSesiones.cerrar(sesionDe(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sesion")
    @Operation(summary = "Consultar la sesión vigente",
            description = "Introspección de la sesión del token presentado. La usan los demás servicios (api-gesto-pago) para rechazar tokens de sesiones revocadas.")
    @ApiResponse(responseCode = "200", description = "Sesión vigente")
    @ApiResponse(responseCode = "401", description = "Sesión expirada o revocada")
    public ResponseEntity<SesionDto> sesion(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(servicioSesiones.consultar(sesionDe(httpRequest)));
    }

    private static UUID sesionDe(HttpServletRequest request) {
        Object sid = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_SESION);
        if (sid instanceof UUID id) {
            return id;
        }
        throw new ErrorNegocio(CodigoError.SESION_REVOCADA, "El token no pertenece a una sesion valida");
    }
}
