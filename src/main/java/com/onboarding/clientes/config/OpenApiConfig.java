package com.onboarding.clientes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuracion de documentacion OpenAPI 3.0 / Swagger UI.
 *
 * <p>Incluye el personalizado visual de Swagger UI: logo del proyecto (servido desde
 * {@code /static/logo.jpeg}), favicon y hoja de estilos {@code /custom-swagger.css}
 * con la paleta corporativa de la API.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI onboardingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Onboarding de Clientes Personas Físicas")
                        .description("""
                                API REST para el registro integral de clientes, apertura automática \
                                de cuentas bancarias y autenticación con JWT.

                                **Autorización:** todos los recursos identificados por UUID pasan por \
                                `AutorizadorRecursos`; el rol `CLIENTE` solo accede a sus propios \
                                recursos y el rol `ADMIN` tiene alcance global (prevención de IDOR).""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo de Ingeniería de Software")
                                .email("soporte@banco.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingrese su token JWT (Bearer) para acceder a los endpoints protegidos.")));
    }

    /**
     * Transformer de branding del Swagger UI.
     *
     * <p>Springdoc declara su transformer por defecto ({@code indexPageTransformer}) con
     * {@code @ConditionalOnMissingBean}, por lo que al registrar este bean queda deshabilitado.
     * Instanciamos la implementacion por defecto ({@link SwaggerIndexPageTransformer}) y la
     * envolvemos con {@link SwaggerUiBrandingTransformer}, que ademas inyecta en el
     * {@code index.html} el favicon y la hoja de estilos corporativa {@code /custom-swagger.css}
     * (logo propio y paleta de la API en lugar de la identidad de SmartBear).
     */
    @Bean
    @Primary
    public SwaggerIndexTransformer swaggerUiBrandingTransformer(
            SwaggerUiConfigProperties swaggerUiConfigProperties,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider) {
        SwaggerIndexPageTransformer transformerPorDefecto = new SwaggerIndexPageTransformer(
                swaggerUiConfigProperties, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
        return new SwaggerUiBrandingTransformer(transformerPorDefecto);
    }
}
