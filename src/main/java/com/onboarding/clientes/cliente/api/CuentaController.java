package com.onboarding.clientes.cliente.api;

import com.onboarding.clientes.cliente.api.request.ConsultaCuentaRequest;
import com.onboarding.clientes.cliente.application.dto.CuentaDto;
import com.onboarding.clientes.cliente.application.query.ConsultaCuenta;
import com.onboarding.clientes.cliente.application.service.ServicioConsultaCuenta;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para consultas de cuentas bancarias.
 *
 * <p>Sin {@code @PathVariable} ni {@code @RequestParam}: los criterios de busqueda viajan en el
 * cuerpo de la peticion. La proyeccion de respuesta incluye el saldo disponible, por lo que existe
 * un unico endpoint de consulta.
 */
@RestController
@RequestMapping("/cuentas")
@Tag(name = "Cuentas", description = "Consultas de cuentas bancarias y saldos")
public class CuentaController {

    private final ServicioConsultaCuenta servicioConsultaCuenta;

    public CuentaController(ServicioConsultaCuenta servicioConsultaCuenta) {
        this.servicioConsultaCuenta = servicioConsultaCuenta;
    }

    @PostMapping("/consulta")
    @Operation(
            summary = "Consultar cuentas con filtros combinables",
            description = "Un unico endpoint de busqueda: numero de cuenta y cliente titular pueden combinarse, con paginacion por cursor. La respuesta de cada cuenta incluye su saldo disponible.")
    @ApiResponse(responseCode = "200", description = "Pagina de resultados (vacia si no hay coincidencias)")
    @ApiResponse(responseCode = "400", description = "Criterios de busqueda invalidos")
    public ResponseEntity<PaginaRespuesta<CuentaDto>> consultar(@Valid @RequestBody ConsultaCuentaRequest request) {

        ConsultaCuenta consulta = new ConsultaCuenta(
                request.numeroCuenta(),
                request.clienteId(),
                request.soloActivas(),
                request.cursor(),
                request.limite());

        return ResponseEntity.ok(servicioConsultaCuenta.buscar(consulta));
    }
}
