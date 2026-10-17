package com.onboarding.clientes.cliente.api;

import com.onboarding.clientes.cliente.api.request.CambiarPasswordRequest;
import com.onboarding.clientes.cliente.api.request.ConsultaUsuarioRequest;
import com.onboarding.clientes.cliente.application.command.ComandoCambiarPassword;
import com.onboarding.clientes.cliente.application.dto.UsuarioDto;
import com.onboarding.clientes.cliente.application.query.ConsultaUsuario;
import com.onboarding.clientes.cliente.application.service.ServicioAutenticacion;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para gestion de usuarios de acceso.
 *
 * <p>Sin {@code @PathVariable} ni {@code @RequestParam}: el identificador del usuario y los criterios
 * de busqueda viajan en el cuerpo de la peticion, validados con {@code @Valid}.
 */
@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuarios", description = "Consulta de usuarios y gestion de contrasena")
public class UsuarioController {

    private final ServicioAutenticacion servicioAutenticacion;

    public UsuarioController(ServicioAutenticacion servicioAutenticacion) {
        this.servicioAutenticacion = servicioAutenticacion;
    }

    @PostMapping("/consulta")
    @Operation(
            summary = "Consultar usuarios con filtros combinables",
            description = "Un unico endpoint de busqueda: id, correo, cliente asociado y estatus pueden combinarse libremente. Sin filtros se listan todos los usuarios con paginacion por cursor.")
    @ApiResponse(responseCode = "200", description = "Pagina de resultados (vacia si no hay coincidencias)")
    @ApiResponse(responseCode = "400", description = "Criterios de busqueda invalidos")
    public ResponseEntity<PaginaRespuesta<UsuarioDto>> consultar(@Valid @RequestBody ConsultaUsuarioRequest request) {

        ConsultaUsuario consulta = new ConsultaUsuario(
                request.id(),
                request.correo(),
                request.clienteId(),
                request.activo(),
                request.cursor(),
                request.limite());

        return ResponseEntity.ok(servicioAutenticacion.buscar(consulta));
    }

    @PutMapping("/password")
    @Operation(summary = "Cambiar contrasena de acceso", description = "Valida la contrasena actual y las reglas de complejidad de la nueva contrasena del usuario identificado por el id del cuerpo.")
    @ApiResponse(responseCode = "204", description = "Contrasena actualizada")
    @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos")
    @ApiResponse(responseCode = "401", description = "Contrasena actual incorrecta")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest request) {

        ComandoCambiarPassword comando = new ComandoCambiarPassword(
                request.usuarioId(), request.contraseniaActual(), request.nuevaContrasenia());
        servicioAutenticacion.cambiarContrasenia(comando);
        return ResponseEntity.noContent().build();
    }
}
