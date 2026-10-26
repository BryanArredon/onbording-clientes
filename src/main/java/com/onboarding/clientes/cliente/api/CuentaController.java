package com.onboarding.clientes.cliente.api;

import com.onboarding.clientes.cliente.api.request.ConsultaCuentaRequest;
import com.onboarding.clientes.cliente.api.request.ConsultaRetencionRequest;
import com.onboarding.clientes.cliente.api.request.LiquidarRetencionRequest;
import com.onboarding.clientes.cliente.api.request.RetenerSaldoRequest;
import com.onboarding.clientes.cliente.api.request.ReversarRetencionRequest;
import com.onboarding.clientes.cliente.api.request.RevisionRetencionRequest;
import com.onboarding.clientes.cliente.application.command.ComandoLiquidarRetencion;
import com.onboarding.clientes.cliente.application.command.ComandoRetenerSaldo;
import com.onboarding.clientes.cliente.application.command.ComandoReversarRetencion;
import com.onboarding.clientes.cliente.application.command.ComandoRevisionRetencion;
import com.onboarding.clientes.cliente.application.dto.CuentaDto;
import com.onboarding.clientes.cliente.application.dto.RetencionCuentaDto;
import com.onboarding.clientes.cliente.application.query.ConsultaCuenta;
import com.onboarding.clientes.cliente.application.query.ConsultaRetencion;
import com.onboarding.clientes.cliente.application.service.ServicioConsultaCuenta;
import com.onboarding.clientes.cliente.application.service.ServicioRetencionCuenta;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para consultas de cuentas bancarias y gestion de retenciones (Holds) de la Saga de pagos.
 *
 * <p>Sin {@code @PathVariable} ni {@code @RequestParam}: los criterios de busqueda y comandos viajan en el
 * cuerpo de la peticion, validados con {@code @Valid}.
 */
@RestController
@RequestMapping("/cuentas")
@Tag(name = "Cuentas", description = "Consultas de cuentas bancarias, saldos y retenciones (Holds)")
public class CuentaController {

    private final ServicioConsultaCuenta servicioConsultaCuenta;
    private final ServicioRetencionCuenta servicioRetencionCuenta;

    public CuentaController(
            ServicioConsultaCuenta servicioConsultaCuenta,
            ServicioRetencionCuenta servicioRetencionCuenta) {
        this.servicioConsultaCuenta = servicioConsultaCuenta;
        this.servicioRetencionCuenta = servicioRetencionCuenta;
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

    @PostMapping("/retenciones")
    @Operation(
            summary = "Retener saldo atomicamente (Etapa 2 de la Saga de pagos)",
            description = "Valida cuenta activa (Luhn), titularidad contra JWT, PIN de 4 digitos (BCrypt, max 3 intentos) y saldo >= monto + comision, creando un Hold en estado RETENIDO.")
    @ApiResponse(responseCode = "201", description = "Retencion aplicada o recuperada por idempotencia")
    @ApiResponse(responseCode = "401", description = "PIN de autorizacion incorrecto")
    @ApiResponse(responseCode = "409", description = "Cuenta inactiva o conflicto de idempotencia")
    @ApiResponse(responseCode = "422", description = "Saldo insuficiente o PIN no configurado")
    @ApiResponse(responseCode = "423", description = "PIN bloqueado temporalmente por intentos fallidos")
    public ResponseEntity<RetencionCuentaDto> retener(@Valid @RequestBody RetenerSaldoRequest request) {
        ComandoRetenerSaldo comando = new ComandoRetenerSaldo(
                request.numeroCuenta(),
                request.pinAutorizacion(),
                request.monto(),
                request.comision(),
                request.moneda(),
                request.concepto(),
                request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioRetencionCuenta.retener(comando));
    }

    @PostMapping("/retenciones/liquidar")
    @Operation(
            summary = "Liquidar en firme una retencion bancaria (Etapa 4 aprobada)",
            description = "Transiciona el Hold de RETENIDO a LIQUIDADO de forma idempotente y registra el asiento definitivo en el ledger.")
    @ApiResponse(responseCode = "200", description = "Retencion liquidada")
    public ResponseEntity<RetencionCuentaDto> liquidar(@Valid @RequestBody LiquidarRetencionRequest request) {
        ComandoLiquidarRetencion comando = new ComandoLiquidarRetencion(
                request.folioMovimiento(), request.referenciaAutorizacion());
        return ResponseEntity.ok(servicioRetencionCuenta.liquidar(comando));
    }

    @PostMapping("/retenciones/reversar")
    @Operation(
            summary = "Reversar una retencion bancaria con abono compensatorio (Etapa 4 rechazada)",
            description = "Transiciona el Hold por REVERSANDO a REVERSADO de forma idempotente, devolviendo el monto + comision al saldo disponible.")
    @ApiResponse(responseCode = "200", description = "Retencion reversada y saldo restituido")
    public ResponseEntity<RetencionCuentaDto> reversar(@Valid @RequestBody ReversarRetencionRequest request) {
        ComandoReversarRetencion comando = new ComandoReversarRetencion(
                request.folioMovimiento(), request.motivo());
        return ResponseEntity.ok(servicioRetencionCuenta.reversar(comando));
    }

    @PostMapping("/retenciones/revision")
    @Operation(
            summary = "Escalar una retencion a REQUIERE_REVISION",
            description = "Protege los fondos retenidos cuando una transaccion queda indeterminada o requiere resolucion manual.")
    @ApiResponse(responseCode = "200", description = "Retencion escalada a REQUIERE_REVISION")
    public ResponseEntity<RetencionCuentaDto> marcarRevision(@Valid @RequestBody RevisionRetencionRequest request) {
        ComandoRevisionRetencion comando = new ComandoRevisionRetencion(
                request.folioMovimiento(), request.motivo());
        return ResponseEntity.ok(servicioRetencionCuenta.marcarParaRevision(comando));
    }

    @PostMapping("/retenciones/consulta")
    @Operation(
            summary = "Consultar el estado de una retencion bancaria",
            description = "Busca por folioMovimiento o por (numeroCuenta, idempotencyKey).")
    @ApiResponse(responseCode = "200", description = "Estado actual de la retencion")
    public ResponseEntity<RetencionCuentaDto> consultarRetencion(
            @Valid @RequestBody ConsultaRetencionRequest request) {
        ConsultaRetencion consulta = new ConsultaRetencion(
                request.folioMovimiento(), request.numeroCuenta(), request.idempotencyKey());
        return ResponseEntity.ok(servicioRetencionCuenta.consultar(consulta));
    }
}
