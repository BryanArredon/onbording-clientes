package com.onboarding.clientes.config;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwaggerUiBrandingTransformerTest {

    private static final String INDEX_HTML = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="UTF-8">
                <title>Swagger UI</title>
                <link rel="stylesheet" type="text/css" href="./swagger-ui.css" />
              </head>
              <body>
                <div id="swagger-ui"></div>
              </body>
            </html>
            """;

    private final SwaggerIndexTransformer delegate = mock(SwaggerIndexTransformer.class);
    private final SwaggerUiBrandingTransformer transformer = new SwaggerUiBrandingTransformer(delegate);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final ResourceTransformerChain chain = mock(ResourceTransformerChain.class);

    private Resource recurso(String url, String contenido) throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.getURL()).thenReturn(new URL(url));
        when(resource.getContentAsByteArray()).thenReturn(contenido.getBytes(StandardCharsets.UTF_8));
        return resource;
    }

    @Test
    @DisplayName("Debe inyectar el CSS y el favicon corporativos en el index del Swagger UI")
    void inyectarBrandingEnElIndex() throws IOException {
        Resource index = recurso(
                "jar:file:/app.jar!/META-INF/resources/webjars/swagger-ui/5.18.2/index.html", INDEX_HTML);
        when(delegate.transform(any(), any(), any())).thenReturn(index);

        Resource resultado = transformer.transform(request, index, chain);
        String html = new String(resultado.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(html).contains("<link rel=\"stylesheet\" type=\"text/css\" href=\"/custom-swagger.css\" />");
        assertThat(html).contains("<link rel=\"icon\" type=\"image/png\" href=\"/favicon.png\" />");
        // El cierre de head sigue intacto
        assertThat(html).contains("</head>");
        assertThat(html.indexOf("custom-swagger.css")).isLessThan(html.indexOf("</head>"));
    }

    @Test
    @DisplayName("No debe modificar recursos que no sean el index del Swagger UI")
    void noModificarOtrosRecursos() throws IOException {
        Resource css = mock(Resource.class);
        when(css.getURL()).thenReturn(new URL("jar:file:/app.jar!/swagger-ui/5.18.2/swagger-ui.css"));
        when(delegate.transform(any(), any(), any())).thenReturn(css);

        Resource resultado = transformer.transform(request, css, chain);

        assertThat(resultado).isSameAs(css);
    }

    @Test
    @DisplayName("No debe duplicar el branding si el index ya fue personalizado")
    void noDuplicarBranding() throws IOException {
        String yaPersonalizado = INDEX_HTML.replace(
                "</head>",
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"/custom-swagger.css\" />\n  </head>");
        Resource index = recurso(
                "jar:file:/app.jar!/META-INF/resources/webjars/swagger-ui/5.18.2/index.html", yaPersonalizado);
        when(delegate.transform(any(), any(), any())).thenReturn(index);

        Resource resultado = transformer.transform(request, index, chain);
        String html = new String(resultado.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(Objects.requireNonNull(html).split("/custom-swagger.css", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Debe devolver el recurso original si la URL no se puede resolver")
    void urlNoResoluble() throws IOException {
        Resource resource = new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public URL getURL() throws IOException {
                throw new IOException("no resoluble");
            }
        };
        when(delegate.transform(any(), any(), any())).thenReturn(resource);

        assertThat(transformer.transform(request, resource, chain)).isSameAs(resource);
    }
}
