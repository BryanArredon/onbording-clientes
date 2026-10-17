package com.onboarding.clientes.config;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

/**
 * Transformer de branding para el Swagger UI.
 *
 * <p>Envolvente del transformer por defecto de springdoc (que inyecta la configuracion de la UI en
 * {@code swagger-initializer.js}) para agregar ademas, en el {@code index.html} del webjar, el
 * favicon y la hoja de estilos corporativa del proyecto ({@code /custom-swagger.css}), que reemplaza
 * el logo y la paleta por defecto de SmartBear por la identidad visual de la API.
 */
public class SwaggerUiBrandingTransformer implements SwaggerIndexTransformer {

    static final String HOJA_ESTILOS = "/custom-swagger.css";
    static final String FAVICON = "/favicon.png";

    private static final String PATRON_INDEX = "**/swagger-ui/**/index.html";

    private final SwaggerIndexTransformer delegate;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public SwaggerUiBrandingTransformer(SwaggerIndexTransformer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Resource transform(
            HttpServletRequest request,
            Resource resource,
            ResourceTransformerChain chain)
            throws IOException {

        Resource transformado = delegate.transform(request, resource, chain);
        if (!esIndexDeSwaggerUi(transformado)) {
            return transformado;
        }

        String html = new String(transformado.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (html.contains(HOJA_ESTILOS)) {
            return transformado;
        }

        String branding = """
                <link rel="icon" type="image/png" href="%s" />
                <link rel="stylesheet" type="text/css" href="%s" />
                </head>""".formatted(FAVICON, HOJA_ESTILOS);
        String personalizado = html.replaceFirst("</head>", branding);

        return new TransformedResource(transformado, personalizado.getBytes(StandardCharsets.UTF_8));
    }

    private boolean esIndexDeSwaggerUi(Resource resource) {
        try {
            URL url = resource.getURL();
            return url != null && matcher.match(PATRON_INDEX, url.toString());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
