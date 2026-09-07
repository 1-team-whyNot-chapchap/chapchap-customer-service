package com.chapchap.customer.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI () {
        return new OpenAPI()
                   .addServersItem(new Server().url("/"))
                   .info(
                     new Info()
                         .title("ChapChap Customer API")
                         .description("ChapChap Customer-Service REST API Document")
                         .version("v1.0.0")
                   ).components(new Components().addSecuritySchemes(BEARER_AUTH,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
                   .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
            ;
    }
}
