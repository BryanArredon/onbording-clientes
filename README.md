# Onboarding de Clientes Personas Físicas

API REST de alto rendimiento y grado bancario construida con **Spring Boot 3.4.x (Java 21 LTS)**, **PostgreSQL 16**, **Maven**, **Docker** y autenticación basada en **JWT / Keycloak**.

---

## 🏛️ Arquitectura del Sistema

El proyecto sigue los principios de **Arquitectura Hexagonal (Puertos y Adaptadores)** y **Feature Slicing**, garantizando un aislamiento total del modelo de dominio respecto a frameworks y detalles de infraestructura:

```
src/main/java/com/onboarding/clientes/
├── cliente/
│   ├── api/                      # Controladores REST, Request/Response DTOs con OpenAPI
│   │   ├── ClienteController.java
│   │   ├── CuentaController.java
│   │   ├── UsuarioController.java
│   │   └── AuthController.java
│   ├── application/              # Casos de uso, servicios, puertos y mappers
│   │   ├── command/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── port/
│   │   ├── query/                 # Criterios de los endpoints unicos de busqueda
│   │   └── service/
│   ├── domain/                   # Núcleo de negocio inmutable (sin Spring / JPA)
│   │   ├── exception/
│   │   ├── model/ (Cliente, Cuenta, Domicilio, Usuario)
│   │   └── valueobject/ (Curp, Rfc, Correo, Telefono, CodigoPostal, NumeroCuenta)
│   └── infrastructure/           # Adaptadores de persistencia JPA y Base de Datos
│       └── persistence/
│           ├── adapter/
│           ├── entity/
│           └── jpa/
├── config/                       # Configuración Spring Security, JWT, OpenAPI, Propiedades
└── shared/                       # Kernel compartido: Errores RFC 7807, Auditoría, Value Objects
```

---

## 📊 Diagrama Entidad - Relación (ERD)

```mermaid
erDiagram
    CLIENTE ||--|| DOMICILIO : "tiene (1:1)"
    CLIENTE ||--|| USUARIO : "accede_con (1:1)"
    CLIENTE ||--|{ CUENTA : "posee (1:N)"
    USUARIO ||--o{ REFRESH_TOKEN : "emite"
    USUARIO ||--o{ DISPOSITIVO_BIOMETRICO : "enrola"

    CLIENTE {
        uuid id PK
        varchar nombre
        varchar segundo_nombre
        varchar apellido_paterno
        varchar apellido_materno
        date fecha_nacimiento
        varchar curp UK
        varchar rfc UK
        varchar sexo
        varchar nacionalidad
        varchar estado_civil
        varchar correo_electronico UK
        char telefono_movil
        char telefono_alterno
        varchar ocupacion
        varchar empresa
        numeric ingreso_mensual
        boolean activo
        timestamptz fecha_baja
        timestamptz created_at
        timestamptz updated_at
    }

    DOMICILIO {
        uuid id PK
        uuid cliente_id FK,UK
        varchar calle
        varchar numero_exterior
        varchar numero_interior
        varchar colonia
        varchar municipio
        varchar estado
        char codigo_postal
        varchar pais
        timestamptz created_at
        timestamptz updated_at
    }

    CUENTA {
        uuid id PK
        uuid cliente_id FK
        char numero_cuenta UK
        varchar tipo_cuenta
        char moneda
        numeric saldo
        varchar estatus
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    USUARIO {
        uuid id PK
        uuid cliente_id FK,UK
        varchar correo_electronico UK
        varchar password_hash
        varchar pin_hash
        boolean activo
        integer intentos_fallidos
        timestamptz bloqueado_hasta
        timestamptz ultimo_acceso
        varchar keycloak_sub UK
        timestamptz created_at
        timestamptz updated_at
    }
```

---

## 🔄 Flujo Integral de Onboarding (Alta Atómica)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente / Ejecutivo
    participant API as ClienteController
    participant Svc as ServicioRegistroCliente
    participant Dom as Dominio (Cliente / Cuenta / Usuario)
    participant DB as PostgreSQL (Flyway Migrations)

    Cliente->>API: POST /clientes (Datos personales, contacto, domicilio, laboral, password)
    API->>Svc: ComandoRegistrarCliente
    Svc->>DB: Validar unicidad (CURP, RFC, Correo)
    Svc->>Dom: Validar mayoría de edad (>=18 años) y consistencia
    Svc->>Dom: Hashear contraseña con BCrypt (Coste 12)
    Svc->>Dom: Construir Agregado Cliente + Domicilio + Cuenta (ACTIVA) + Usuario (ACTIVO)
    Svc->>DB: Insertar Cliente, Domicilio, Usuario y Cuenta
    DB-->>Svc: Trigger asigna número de cuenta con dígito verificador Luhn
    Svc-->>API: ClienteDetalle (con cuenta y usuario)
    API-->>Cliente: 201 Created + Location /clientes
```

> La API no utiliza `@PathVariable` ni `@RequestParam`: los identificadores y los criterios de
> búsqueda viajan siempre en el cuerpo de la petición, mediante Request DTOs validados con `@Valid`.
> Los errores se resuelven de forma centralizada en `ManejadorExcepcionesGlobales`
> (`@RestControllerAdvice`) y se devuelven como `ProblemDetail` (RFC 9457).

---

## 🚀 Endpoints REST Principales

| Método | Endpoint | Descripción | Acceso |
| :--- | :--- | :--- | :--- |
| `POST` | `/clientes` | Alta integral de cliente, domicilio, cuenta y usuario | Público |
| `POST` | `/clientes/consulta` | Único endpoint de búsqueda: id, CURP, RFC, correo, número de cuenta, estatus y rango de fechas combinables + paginación por cursor | Autenticado |
| `PUT` | `/clientes` | Actualizar cliente (id en el cuerpo; protección de inmutabilidad de CURP/RFC) | Autenticado |
| `DELETE`| `/clientes` | Baja lógica de cliente (id en el cuerpo; inactiva cuentas y usuario en cascada) | Autenticado |
| `POST` | `/cuentas/consulta` | Único endpoint de búsqueda de cuentas (número de cuenta y/o cliente); la respuesta incluye el saldo disponible | Autenticado |
| `POST` | `/auth/login` | Inicio de sesión con correo y contraseña (Emite JWT) | Público |
| `POST` | `/usuarios/consulta` | Único endpoint de búsqueda de usuarios: id, correo, cliente asociado y estatus combinables | Autenticado |
| `PUT` | `/usuarios/password` | Cambio de contraseña con validación de complejidad (id en el cuerpo) | Autenticado |

---

## 🛠️ Ejecución y Pruebas

### 1. Compilación y Ejecución con Maven
```bash
# Compilar el proyecto
mvn clean compile

# Ejecutar las pruebas unitarias
mvn test

# Empaquetar el artefacto JAR ejecutable
mvn package -DskipTests
```

### 2. Despliegue con Docker Compose (PostgreSQL + Keycloak Ligero + API)
```bash
# Iniciar todos los servicios (Postgres, Keycloak y API)
docker compose up -d --build

# Verificar logs
docker compose logs -f onboarding-app
```

- **Swagger UI / Documentación interactiva**: `http://localhost:8080/swagger-ui.html`
- **Consola Keycloak**: `http://localhost:8081` (Admin: `admin` / `admin`)
- **Health Check Actuator**: `http://localhost:8080/actuator/health`
