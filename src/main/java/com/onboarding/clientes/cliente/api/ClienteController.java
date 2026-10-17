package com.onboarding.clientes.cliente.api;

import com.onboarding.clientes.cliente.api.request.ActualizarClienteRequest;
import com.onboarding.clientes.cliente.api.request.ConsultaClienteRequest;
import com.onboarding.clientes.cliente.api.request.IdentificacionRequest;
import com.onboarding.clientes.cliente.api.request.RegistrarClienteRequest;
import com.onboarding.clientes.cliente.application.command.ComandoActualizarCliente;
import com.onboarding.clientes.cliente.application.command.ComandoRegistrarCliente;
import com.onboarding.clientes.cliente.application.dto.ClienteDetalle;
import com.onboarding.clientes.cliente.application.dto.ClienteResumen;
import com.onboarding.clientes.cliente.application.query.ConsultaCliente;
import com.onboarding.clientes.cliente.application.service.ServicioActualizacionCliente;
import com.onboarding.clientes.cliente.application.service.ServicioBajaCliente;
import com.onboarding.clientes.cliente.application.service.ServicioConsultaCliente;
import com.onboarding.clientes.cliente.application.service.ServicioRegistroCliente;
import com.onboarding.clientes.shared.web.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestion de clientes personas fisicas.
 *
 * <p>Sin {@code @PathVariable} ni {@code @RequestParam}: la identificacion del recurso y los criterios
 * de busqueda viajan siempre en el cuerpo de la peticion mediante Request DTOs validados con
 * {@code @Valid}. Toda la validacion de entrada y el manejo de errores vive en el dominio y en
 * {@code ManejadorExcepcionesGlobales}: aqui no hay bloques try-catch ni validaciones repetidas.
 */
@RestController
@RequestMapping("/clientes")
@Tag(name = "Clientes", description = "Operaciones de onboarding, consulta, actualizacion y baja de clientes")
public class ClienteController {

    private final ServicioRegistroCliente servicioRegistro;
    private final ServicioConsultaCliente servicioConsulta;
    private final ServicioActualizacionCliente servicioActualizacion;
    private final ServicioBajaCliente servicioBaja;

    public ClienteController(
            ServicioRegistroCliente servicioRegistro,
            ServicioConsultaCliente servicioConsulta,
            ServicioActualizacionCliente servicioActualizacion,
            ServicioBajaCliente servicioBaja) {
        this.servicioRegistro = servicioRegistro;
        this.servicioConsulta = servicioConsulta;
        this.servicioActualizacion = servicioActualizacion;
        this.servicioBaja = servicioBaja;
    }

    @PostMapping
    @Operation(summary = "Registrar nuevo cliente", description = "Crea el cliente, su domicilio, su cuenta bancaria y su usuario de acceso en una sola transaccion.")
    @ApiResponse(responseCode = "201", description = "Cliente registrado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos")
    @ApiResponse(responseCode = "409", description = "CURP, RFC o Correo duplicado")
    public ResponseEntity<ClienteDetalle> registrar(
            @Valid @RequestBody RegistrarClienteRequest request,
            HttpServletRequest httpRequest) {

        ComandoRegistrarCliente.Persona persona = new ComandoRegistrarCliente.Persona(
                request.persona().nombre(),
                request.persona().segundoNombre(),
                request.persona().apellidoPaterno(),
                request.persona().apellidoMaterno(),
                request.persona().fechaNacimiento(),
                request.persona().curp(),
                request.persona().rfc(),
                request.persona().sexo(),
                request.persona().nacionalidad(),
                request.persona().estadoCivil());

        ComandoRegistrarCliente.Contacto contacto = new ComandoRegistrarCliente.Contacto(
                request.contacto().correo(),
                request.contacto().telefonoMovil(),
                request.contacto().telefonoAlterno());

        ComandoRegistrarCliente.Ubicacion ubicacion = new ComandoRegistrarCliente.Ubicacion(
                request.ubicacion().calle(),
                request.ubicacion().numeroExterior(),
                request.ubicacion().numeroInterior(),
                request.ubicacion().colonia(),
                request.ubicacion().municipio(),
                request.ubicacion().estado(),
                request.ubicacion().codigoPostal(),
                request.ubicacion().pais());

        ComandoRegistrarCliente.Laboral laboral = new ComandoRegistrarCliente.Laboral(
                request.laboral().ocupacion(),
                request.laboral().empresa(),
                request.laboral().ingresoMensual(),
                request.laboral().monedaIngreso());

        ComandoRegistrarCliente.Credenciales credenciales = new ComandoRegistrarCliente.Credenciales(
                request.credenciales().contrasenia(),
                request.credenciales().pin());

        ComandoRegistrarCliente.AperturaCuenta cuenta = request.cuenta() != null
                ? new ComandoRegistrarCliente.AperturaCuenta(
                        request.cuenta().saldoInicial(),
                        request.cuenta().tipoCuenta(),
                        request.cuenta().monedaCuenta())
                : new ComandoRegistrarCliente.AperturaCuenta(null, null, null);

        ComandoRegistrarCliente comando = new ComandoRegistrarCliente(
                persona, contacto, ubicacion, laboral, credenciales, cuenta);

        ClienteDetalle detalle = servicioRegistro.registrar(comando);
        return ResponseEntity.created(URI.create("/clientes")).body(detalle);
    }

    @PostMapping("/consulta")
    @Operation(
            summary = "Consultar clientes con filtros combinables",
            description = "Un unico endpoint de busqueda: id, CURP, RFC, correo, numero de cuenta, estatus y rango de fechas pueden combinarse libremente. Con la paginacion por cursor se listan todos los clientes si no se envia ningun filtro.")
    @ApiResponse(responseCode = "200", description = "Pagina de resultados (vacia si no hay coincidencias)")
    @ApiResponse(responseCode = "400", description = "Criterios de busqueda invalidos")
    public ResponseEntity<PaginaRespuesta<ClienteResumen>> consultar(@Valid @RequestBody ConsultaClienteRequest request) {

        ConsultaCliente consulta = new ConsultaCliente(
                request.id(),
                request.curp(),
                request.rfc(),
                request.correo(),
                request.numeroCuenta(),
                request.soloActivos(),
                request.desde(),
                request.hasta(),
                request.cursor(),
                request.limite());

        return ResponseEntity.ok(servicioConsulta.buscar(consulta));
    }

    @PutMapping
    @Operation(summary = "Actualizar información de cliente", description = "Actualiza datos personales, contacto, domicilio y laboral del cliente identificado por el id del cuerpo. Protege la inmutabilidad de CURP, RFC y cuenta: el servidor ignora cualquier intento de modificacion de esos campos.")
    @ApiResponse(responseCode = "200", description = "Cliente actualizado")
    @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente")
    public ResponseEntity<ClienteDetalle> actualizar(@Valid @RequestBody ActualizarClienteRequest request) {

        ComandoActualizarCliente.Persona persona = new ComandoActualizarCliente.Persona(
                request.persona().nombre(),
                request.persona().segundoNombre(),
                request.persona().apellidoPaterno(),
                request.persona().apellidoMaterno(),
                request.persona().fechaNacimiento(),
                request.persona().curpDeclarada(),
                request.persona().rfcDeclarado(),
                request.persona().sexo(),
                request.persona().nacionalidad(),
                request.persona().estadoCivil());

        ComandoActualizarCliente.Contacto contacto = new ComandoActualizarCliente.Contacto(
                request.contacto().correo(),
                request.contacto().telefonoMovil(),
                request.contacto().telefonoAlterno());

        ComandoActualizarCliente.Ubicacion ubicacion = new ComandoActualizarCliente.Ubicacion(
                request.ubicacion().calle(),
                request.ubicacion().numeroExterior(),
                request.ubicacion().numeroInterior(),
                request.ubicacion().colonia(),
                request.ubicacion().municipio(),
                request.ubicacion().estado(),
                request.ubicacion().codigoPostal(),
                request.ubicacion().pais());

        ComandoActualizarCliente.Laboral laboral = new ComandoActualizarCliente.Laboral(
                request.laboral().ocupacion(),
                request.laboral().empresa(),
                request.laboral().ingresoMensual(),
                request.laboral().monedaIngreso());

        ComandoActualizarCliente comando = new ComandoActualizarCliente(request.id(), persona, contacto, ubicacion, laboral);
        return ResponseEntity.ok(servicioActualizacion.actualizar(request.id(), comando));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Baja lógica de cliente", description = "Desactiva al cliente identificado por el id del cuerpo y en cascada a sus cuentas bancarias y usuario de acceso.")
    @ApiResponse(responseCode = "204", description = "Cliente dado de baja")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente")
    public ResponseEntity<Void> darDeBaja(@Valid @RequestBody IdentificacionRequest request) {
        servicioBaja.darDeBaja(request.id());
        return ResponseEntity.noContent().build();
    }
}
