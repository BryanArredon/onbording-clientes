package com.onboarding.clientes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion de seguridad Spring Security:
 * - Denegar por defecto
 * - Sesiones sin estado (Stateless JWT)
 * - Endpoints publicos: Swagger UI, Actuator Health, Auth Login, POST /clientes (registro)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LimitadorSolicitudesFilter limitadorSolicitudesFilter;
    private final PuntoEntradaNoAutenticado puntoEntradaNoAutenticado;
    private final List<String> origenesPermitidos;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            LimitadorSolicitudesFilter limitadorSolicitudesFilter,
            PuntoEntradaNoAutenticado puntoEntradaNoAutenticado,
            @Value("${onboarding.cors.origenes-permitidos:http://localhost:3000}") List<String> origenesPermitidos) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.limitadorSolicitudesFilter = limitadorSolicitudesFilter;
        this.puntoEntradaNoAutenticado = puntoEntradaNoAutenticado;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(puntoEntradaNoAutenticado))
                // Cabeceras de seguridad (ASVS V14.4). Spring ya agrega nosniff, X-Frame-Options DENY,
                // Cache-Control no-store y HSTS sobre HTTPS; se suman CSP anti-framing y Referrer-Policy.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        // OpenAPI / Swagger UI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // Recursos estaticos del branding de Swagger UI (logo, favicon, tema oscuro/claro)
                        .requestMatchers("/logo.jpeg", "/favicon.png", "/custom-swagger.css", "/custom-swagger.js").permitAll()
                        // Actuator health/metrics
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Autenticacion
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/mfa/verificar").permitAll()
                        // Alta de cliente (abierto para nuevo registro)
                        .requestMatchers(HttpMethod.POST, "/clientes").permitAll()
                        // Todo lo demas requiere autenticacion
                        .anyRequest().authenticated()
                )
                .addFilterBefore(limitadorSolicitudesFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, LimitadorSolicitudesFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Origenes explicitos: nunca "*" junto con credenciales.
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
