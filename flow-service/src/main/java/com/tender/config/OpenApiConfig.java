package com.tender.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI setup. With springdoc-openapi on the classpath, the raw
 * spec is auto-generated from the controllers and DTOs and served at
 * /v3/api-docs, with an interactive UI at /swagger-ui.html. This class only
 * adds descriptive metadata on top of that.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flow Service API")
                        .description("""
                                Flow Service API
                                """)
                        .version("v1")
                         .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .tags(List.of(
                        new Tag().name("Deposit").description("view of Invoices"),
                        new Tag().name("Flow").description("view of Payments"),
                        new Tag().name("Reminder").description("view of Plans")
                        ));
    }
}
