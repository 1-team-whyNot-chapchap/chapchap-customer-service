package com.chapchap.customer.global.config;

import com.chapchap.customer.global.config.openapi.OpenApiConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiGatewayServerTest {
    @Test
    void apiCallsUseTheOriginWhereSwaggerWasOpened() {
        var api = new OpenApiConfig().customOpenAPI();
        assertThat(api.getServers()).hasSize(1);
        String server = api.getServers().getFirst().getUrl();
        assertThat(URI.create("http://localhost:8080/swagger-ui/index.html").resolve(server)
                .resolve("api/customer/faqs").toString())
                .isEqualTo("http://localhost:8080/api/customer/faqs");
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }
}
