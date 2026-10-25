package id.neravoxa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.config.allowedOrigins:*}")
    private String allowedOrigins;

    @Bean
    public OpenAPI neravoxaOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("Neravoxa API Documentation")
                        .description("REST API Documentation for Neravoxa platform")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("PT Lintas Teknokreatif Nusantara")
                                .email("support@neravoxa.id")
                                .url("https://www.lintastekno.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.lintastekno.com/license")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Development Server"),
                        new Server().url("http://localhost:8080").description("Production Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .schemaRequirement(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authorization header using the Bearer scheme. Example: \\\"Authorization: Bearer {token}\\\""));
    }
}

